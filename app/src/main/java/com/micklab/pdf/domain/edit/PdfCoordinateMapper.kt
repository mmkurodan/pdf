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

    /** Fraction rect -> PDF user-space rect [llx, lly, urx, ury] (rotation / crop aware). */
    fun toUserRect(
        cropLlx: Float, cropLly: Float, cropW: Float, cropH: Float,
        rotationDeg: Int, fLeft: Float, fTop: Float, fRight: Float, fBottom: Float,
    ): FloatArray {
        val p = place(cropLlx, cropLly, cropW, cropH, rotationDeg, fLeft, fTop, fRight, fBottom)
        val corners = arrayOf(
            p.x to p.y, (p.x + p.width) to p.y, p.x to (p.y + p.height), (p.x + p.width) to (p.y + p.height),
        )
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((vx, vy) in corners) {
            val ux = p.a * vx + p.c * vy + p.e
            val uy = p.b * vx + p.d * vy + p.f
            minX = minOf(minX, ux); maxX = maxOf(maxX, ux); minY = minOf(minY, uy); maxY = maxOf(maxY, uy)
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /** PDF user-space rect -> fraction rect (inverse of [toUserRect]). */
    fun toFractionRect(
        cropLlx: Float, cropLly: Float, cropW: Float, cropH: Float,
        rotationDeg: Int, ullx: Float, ully: Float, uurx: Float, uury: Float,
    ): FractionRect {
        val rot = ((rotationDeg % 360) + 360) % 360
        val vw = if (rot == 90 || rot == 270) cropH else cropW
        val vh = if (rot == 90 || rot == 270) cropW else cropH
        val p = place(cropLlx, cropLly, cropW, cropH, rotationDeg, 0f, 0f, 1f, 1f)
        val det = p.a * p.d - p.b * p.c
        val corners = arrayOf(ullx to ully, uurx to ully, ullx to uury, uurx to uury)
        var minFx = Float.MAX_VALUE
        var minFy = Float.MAX_VALUE
        var maxFx = -Float.MAX_VALUE
        var maxFy = -Float.MAX_VALUE
        for ((ux, uy) in corners) {
            val vx = (p.d * (ux - p.e) - p.c * (uy - p.f)) / det
            val vy = (-p.b * (ux - p.e) + p.a * (uy - p.f)) / det
            val fx = if (vw != 0f) vx / vw else 0f
            val fy = if (vh != 0f) 1f - vy / vh else 0f
            minFx = minOf(minFx, fx); maxFx = maxOf(maxFx, fx); minFy = minOf(minFy, fy); maxFy = maxOf(maxFy, fy)
        }
        return FractionRect(
            minFx.coerceIn(0f, 1f), minFy.coerceIn(0f, 1f), maxFx.coerceIn(0f, 1f), maxFy.coerceIn(0f, 1f),
        )
    }
}
