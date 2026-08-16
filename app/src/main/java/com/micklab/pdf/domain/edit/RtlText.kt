package com.micklab.pdf.domain.edit

import android.icu.text.Bidi

/**
 * Turns logical-order text into the visual, shaped form that pdfbox-android needs to
 * draw right-to-left scripts, because its `showText` applies no complex shaping or
 * bidi of its own.
 *
 *  1. [ArabicShaper] maps Arabic letters to their contextual presentation forms
 *     (Hebrew needs no shaping).
 *  2. The Unicode Bidirectional Algorithm ([Bidi.writeReordered]) reorders RTL runs
 *     into visual order, keeping combining marks with their base and mirroring
 *     brackets. Mixed LTR/RTL is handled; purely-LTR text is returned untouched.
 */
object RtlText {

    /** Whether [text] contains any Hebrew or Arabic characters (so shaping is worth doing). */
    fun hasRtl(text: String): Boolean = text.any { isRtl(it.code) }

    /**
     * One display line in logical order → visual, shaped order. Call **per line**
     * (never across a line break). A no-op for empty or purely-LTR input, so callers
     * can apply it unconditionally without changing LTR behaviour.
     */
    fun toVisual(logicalLine: String): String {
        if (logicalLine.isEmpty() || !hasRtl(logicalLine)) return logicalLine
        val shaped = ArabicShaper.shape(logicalLine)
        val bidi = Bidi(shaped, Bidi.LEVEL_DEFAULT_LTR.toInt())
        return bidi.writeReordered(Bidi.DO_MIRRORING.toInt() or Bidi.KEEP_BASE_COMBINING.toInt())
    }

    private fun isRtl(code: Int): Boolean =
        code in 0x0590..0x05FF ||   // Hebrew
        code in 0x0600..0x06FF ||   // Arabic
        code in 0x0750..0x077F ||   // Arabic Supplement
        code in 0x08A0..0x08FF ||   // Arabic Extended-A
        code in 0xFB1D..0xFB4F ||   // Hebrew presentation forms
        code in 0xFB50..0xFDFF ||   // Arabic Presentation Forms-A
        code in 0xFE70..0xFEFF      // Arabic Presentation Forms-B
}
