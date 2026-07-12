package com.micklab.pdf.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class DocumentTextResultJsonTest {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    @Test
    fun roundTripsThroughJson() {
        val original = DocumentTextResult(
            fileName = "sample.pdf",
            pageCount = 2,
            engine = "Tesseract (jpn+eng)",
            languages = listOf("jpn", "eng"),
            createdAtEpochMs = 1_700_000_000_000L,
            pages = listOf(
                PageTextResult(
                    pageIndex = 0,
                    pageNumber = 1,
                    source = TextSource.EMBEDDED_TEXT_LAYER,
                    text = "埋め込みテキスト",
                ),
                PageTextResult(
                    pageIndex = 1,
                    pageNumber = 2,
                    source = TextSource.OCR,
                    text = "Hello 世界",
                    averageConfidence = 0.92f,
                    blocks = listOf(
                        OcrBlock("Hello 世界", 0.92f, BoundingBox(0, 0, 100, 40)),
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<DocumentTextResult>(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun jsonKeepsEmbeddedAndOcrDistinct() {
        val result = DocumentTextResult(
            fileName = "a.pdf",
            pageCount = 2,
            engine = "Tesseract (jpn)",
            languages = listOf("jpn"),
            createdAtEpochMs = 0L,
            pages = listOf(
                PageTextResult(0, 1, TextSource.EMBEDDED_TEXT_LAYER, "layer"),
                PageTextResult(1, 2, TextSource.OCR, "ocr"),
            ),
        )

        val encoded = json.encodeToString(result)

        assertThat(encoded).contains("EMBEDDED_TEXT_LAYER")
        assertThat(encoded).contains("\"OCR\"")
    }

    @Test
    fun fullTextJoinsPages() {
        val result = DocumentTextResult(
            fileName = "a.pdf",
            pageCount = 2,
            engine = "e",
            languages = listOf("jpn"),
            createdAtEpochMs = 0L,
            pages = listOf(
                PageTextResult(0, 1, TextSource.OCR, "one"),
                PageTextResult(1, 2, TextSource.OCR, "two"),
            ),
        )

        assertThat(result.fullText).isEqualTo("one\n\ntwo")
    }
}
