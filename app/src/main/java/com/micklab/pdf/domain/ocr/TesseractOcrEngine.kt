package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.model.OcrBlock
import com.micklab.pdf.domain.model.OcrEngineType
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline OCR backed by Tesseract (tesseract4android). Multilingual: pass e.g.
 * `["jpn", "eng"]` to recognize mixed Japanese/English.
 *
 * Line-level blocks are derived from the recognized text and carry the page's
 * mean confidence. Finer word/char boxes are available via
 * `TessBaseAPI.getResultIterator()` and can be added here without touching the
 * [OcrEngine] contract.
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

                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                api.setImage(bitmap.asArgb8888())

                val text = api.getUTF8Text().orEmpty().trim()
                val meanConf = api.meanConfidence().coerceIn(0, 100) / 100f

                val blocks = text.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { OcrBlock(text = it, confidence = meanConf, boundingBox = null) }
                    .toList()

                Log.i(PdfToolsApp.TAG, "Tesseract OCR ($langArg): ${text.length} chars, conf=$meanConf")
                OcrPageOutcome(text = text, averageConfidence = meanConf, blocks = blocks)
            } finally {
                runCatching { api.recycle() }
            }
        }

    /** Tesseract requires ARGB_8888 input. */
    private fun Bitmap.asArgb8888(): Bitmap =
        if (config == Bitmap.Config.ARGB_8888) this
        else copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false)
}
