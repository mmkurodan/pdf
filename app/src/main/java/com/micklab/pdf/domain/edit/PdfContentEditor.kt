package com.micklab.pdf.domain.edit

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import javax.inject.Inject

/**
 * Low-level, non-destructive page drawing. Every write is appended
 * ([AppendMode.APPEND] with `resetContext = true`) so the existing background —
 * a scanned image or vector content — is never modified. Placement comes from
 * [PdfCoordinateMapper]: the visual→user affine is concatenated onto the CTM and
 * the content is then drawn in visual coordinates, so it stays upright and
 * correctly positioned even on rotated pages.
 */
class PdfContentEditor @Inject constructor() {

    fun addText(
        document: PDDocument,
        page: PDPage,
        placement: PdfCoordinateMapper.Placement,
        font: PDFont,
        text: String,
        fontSizePt: Float,
        colorRgb: Int,
    ) {
        PDPageContentStream(document, page, AppendMode.APPEND, true, true).use { cs ->
            cs.saveGraphicsState()
            cs.transform(placement.matrix())
            cs.beginText()
            cs.setFont(font, fontSizePt)
            cs.setNonStrokingColor(
                ((colorRgb shr 16) and 0xFF) / 255f,
                ((colorRgb shr 8) and 0xFF) / 255f,
                (colorRgb and 0xFF) / 255f,
            )
            // Baseline a little above the box bottom so text sits inside the box.
            cs.newLineAtOffset(placement.x, placement.y + fontSizePt * BASELINE_PAD)
            cs.showText(text)
            cs.endText()
            cs.restoreGraphicsState()
        }
    }

    fun addImage(
        document: PDDocument,
        page: PDPage,
        placement: PdfCoordinateMapper.Placement,
        image: PDImageXObject,
    ) {
        // Fit inside the placement box preserving aspect ratio (no stretching).
        val iw = image.width.toFloat().coerceAtLeast(1f)
        val ih = image.height.toFloat().coerceAtLeast(1f)
        val scale = minOf(placement.width / iw, placement.height / ih)
        val w = iw * scale
        val h = ih * scale
        val x = placement.x + (placement.width - w) / 2f
        val y = placement.y + (placement.height - h) / 2f
        PDPageContentStream(document, page, AppendMode.APPEND, true, true).use { cs ->
            cs.saveGraphicsState()
            cs.transform(placement.matrix())
            cs.drawImage(image, x, y, w, h)
            cs.restoreGraphicsState()
        }
    }

    private fun PdfCoordinateMapper.Placement.matrix(): Matrix = Matrix(a, b, c, d, e, f)

    private companion object {
        const val BASELINE_PAD = 0.2f
    }
}
