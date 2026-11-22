package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.model.BoundingBox
import com.micklab.pdf.domain.model.OcrBlock
import com.micklab.pdf.domain.model.OcrEngineType
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline OCR backed by Tesseract (tesseract4android). Multilingual: pass e.g.
 * `["jpn", "eng"]` to recognize mixed Japanese/English.
 *
 * Line-level blocks carry each line's pixel bounding box and confidence, read
 * from `TessBaseAPI.getResultIterator()`.
 */
@Singleton
class TesseractOcrEngine @Inject constructor(
    private val modelManager: OcrModelManager,
    private val dispatchers: DispatcherProvider,
) : OcrEngine {

    override val type: OcrEngineType = OcrEngineType.TESSERACT

    override suspend fun isAvailable(languages: List<String>): Boolean =
        withContext(dispatchers.io) { modelManager.hasAllLanguages(languages) }

    override suspend fun recognize(bitmap: Bitmap, languages: List<String>): OcrPageOutcome =
        withContext(dispatchers.io) {
            modelManager.ensureAssetsCopied()
            if (!modelManager.hasAllLanguages(languages)) {
                throw OcrModelUnavailableException(languages)
            }

            val api = TessBaseAPI()
            try {
                val langArg = languages.joinToString(separator = "+")
                val initialized = api.init(modelManager.tessBasePath(), langArg)
                if (!initialized) throw OcrModelUnavailableException(languages)

                // Vertical Japanese (jpn_vert): segment as a single vertical text block so
                // the vertical model reads top-to-bottom, right-to-left columns correctly.
                val vertical = languages.any { it.equals("jpn_vert", ignoreCase = true) }
                api.setPageSegMode(
                    if (vertical) TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT
                    else TessBaseAPI.PageSegMode.PSM_AUTO,
                )
                api.setImage(bitmap.asArgb8888())

                val text = api.getUTF8Text().orEmpty().trim()
                val meanConf = api.meanConfidence().coerceIn(0, 100) / 100f

                val blocks = lineBlocks(api).ifEmpty {
                    // Iterator unavailable: fall back to text lines without positions.
                    text.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { OcrBlock(text = it, confidence = meanConf, boundingBox = null) }
                        .toList()
                }

                Log.i(PdfToolsApp.TAG, "Tesseract OCR ($langArg): ${text.length} chars, conf=$meanConf")
                OcrPageOutcome(text = text, averageConfidence = meanConf, blocks = blocks)
            } finally {
                runCatching { api.recycle() }
            }
        }

    /** Text lines with their pixel bounding boxes (image coordinates, origin top-left). */
    private fun lineBlocks(api: TessBaseAPI): List<OcrBlock> {
        val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
        val it = runCatching { api.resultIterator }.getOrNull() ?: return emptyList()
        val out = ArrayList<OcrBlock>()
        try {
            it.begin()
            do {
                val line = it.getUTF8Text(level)?.trim().orEmpty()
                if (line.isEmpty()) continue
                val r = it.getBoundingRect(level)
                out += OcrBlock(
                    text = line,
                    confidence = it.confidence(level).coerceIn(0f, 100f) / 100f,
                    boundingBox = BoundingBox(r.left, r.top, r.right, r.bottom),
                )
            } while (it.next(level))
        } catch (e: Exception) {
            Log.w(PdfToolsApp.TAG, "Tesseract line boxes unavailable: ${e.message}")
            return emptyList()
        } finally {
            runCatching { it.delete() }
        }
        return out
    }

    /** Tesseract requires ARGB_8888 input. */
    private fun Bitmap.asArgb8888(): Bitmap =
        if (config == Bitmap.Config.ARGB_8888) this
        else copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false)
}
