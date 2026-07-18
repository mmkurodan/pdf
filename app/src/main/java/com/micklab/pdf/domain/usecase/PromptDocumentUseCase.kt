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

/** Whether the user's prompt is applied to the whole document at once or to each page. */
enum class PromptScope { WHOLE_DOCUMENT, PER_PAGE }

data class PagePromptAnswer(val pageNumber: Int, val answer: String)

data class PromptResult(
    val fileName: String,
    val method: SummaryMethod,
    val scope: PromptScope,
    val engineLabel: String,
    /** Populated for [PromptScope.WHOLE_DOCUMENT]. */
    val wholeAnswer: String,
    /** Populated for [PromptScope.PER_PAGE]. */
    val pages: List<PagePromptAnswer>,
)

/**
 * Runs an arbitrary user prompt over a PDF/image with the LLM — either once over
 * the whole document or independently per page — building on either OCR/embedded
 * text or the vision LLM (same two methods as [SummarizeDocumentUseCase]).
 */
class PromptDocumentUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val extractDocumentText: ExtractDocumentTextUseCase,
    private val llmClient: LlmClient,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) {
    suspend operator fun invoke(
        source: Uri,
        prompt: String,
        scope: PromptScope,
        method: SummaryMethod,
        engineType: OcrEngineType,
        languages: List<String>,
        renderDpi: Int = 200,
        onProgress: ProgressCallback = NoProgress,
    ): PromptResult = withContext(dispatchers.io) {
        val name = fileRepository.displayName(source)
        val instruction = prompt.trim()

        val wholeAnswer: String
        val pages: List<PagePromptAnswer>
        when (scope) {
            PromptScope.PER_PAGE -> {
                pages = when (method) {
                    SummaryMethod.OCR_THEN_LLM -> perPageViaOcr(source, instruction, engineType, languages, renderDpi, onProgress)
                    SummaryMethod.LLM_VISION -> perPageViaVision(source, name, instruction, renderDpi, onProgress)
                }
                wholeAnswer = ""
            }
            PromptScope.WHOLE_DOCUMENT -> {
                val documentText = when (method) {
                    SummaryMethod.OCR_THEN_LLM -> wholeTextViaOcr(source, engineType, languages, renderDpi, onProgress)
                    SummaryMethod.LLM_VISION -> wholeTextViaVision(source, name, renderDpi, onProgress)
                }
                wholeAnswer = if (documentText.isBlank()) {
                    LocaleManager.string(appContext, R.string.uc_prm_no_content)
                } else {
                    onProgress(0.92f, LocaleManager.string(appContext, R.string.uc_prm_whole))
                    llmClient.chat(instructionPrompt(instruction, documentText.take(WHOLE_TEXT_LIMIT))).trim()
                }
                pages = emptyList()
            }
        }

        Log.i(PdfToolsApp.TAG, "Prompted $name ($scope, $method)")
        PromptResult(
            fileName = name,
            method = method,
            scope = scope,
            engineLabel = if (method == SummaryMethod.OCR_THEN_LLM) engineType.displayName else "LLM Vision",
            wholeAnswer = wholeAnswer,
            pages = pages,
        )
    }

    // --- per page ---

    private suspend fun perPageViaOcr(
        source: Uri,
        instruction: String,
        engineType: OcrEngineType,
        languages: List<String>,
        renderDpi: Int,
        onProgress: ProgressCallback,
    ): List<PagePromptAnswer> {
        val textResult = extractDocumentText(
            source = source,
            engineType = engineType,
            languages = languages,
            mode = TextExtractionMode.AUTO,
            renderDpi = renderDpi,
        ) { fraction, label -> onProgress(fraction * 0.4f, label) }

        val total = textResult.pages.size
        val answers = ArrayList<PagePromptAnswer>(total)
        textResult.pages.forEachIndexed { index, page ->
            coroutineContext.ensureActive()
            onProgress(0.4f + 0.55f * index / total, LocaleManager.string(appContext, R.string.uc_prm_page, index + 1, total))
            val answer = if (page.text.isBlank()) {
                LocaleManager.string(appContext, R.string.uc_prm_no_text)
            } else {
                llmClient.chat(instructionPrompt(instruction, page.text.take(PAGE_TEXT_LIMIT))).trim()
            }
            answers += PagePromptAnswer(page.pageNumber, answer)
        }
        return answers
    }

    private suspend fun perPageViaVision(
        source: Uri,
        name: String,
        instruction: String,
        renderDpi: Int,
        onProgress: ProgressCallback,
    ): List<PagePromptAnswer> = forEachPageImage(source, name, renderDpi, onProgress, R.string.uc_prm_page) { pageNumber, base64 ->
        PagePromptAnswer(pageNumber, llmClient.chat(instruction, base64).trim())
    }

    // --- whole document ---

    private suspend fun wholeTextViaOcr(
        source: Uri,
        engineType: OcrEngineType,
        languages: List<String>,
        renderDpi: Int,
        onProgress: ProgressCallback,
    ): String {
        val textResult = extractDocumentText(
            source = source,
            engineType = engineType,
            languages = languages,
            mode = TextExtractionMode.AUTO,
            renderDpi = renderDpi,
        ) { fraction, label -> onProgress(fraction * 0.85f, label) }
        return textResult.pages.joinToString("\n\n") { "P${it.pageNumber}:\n${it.text}" }
    }

    private suspend fun wholeTextViaVision(
        source: Uri,
        name: String,
        renderDpi: Int,
        onProgress: ProgressCallback,
    ): String {
        val pages = forEachPageImage(source, name, renderDpi, onProgress, R.string.uc_prm_transcribe) { pageNumber, base64 ->
            PagePromptAnswer(pageNumber, llmClient.chat(TRANSCRIBE_PROMPT, base64).trim())
        }
        return pages.joinToString("\n\n") { "P${it.pageNumber}:\n${it.answer}" }
    }

    // --- shared page-image iteration (single image or each PDF page) ---

    private suspend fun forEachPageImage(
        source: Uri,
        name: String,
        renderDpi: Int,
        onProgress: ProgressCallback,
        labelRes: Int,
        transform: suspend (pageNumber: Int, base64: String) -> PagePromptAnswer,
    ): List<PagePromptAnswer> {
        val mime = fileRepository.mimeType(source).orEmpty()
        if (mime.startsWith("image/") || name.hasImageExtension()) {
            val bitmap = decodeDownsampled(source) ?: return emptyList()
            val base64 = try {
                llmClient.encodeJpegBase64(bitmap)
            } finally {
                bitmap.recycle()
            }
            return listOf(transform(1, base64))
        }

        val temp = workspace.copyUriToTemp(source)
        val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val answers = ArrayList<PagePromptAnswer>()
        try {
            val count = renderer.pageCount
            for (i in 0 until count) {
                coroutineContext.ensureActive()
                onProgress(0.9f * i / count, LocaleManager.string(appContext, labelRes, i + 1, count))
                val bitmap = renderPage(renderer, i, renderDpi)
                val base64 = try {
                    llmClient.encodeJpegBase64(bitmap)
                } finally {
                    bitmap.recycle()
                }
                answers += transform(i + 1, base64)
            }
            return answers
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

    /** Frames the user's instruction above the document body. */
    private fun instructionPrompt(instruction: String, text: String): String =
        "$instruction\n\n本文:\n$text"

    private fun String.hasImageExtension(): Boolean {
        val lower = lowercase()
        return IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }

    private companion object {
        const val MAX_SIDE = 3000
        const val PAGE_TEXT_LIMIT = 8000
        const val WHOLE_TEXT_LIMIT = 24000
        // Kept in Japanese by design, matching the summary prompts.
        const val TRANSCRIBE_PROMPT =
            "この画像は文書(PDF)の1ページです。書かれている文字をそのまま書き起こしてください。説明や前置きは不要です。"
        val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".heic")
    }
}
