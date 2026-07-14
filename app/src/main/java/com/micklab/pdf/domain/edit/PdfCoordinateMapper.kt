package com.micklab.pdf.domain.edit

/**
 * Maps a placement expressed in visual page fractions - as the user sees the
 * rendered page: origin top-left, x rightwards, y downwards, each coordinate in
 * 0..1 - to PDF user space (origin bottom-left, y up, points) for a given page
 * CropBox and rotation.
 *
 * Pure math (no PDFBox / Android types) so it is unit-testable on the JVM. The
 * result carries both the box in visual space (points, bottom-left origin) and
 * an affine (a, b, c, d, e, f) mapping visual points to user space. Drawing code
 * concatenates the affine onto the content stream CTM and then draws the box in
 * visual coordinates, which keeps text and images upright and correctly placed
 * even on pages with a non-zero rotation.
 */
object PdfCoordinateMapper {

    /**
     * Placement result. (a, b, c, d, e, f) is the visual-to-user affine in
     * PDFBox Matrix order: userX = a*x + c*y + e, userY = b*x + d*y + f. The
     * (x, y, width, height) box is in visual space, points, bottom-left origin.
     */
    data class Placement(
        val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float,
        val x: Float, val y: Float, val width: Float, val height: Float,
    )

    /**
     * cropLlx / cropLly is the CropBox lower-left in user space (points); cropW /
     * cropH its size (points). rotationDeg is the page rotation (any multiple of
     * 90; normalized here). fLeft / fTop / fRight / fBottom are visual fractions
     * in 0..1 with a top-left origin.
     */
    fun place(
        cropLlx: Float, cropLly: Float, cropW: Float, cropH: Float,
        rotationDeg: Int,
        fLeft: Float, fTop: Float, fRight: Float, fBottom: Float,
    ): Placement {
        val rot = ((rotationDeg % 360) + 360) % 360
        val swap = rot == 90 || rot == 270
        val vw = if (swap) cropH else cropW   // visual width in points
        val vh = if (swap) cropW else cropH   // visual height in points

        val x = fLeft * vw
        val width = (fRight - fLeft) * vw
        val height = (fBottom - fTop) * vh
        val y = (1f - fBottom) * vh            // bottom edge, measured from the visual bottom

        // Visual (bottom-left) to user space, folding in the crop origin. Each case
        // is the inverse of the viewer rotating the page clockwise by rot.
        val m = when (rot) {
            90 -> floatArrayOf(0f, 1f, -1f, 0f, cropLlx + cropW, cropLly)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f, cropLlx + cropW, cropLly + cropH)
            270 -> floatArrayOf(0f, -1f, 1f, 0f, cropLlx, cropLly + cropH)
            else -> floatArrayOf(1f, 0f, 0f, 1f, cropLlx, cropLly)
        }
        return Placement(m[0], m[1], m[2], m[3], m[4], m[5], x, y, width, height)
    }
}
