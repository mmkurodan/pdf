package com.micklab.pdf.domain.edit

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

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
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        rotationDeg: Int = 0,
    ) {
        val lines = text.split(LINE_BREAK)
        val leading = fontSizePt * LINE_HEIGHT
        val red = ((colorRgb shr 16) and 0xFF) / 255f
        val green = ((colorRgb shr 8) and 0xFF) / 255f
        val blue = (colorRgb and 0xFF) / 255f
        // First baseline near the top of the box; each newline drops one leading.
        val baseX = placement.x
        val baseY = placement.y + placement.height - fontSizePt
        val shear = if (italic) ITALIC_SHEAR else 0f
        PDPageContentStream(document, page, AppendMode.APPEND, true, true).use { cs ->
            cs.saveGraphicsState()
            cs.transform(placement.matrix())
            if (rotationDeg % 360 != 0) {
                // Rotate about the box centre in visual space (y-up here), so a positive
                // angle reads clockwise to the viewer. Applied after the placement CTM.
                val cx = placement.x + placement.width / 2f
                val cy = placement.y + placement.height / 2f
                val rad = -Math.toRadians(rotationDeg.toDouble())
                val cosr = cos(rad).toFloat()
                val sinr = sin(rad).toFloat()
                cs.transform(
                    Matrix(cosr, sinr, -sinr, cosr, cx - cx * cosr + cy * sinr, cy - cx * sinr - cy * cosr),
                )
            }
            cs.beginText()
            cs.setFont(font, fontSizePt)
            cs.setNonStrokingColor(red, green, blue)
            if (bold) {
                // Faux-bold: fill + stroke the glyph outlines with the same colour.
                cs.setRenderingMode(RenderingMode.FILL_STROKE)
                cs.setStrokingColor(red, green, blue)
                cs.setLineWidth(fontSizePt * BOLD_STROKE)
            }
            // The text matrix carries the italic shear and the first baseline position.
            cs.setTextMatrix(Matrix(1f, 0f, shear, 1f, baseX, baseY))
            lines.forEachIndexed { i, line ->
                if (i > 0) cs.newLineAtOffset(0f, -leading)
                if (line.isNotEmpty()) cs.showText(line)
            }
            cs.endText()
            if (underline) {
                cs.setStrokingColor(red, green, blue)
                cs.setLineWidth(fontSizePt * UNDERLINE_WEIGHT)
                lines.forEachIndexed { i, line ->
                    if (line.isEmpty()) return@forEachIndexed
                    val w = runCatching { font.getStringWidth(line) / 1000f * fontSizePt }
                        .getOrElse { line.length * fontSizePt * 0.5f }
                    val ly = baseY - i * leading - fontSizePt * UNDERLINE_OFFSET
                    val lx = baseX - shear * i * leading // follow the italic shear per line
                    cs.moveTo(lx, ly)
                    cs.lineTo(lx + w, ly)
                    cs.stroke()
                }
            }
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
        const val LINE_HEIGHT = 1.2f
        const val ITALIC_SHEAR = 0.21f      // ~12° faux-italic slant
        const val BOLD_STROKE = 0.03f       // stroke width as a fraction of font size
        const val UNDERLINE_WEIGHT = 0.05f  // underline thickness as a fraction of font size
        const val UNDERLINE_OFFSET = 0.12f  // underline drop below the baseline
        val LINE_BREAK = Regex("\\r?\\n")
    }
}
