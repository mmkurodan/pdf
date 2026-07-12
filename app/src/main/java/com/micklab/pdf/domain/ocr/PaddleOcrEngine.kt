package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import com.micklab.pdf.domain.model.OcrEngineType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PaddleOCR backend — pluggable extension point.
 *
 * It participates in DI exactly like [TesseractOcrEngine] (bound via
 * [com.micklab.pdf.di.OcrModule] and selectable in the UI), but recognition is
 * intentionally left unimplemented so the project builds without shipping the
 * native models.
 *
 * ## How to implement (offline, on-device)
 * 1. Add Paddle-Lite:
 *    `implementation "com.baidu.paddle:paddle-lite:<ver>"`  (or the ncnn port).
 * 2. Bundle the 3 PaddleOCR models under `assets/paddleocr/`:
 *    - detection  (`det`)   — text region detection
 *    - recognition(`rec`)   — CRNN recognizer (choose a `japan`/`en` dict)
 *    - classifier (`cls`)   — angle classifier (optional)
 *    Provide the character dictionary (`ppocr_keys_*.txt`).
 * 3. Load models with `MobileConfig`/`PaddlePredictor`, run det → cls → rec,
 *    map boxes to [com.micklab.pdf.domain.model.BoundingBox] and text/score to
 *    [OcrBlock], then return an [OcrPageOutcome].
 *
 * Because it implements the same [OcrEngine] contract, nothing else in the app
 * has to change when this is filled in.
 */
@Singleton
class PaddleOcrEngine @Inject constructor() : OcrEngine {

    override val type: OcrEngineType = OcrEngineType.PADDLE_OCR

    override suspend fun isAvailable(languages: List<String>): Boolean = false

    override suspend fun recognize(bitmap: Bitmap, languages: List<String>): OcrPageOutcome {
        throw OcrEngineNotImplementedException(type)
    }
}
