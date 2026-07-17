package com.micklab.pdf.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import com.micklab.pdf.R
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.domain.model.OcrEngineType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device PaddleOCR via ONNX Runtime. Fully offline once the PP-OCR ONNX
 * models are downloaded (see [PaddleModelManager]); recognition runs through
 * [PaddleOcrPipeline] (detection → boxes → recognition → CTC).
 */
@Singleton
class PaddleOcrEngine @Inject constructor(
    private val modelManager: PaddleModelManager,
    private val pipeline: PaddleOcrPipeline,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) : OcrEngine {

    override val type: OcrEngineType = OcrEngineType.PADDLE_OCR

    override suspend fun isAvailable(languages: List<String>): Boolean =
        withContext(dispatchers.io) { modelManager.isDownloaded() }

    override suspend fun recognize(bitmap: Bitmap, languages: List<String>): OcrPageOutcome =
        withContext(dispatchers.io) {
            if (!modelManager.isDownloaded()) {
                throw OcrModelUnavailableException(
                    languages,
                    LocaleManager.string(appContext, R.string.poe_model_unavailable),
                )
            }
            pipeline.recognize(bitmap)
        }
}
