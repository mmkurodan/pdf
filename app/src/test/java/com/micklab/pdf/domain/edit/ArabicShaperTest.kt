package com.micklab.pdf.domain.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Inputs are base Arabic letters (U+06xx); expected outputs are Arabic Presentation
 * Forms-B. Built from explicit code points so the exact values are unambiguous.
 * beh: isolated FE8F / final FE90 / initial FE91 / medial FE92; alef: iso FE8D / final FE8E;
 * lam-alef ligature: isolated FEFB / final FEFC.
 */
class ArabicShaperTest {

    private fun s(vararg cps: Int): String = cps.joinToString("") { it.toChar().toString() }

    private val ALEF = 0x0627
    private val BEH = 0x0628
    private val LAM = 0x0644
    private val FATHA = 0x064E
    private val SPACE = 0x0020

    @Test
    fun `single letter is isolated`() {
        assertThat(ArabicShaper.shape(s(ALEF))).isEqualTo(s(0xFE8D))
        assertThat(ArabicShaper.shape(s(BEH))).isEqualTo(s(0xFE8F))
    }

    @Test
    fun `dual-joining letters take initial, medial and final forms`() {
        assertThat(ArabicShaper.shape(s(BEH, BEH))).isEqualTo(s(0xFE91, 0xFE90))
        assertThat(ArabicShaper.shape(s(BEH, BEH, BEH))).isEqualTo(s(0xFE91, 0xFE92, 0xFE90))
    }

    @Test
    fun `right-joining letter connects only on its right`() {
        assertThat(ArabicShaper.shape(s(BEH, ALEF))).isEqualTo(s(0xFE91, 0xFE8E))
    }

    @Test
    fun `lam-alef forms a ligature`() {
        assertThat(ArabicShaper.shape(s(LAM, ALEF))).isEqualTo(s(0xFEFB))
        assertThat(ArabicShaper.shape(s(BEH, LAM, ALEF))).isEqualTo(s(0xFE91, 0xFEFC))
    }

    @Test
    fun `combining marks are transparent to joining`() {
        assertThat(ArabicShaper.shape(s(BEH, FATHA, BEH))).isEqualTo(s(0xFE91, FATHA, 0xFE90))
    }

    @Test
    fun `space breaks the joining run`() {
        assertThat(ArabicShaper.shape(s(BEH, SPACE, BEH))).isEqualTo(s(0xFE8F, SPACE, 0xFE8F))
    }

    @Test
    fun `non-Arabic text passes through unchanged`() {
        assertThat(ArabicShaper.shape("AB 12")).isEqualTo("AB 12")
        assertThat(ArabicShaper.shape("")).isEqualTo("")
    }
}
