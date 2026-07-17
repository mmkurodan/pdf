package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import com.micklab.pdf.domain.model.OcrBlock
import com.micklab.pdf.domain.model.OcrEngineType

/**
 * Abstraction over an OCR backend. This is the seam that makes the OCR layer
 * swappable ("差し替え可能な構造"): Tesseract, PaddleOCR, or a local LLM vision
 * encoder all implement this same contract and are collected via Hilt
 * multibindings (see [com.micklab.pdf.di.OcrModule]).
 */
interface OcrEngine {

    val type: OcrEngineType

    /**
     * Whether this engine can run for the requested [languages] right now
     * (e.g. Tesseract needs the matching `*.traineddata` present on device).
     */
    suspend fun isAvailable(languages: List<String>): Boolean

    /**
     * Recognize text on a single already-rendered page/image [bitmap].
     * Implementations must be safe to call from a background dispatcher.
     */
    suspend fun recognize(bitmap: Bitmap, languages: List<String>): OcrPageOutcome
}

/** Raw per-page OCR output, before it is tagged with a [com.micklab.pdf.domain.model.TextSource]. */
data class OcrPageOutcome(
    val text: String,
    val averageConfidence: Float,
    val blocks: List<OcrBlock>,
)

/** Thrown when the model/data required to run an engine is missing. */
class OcrModelUnavailableException(
    val languages: List<String>,
    // Callers pass a localized message; this fallback is English-only (rarely used).
    message: String = "OCR model (traineddata) not found: ${languages.joinToString("+")}",
) : Exception(message)

/** Thrown by pluggable engines that are wired but not yet implemented. */
class OcrEngineNotImplementedException(
    engine: OcrEngineType,
) : Exception("${engine.displayName} is not implemented yet (extension point).")
