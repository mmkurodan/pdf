package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.model.OcrEngineType
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
) : OcrEngine {

    override val type: OcrEngineType = OcrEngineType.PADDLE_OCR

    override suspend fun isAvailable(languages: List<String>): Boolean =
        withContext(dispatchers.io) { modelManager.isDownloaded() }

    override suspend fun recognize(bitmap: Bitmap, languages: List<String>): OcrPageOutcome =
        withContext(dispatchers.io) {
            if (!modelManager.isDownloaded()) {
                throw OcrModelUnavailableException(
                    languages,
                    "PaddleOCR モデルが未取得です。OCR 画面の『PaddleOCR モデルをダウンロード』から取得してください。",
                )
            }
            pipeline.recognize(bitmap)
        }
}
