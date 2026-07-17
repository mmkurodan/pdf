package com.micklab.pdf.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.R
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.NoProgress
import com.micklab.pdf.core.ProgressCallback
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.ocr.LlmClient
import com.micklab.pdf.domain.pdf.PdfWorkspace
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** How the summary is produced. */
enum class SummaryMethod {
    /** Extract text (embedded/OCR), then summarize the text with the LLM. */
    OCR_THEN_LLM,

    /** Send each page image directly to the vision LLM to summarize. */
    LLM_VISION,
}

data class PageSummary(val pageNumber: Int, val summary: String)

data class DocumentSummary(
    val fileName: String,
    val method: String,
    val overallSummary: String,
    val pages: List<PageSummary>,
)

/**
 * Summarizes a PDF/image at the whole-document and per-page level, either by
 * OCR-then-LLM or by sending page images directly to a vision LLM.
 */
class SummarizeDocumentUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val extractDocumentText: ExtractDocumentTextUseCase,
    private val llmClient: LlmClient,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) {
    suspend operator fun invoke(
        source: Uri,
        method: SummaryMethod,
        engineType: OcrEngineType,
        languages: List<String>,
        renderDpi: Int = 200,
        onProgress: ProgressCallback = NoProgress,
    ): DocumentSummary = withContext(dispatchers.io) {
        val name = fileRepository.displayName(source)
        val pageSummaries = when (method) {
            SummaryMethod.OCR_THEN_LLM ->
                summarizeViaOcr(source, engineType, languages, renderDpi, onProgress)

            SummaryMethod.LLM_VISION ->
                summarizeViaVision(source, name, renderDpi, onProgress)
        }

        val overall = if (pageSummaries.isEmpty()) {
            LocaleManager.string(appContext, R.string.uc_sum_no_content)
        } else if (pageSummaries.size == 1) {
            pageSummaries.first().summary
        } else {
            onProgress(0.95f, LocaleManager.string(appContext, R.string.uc_sum_overall))
            val joined = pageSummaries.joinToString("\n") { "P${it.pageNumber}: ${it.summary}" }
            llmClient.chat(overallPrompt(joined))
        }

        Log.i(PdfToolsApp.TAG, "Summarized $name (${pageSummaries.size} pages, $method)")
        DocumentSummary(
            fileName = name,
            method = "${method.name} / ${engineType.displayName.takeIf { method == SummaryMethod.OCR_THEN_LLM } ?: "LLM Vision"}",
            overallSummary = overall.trim(),
            pages = pageSummaries,
        )
    }

    private suspend fun summarizeViaOcr(
        source: Uri,
        engineType: OcrEngineType,
        languages: List<String>,
        renderDpi: Int,
        onProgress: ProgressCallback,
    ): List<PageSummary> {
        val textResult = extractDocumentText(
            source = source,
            engineType = engineType,
            languages = languages,
            mode = TextExtractionMode.AUTO,
            renderDpi = renderDpi,
        ) { fraction, label -> onProgress(fraction * 0.4f, label) }

        val summaries = ArrayList<PageSummary>(textResult.pages.size)
        val total = textResult.pages.size
        textResult.pages.forEachIndexed { index, page ->
            coroutineContext.ensureActive()
            onProgress(0.4f + 0.5f * index / total, LocaleManager.string(appContext, R.string.uc_sum_page, index + 1, total))
            val summary = if (page.text.isBlank()) {
                LocaleManager.string(appContext, R.string.uc_sum_no_text)
            } else {
                llmClient.chat(pageTextPrompt(page.text))
            }
            summaries += PageSummary(page.pageNumber, summary.trim())
        }
        return summaries
    }

    private suspend fun summarizeViaVision(
        source: Uri,
        name: String,
        renderDpi: Int,
        onProgress: ProgressCallback,
    ): List<PageSummary> {
        val mime = fileRepository.mimeType(source).orEmpty()
        if (mime.startsWith("image/") || name.hasImageExtension()) {
            val bitmap = decodeDownsampled(source) ?: return emptyList()
            val base64 = try {
                llmClient.encodeJpegBase64(bitmap)
            } finally {
                bitmap.recycle()
            }
            val summary = llmClient.chat(pageVisionPrompt(), base64).trim()
            return listOf(PageSummary(1, summary))
        }

        val temp = workspace.copyUriToTemp(source)
        val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val summaries = ArrayList<PageSummary>()
        try {
            val count = renderer.pageCount
            for (i in 0 until count) {
                coroutineContext.ensureActive()
                onProgress(0.9f * i / count, LocaleManager.string(appContext, R.string.uc_sum_page, i + 1, count))
                val bitmap = renderPage(renderer, i, renderDpi)
                val base64 = try {
                    llmClient.encodeJpegBase64(bitmap)
                } finally {
                    bitmap.recycle()
                }
                val summary = llmClient.chat(pageVisionPrompt(), base64).trim()
                summaries += PageSummary(i + 1, summary)
            }
            return summaries
        } finally {
            renderer.close()
            pfd.close()
            workspace.delete(temp)
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int, dpi: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val scale = dpi.coerceIn(72, 300) / 72f
            val width = (page.width * scale).roundToInt().coerceIn(1, MAX_SIDE)
            val height = (page.height * scale).roundToInt().coerceIn(1, MAX_SIDE)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        fileRepository.openInput(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return fileRepository.openInput(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun pageTextPrompt(text: String): String =
        "次のページ本文を日本語で3〜5文に簡潔に要約してください。要点のみで、前置きは不要です。\n\n本文:\n${text.take(8000)}"

    private fun pageVisionPrompt(): String =
        "この画像は文書(PDF)の1ページです。内容を日本語で3〜5文に簡潔に要約してください。要点のみで、前置きは不要です。"

    private fun overallPrompt(pageSummaries: String): String =
        "以下は文書の各ページ要約です。文書全体を日本語で5〜8文程度に簡潔にまとめてください。\n\n$pageSummaries"

    private fun String.hasImageExtension(): Boolean {
        val lower = lowercase()
        return IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }

    private companion object {
        const val MAX_SIDE = 3000
        val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".heic")
    }
}
