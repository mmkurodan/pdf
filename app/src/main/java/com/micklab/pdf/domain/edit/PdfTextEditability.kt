package com.micklab.pdf.domain.edit

import com.tom_roush.pdfbox.pdmodel.font.PDFont

/**
 * Font-level checks that decide whether existing text can be edited *in place*.
 *
 * "Healthy font dictionary" is necessary but not sufficient: the font must be
 * embedded AND able to encode the replacement text (its subset must contain the
 * needed glyphs). PDFBox 2.0's [PDFont] exposes no `isEmbedded()`, so embedding
 * is inferred from the font descriptor's font-file streams.
 */
object PdfTextEditability {

    /** True if the font program is embedded (FontFile / FontFile2 / FontFile3 present). */
    fun isEmbedded(font: PDFont): Boolean {
        val descriptor = font.fontDescriptor ?: return false
        return descriptor.fontFile != null ||
            descriptor.fontFile2 != null ||
            descriptor.fontFile3 != null
    }

    /** True if [text] can be re-encoded with [font] (all glyphs available). */
    fun canEncode(font: PDFont, text: String): Boolean =
        runCatching { font.encode(text) }.isSuccess

    /** In-place editing is only possible with an embedded font that can encode the new text. */
    fun canEdit(font: PDFont, replacement: String): Boolean =
        isEmbedded(font) && canEncode(font, replacement)
}
