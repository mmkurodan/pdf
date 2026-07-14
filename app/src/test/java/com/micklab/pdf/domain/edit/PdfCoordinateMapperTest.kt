package com.micklab.pdf.domain.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfCoordinateMapperTest {

    /** Applies the visual→user affine to a visual point. */
    private fun PdfCoordinateMapper.Placement.mapVisual(vx: Float, vy: Float): Pair<Float, Float> =
        (a * vx + c * vy + e) to (b * vx + d * vy + f)

    @Test
    fun `no rotation maps full page onto the crop box`() {
        val p = PdfCoordinateMapper.place(0f, 0f, 200f, 100f, 0, 0f, 0f, 1f, 1f)
        assertThat(p.width).isEqualTo(200f)
        assertThat(p.height).isEqualTo(100f)

        val (blx, bly) = p.mapVisual(p.x, p.y)                          // box bottom-left
        val (trx, tryy) = p.mapVisual(p.x + p.width, p.y + p.height)    // box top-right
        assertThat(blx).isWithin(EPS).of(0f)
        assertThat(bly).isWithin(EPS).of(0f)
        assertThat(trx).isWithin(EPS).of(200f)
        assertThat(tryy).isWithin(EPS).of(100f)
    }

    @Test
    fun `crop origin offset and a sub-rectangle are honored`() {
        // Top-left quarter of a page whose crop box starts at (50, 20).
        val p = PdfCoordinateMapper.place(50f, 20f, 200f, 100f, 0, 0f, 0f, 0.5f, 0.5f)
        val (blx, bly) = p.mapVisual(p.x, p.y)
        val (trx, tryy) = p.mapVisual(p.x + p.width, p.y + p.height)
        assertThat(blx).isWithin(EPS).of(50f)
        assertThat(bly).isWithin(EPS).of(70f)
        assertThat(trx).isWithin(EPS).of(150f)
        assertThat(tryy).isWithin(EPS).of(120f)
    }

    @Test
    fun `rotation 90 swaps the visual size and still covers the crop box`() {
        val p = PdfCoordinateMapper.place(0f, 0f, 200f, 100f, 90, 0f, 0f, 1f, 1f)
        assertThat(p.width).isEqualTo(100f)   // visual width  = cropH
        assertThat(p.height).isEqualTo(200f)  // visual height = cropW

        val (x1, y1) = p.mapVisual(p.x, p.y)
        val (x2, y2) = p.mapVisual(p.x + p.width, p.y + p.height)
        assertThat(minOf(x1, x2)).isWithin(EPS).of(0f)
        assertThat(maxOf(x1, x2)).isWithin(EPS).of(200f)
        assertThat(minOf(y1, y2)).isWithin(EPS).of(0f)
        assertThat(maxOf(y1, y2)).isWithin(EPS).of(100f)
    }

    @Test
    fun `rotation 270 covers the crop box`() {
        val p = PdfCoordinateMapper.place(0f, 0f, 200f, 100f, 270, 0f, 0f, 1f, 1f)
        assertThat(p.width).isEqualTo(100f)
        assertThat(p.height).isEqualTo(200f)

        val (x1, y1) = p.mapVisual(p.x, p.y)
        val (x2, y2) = p.mapVisual(p.x + p.width, p.y + p.height)
        assertThat(minOf(x1, x2)).isWithin(EPS).of(0f)
        assertThat(maxOf(x1, x2)).isWithin(EPS).of(200f)
        assertThat(minOf(y1, y2)).isWithin(EPS).of(0f)
        assertThat(maxOf(y1, y2)).isWithin(EPS).of(100f)
    }

    @Test
    fun `negative and over-360 rotations normalize`() {
        val a = PdfCoordinateMapper.place(0f, 0f, 200f, 100f, -90, 0f, 0f, 1f, 1f)
        val b = PdfCoordinateMapper.place(0f, 0f, 200f, 100f, 270, 0f, 0f, 1f, 1f)
        assertThat(a).isEqualTo(b)
    }

    private companion object {
        const val EPS = 0.001f
    }
}
