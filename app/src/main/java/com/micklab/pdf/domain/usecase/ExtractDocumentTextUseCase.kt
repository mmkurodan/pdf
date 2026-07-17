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
import com.micklab.pdf.domain.model.DocumentTextResult
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.model.PageTextResult
import com.micklab.pdf.domain.model.TextSource
import com.micklab.pdf.domain.ocr.OcrEngineRegistry
import com.micklab.pdf.domain.ocr.OcrModelUnavailableException
import com.micklab.pdf.domain.pdf.PdfWorkspace
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Controls the embedded-text-layer vs OCR trade-off. */
enum class TextExtractionMode {
    /** Use the embedded text layer where present, OCR the rest. */
    AUTO,

    /** Only pull the existing text layer (no OCR). */
    EMBEDDED_ONLY,

    /** Ignore any text layer and OCR every page. */
    OCR_ONLY,
}

/**
 * OCR / テキスト抽出.
 *
 * Produces a [DocumentTextResult] (JSON-serializable). Crucially, each page is
 * tagged with its [TextSource] so callers can tell an embedded PDF text layer
 * ([TextSource.EMBEDDED_TEXT_LAYER]) apart from OCR output ([TextSource.OCR]) —
 * a core requirement. Works on PDFs and single images.
 */
class ExtractDocumentTextUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val ocrRegistry: OcrEngineRegistry,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) {
    suspend operator fun invoke(
        source: Uri,
        engineType: OcrEngineType,
        languages: List<String>,
        mode: TextExtractionMode,
        renderDpi: Int = 200,
        onProgress: ProgressCallback = NoProgress,
    ): DocumentTextResult = withContext(dispatchers.io) {
        val name = fileRepository.displayName(source)
        val mime = fileRepository.mimeType(source).orEmpty()
        if (mime.startsWith("image/") || name.hasImageExtension()) {
            extractFromImage(source, name, engineType, languages)
        } else {
            extractFromPdf(source, name, engineType, languages, mode, renderDpi, onProgress)
        }
    }

    private suspend fun extractFromImage(
        source: Uri,
        name: String,
        engineType: OcrEngineType,
        languages: List<String>,
    ): DocumentTextResult {
        val engine = ocrRegistry.engine(engineType)
        if (!engine.isAvailable(languages)) throw OcrModelUnavailableException(languages)

        val bitmap = decodeDownsampled(source) ?: error(LocaleManager.string(appContext, R.string.uc_ocr_image_load_failed, name))
        val outcome = try {
            engine.recognize(bitmap, languages)
        } finally {
            bitmap.recycle()
        }
        val page = PageTextResult(
            pageIndex = 0,
            pageNumber = 1,
            source = TextSource.OCR,
            text = outcome.text,
            averageConfidence = outcome.averageConfidence,
            blocks = outcome.blocks,
        )
        return DocumentTextResult(
            fileName = name,
            pageCount = 1,
            engine = engineLabel(engineType, languages),
            languages = languages,
            createdAtEpochMs = System.currentTimeMillis(),
            pages = listOf(page),
        )
    }

    private suspend fun extractFromPdf(
        source: Uri,
        name: String,
        engineType: OcrEngineType,
        languages: List<String>,
        mode: TextExtractionMode,
        renderDpi: Int,
        onProgress: ProgressCallback,
    ): DocumentTextResult {
        val temp = workspace.copyUriToTemp(source)
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            return workspace.load(temp).use { document ->
                val pageCount = document.numberOfPages
                val engine = ocrRegistry.engine(engineType)
                val ocrAvailable = mode != TextExtractionMode.EMBEDDED_ONLY &&
                        engine.isAvailable(languages)
                if (mode == TextExtractionMode.OCR_ONLY && !ocrAvailable) {
                    throw OcrModelUnavailableException(languages)
                }
                if (mode != TextExtractionMode.EMBEDDED_ONLY && ocrAvailable) {
                    pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                }

                val stripper = PDFTextStripper()
                val pages = ArrayList<PageTextResult>(pageCount)
                for (i in 0 until pageCount) {
                    coroutineContext.ensureActive()
                    // Emit before the (possibly slow) OCR call so the label names the
                    // page in progress now; the fraction reflects completed pages.
                    onProgress(i.toFloat() / pageCount, LocaleManager.string(appContext, R.string.uc_ocr_analyzing_page, i + 1, pageCount))
                    val embedded = if (mode != TextExtractionMode.OCR_ONLY) {
                        extractEmbedded(stripper, document, i)
                    } else {
                        ""
                    }
                    val useEmbedded = when (mode) {
                        TextExtractionMode.EMBEDDED_ONLY -> true
                        TextExtractionMode.OCR_ONLY -> false
                        TextExtractionMode.AUTO -> embedded.trim().length >= EMBEDDED_MIN_CHARS
                    }

                    pages += if (useEmbedded) {
                        val trimmed = embedded.trim()
                        PageTextResult(
                            pageIndex = i,
                            pageNumber = i + 1,
                            source = if (trimmed.isEmpty()) TextSource.NONE else TextSource.EMBEDDED_TEXT_LAYER,
                            text = trimmed,
                        )
                    } else if (renderer != null) {
                        val bitmap = renderPage(renderer!!, i, renderDpi)
                        val outcome = try {
                            engine.recognize(bitmap, languages)
                        } finally {
                            bitmap.recycle()
                        }
                        PageTextResult(
                            pageIndex = i,
                            pageNumber = i + 1,
                            source = TextSource.OCR,
                            text = outcome.text,
                            averageConfidence = outcome.averageConfidence,
                            blocks = outcome.blocks,
                        )
                    } else {
                        // This page has no embedded text and needs OCR, but the
                        // model is unavailable. Fail with an actionable message
                        // rather than silently emitting empty text.
                        throw OcrModelUnavailableException(languages)
                    }
                }
                onProgress(1f, LocaleManager.string(appContext, R.string.uc_ocr_page_done, pageCount, pageCount))

                Log.i(PdfToolsApp.TAG, "Extracted text from $pageCount page(s) of $name")
                DocumentTextResult(
                    fileName = name,
                    pageCount = pageCount,
                    engine = engineLabel(engineType, languages),
                    languages = languages,
                    createdAtEpochMs = System.currentTimeMillis(),
                    pages = pages,
                )
            }
        } finally {
            renderer?.close()
            pfd?.close()
            workspace.delete(temp)
        }
    }

    private fun extractEmbedded(stripper: PDFTextStripper, document: PDDocument, pageIndex: Int): String {
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        return runCatching { stripper.getText(document) }.getOrDefault("")
    }

    private fun renderPage(renderer: PdfRenderer, pageIndex: Int, dpi: Int): Bitmap {
        val page = renderer.openPage(pageIndex)
        try {
            val scale = dpi.coerceIn(72, 400) / 72f
            val w = (page.width * scale).roundToInt().coerceIn(1, MAX_SIDE)
            val h = (page.height * scale).roundToInt().coerceIn(1, MAX_SIDE)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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
        while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return fileRepository.openInput(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun engineLabel(engineType: OcrEngineType, languages: List<String>): String =
        "${engineType.displayName} (${languages.joinToString("+")})"

    private fun String.hasImageExtension(): Boolean {
        val lower = lowercase()
        return IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }

    companion object {
        private const val EMBEDDED_MIN_CHARS = 4
        private const val MAX_SIDE = 4000
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".heic")
    }
}
