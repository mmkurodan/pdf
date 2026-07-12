package com.micklab.pdf.domain.model

import kotlinx.serialization.Serializable

/** Which OCR backend to use. The layer is pluggable; see the domain.ocr package. */
enum class OcrEngineType(val displayName: String) {
    TESSERACT("Tesseract"),
    PADDLE_OCR("PaddleOCR"),
    LLM_VISION("ローカル LLM Vision"),
}

/**
 * Where a piece of text came from. Text already present in the PDF as a real
 * text layer is reported as [EMBEDDED_TEXT_LAYER] and is kept distinct from
 * text produced by [OCR] — a core requirement.
 */
@Serializable
enum class TextSource {
    EMBEDDED_TEXT_LAYER,
    OCR,
    NONE,
}

@Serializable
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

@Serializable
data class OcrBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: BoundingBox? = null,
)

/** Per-page text with provenance. */
@Serializable
data class PageTextResult(
    val pageIndex: Int,   // 0-based
    val pageNumber: Int,  // 1-based, for display
    val source: TextSource,
    val text: String,
    val averageConfidence: Float? = null,
    val blocks: List<OcrBlock> = emptyList(),
)

/**
 * The JSON payload returned by the OCR / text-extraction feature.
 * Serializable with kotlinx.serialization.
 */
@Serializable
data class DocumentTextResult(
    val fileName: String,
    val pageCount: Int,
    val engine: String,
    val languages: List<String>,
    val createdAtEpochMs: Long,
    val pages: List<PageTextResult>,
) {
    /** Convenience: all page text joined for quick display/copy. */
    val fullText: String
        get() = pages.joinToString(separator = "\n\n") { it.text }
}
