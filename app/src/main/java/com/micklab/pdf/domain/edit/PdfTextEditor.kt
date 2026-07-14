package com.micklab.pdf.domain.edit

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Outcome of an in-place text-layer replacement attempt. */
sealed interface TextReplaceResult {
    /** Rewritten in place with the page's own (embedded) font. */
    data object Replaced : TextReplaceResult

    /** Located uniquely, but the font isn't embedded / can't encode the replacement:
     *  the caller should delete the run ([blankFirst]) and redraw with the default font. */
    data object NeedsRedraw : TextReplaceResult

    data class Skipped(val reason: String) : TextReplaceResult
}

/**
 * In-place editing of an existing text layer. The target run is located by
 * re-encoding it with the page's own font and byte-matching the shown text
 * (Tj / ' / " with a COSString, or TJ with a COSArray) on the page's own content
 * stream — never by guessing. A unique match is required.
 *
 * - If that font is embedded and can encode the replacement, the token is
 *   rewritten in place ([Replaced]).
 * - If not, [replaceFirst] returns [NeedsRedraw] and the caller deletes the run
 *   via [blankFirst] and redraws it with the default font (per product decision:
 *   no embedded font -> drop the text-layer run and re-add with the default font;
 *   still no white-out).
 * - Missing / ambiguous matches are skipped.
 */
class PdfTextEditor @Inject constructor() {

    fun replaceFirst(document: PDDocument, page: PDPage, target: String, replacement: String): TextReplaceResult {
        if (target.isEmpty()) return TextReplaceResult.Skipped("対象テキストが空です")
        val resources = page.resources ?: return TextReplaceResult.Skipped("ページのフォント情報がありません")

        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val located = locate(tokens, resources, target)
        if (located.size != 1) {
            return TextReplaceResult.Skipped(
                if (located.size > 1) "複数箇所に一致したため、安全のため編集しませんでした"
                else "対象テキストが見つかりません（画像内・特殊レイアウトの可能性）",
            )
        }

        val (idx, font) = located[0]
        if (!PdfTextEditability.canEdit(font, replacement)) return TextReplaceResult.NeedsRedraw
        val newBytes = runCatching { font.encode(replacement) }.getOrElse { return TextReplaceResult.NeedsRedraw }

        tokens[idx] = if (tokens[idx] is COSArray) COSArray().apply { add(COSString(newBytes)) } else COSString(newBytes)
        writeBack(document, page, tokens)
        return TextReplaceResult.Replaced
    }

    /** Deletes the (unique) run that shows [target], leaving the rest untouched. */
    fun blankFirst(document: PDDocument, page: PDPage, target: String): Boolean {
        val resources = page.resources ?: return false
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val located = locate(tokens, resources, target)
        if (located.size != 1) return false
        val idx = located[0].first
        tokens[idx] = if (tokens[idx] is COSArray) COSArray() else COSString(ByteArray(0))
        writeBack(document, page, tokens)
        return true
    }

    /** Operand-token indices (with their font) whose shown bytes equal encode([target]). */
    private fun locate(tokens: List<Any?>, resources: PDResources, target: String): List<Pair<Int, PDFont>> {
        val out = ArrayList<Pair<Int, PDFont>>()
        val operandIndices = ArrayList<Int>()
        var currentFont: PDFont? = null
        tokens.forEachIndexed { i, token ->
            if (token is Operator) {
                when (token.name) {
                    "Tf" -> operandIndices.firstOrNull { tokens[it] is COSName }?.let { idx ->
                        currentFont = runCatching { resources.getFont(tokens[idx] as COSName) }.getOrNull()
                    }
                    "Tj", "'", "\"" -> operandIndices.lastOrNull { tokens[it] is COSString }?.let { idx ->
                        val font = currentFont
                        if (font != null && matches(font, (tokens[idx] as COSString).bytes, target)) out += idx to font
                    }
                    "TJ" -> operandIndices.lastOrNull { tokens[it] is COSArray }?.let { idx ->
                        val font = currentFont
                        if (font != null && matches(font, concatStrings(tokens[idx] as COSArray), target)) out += idx to font
                    }
                }
                operandIndices.clear()
            } else {
                operandIndices.add(i)
            }
        }
        return out
    }

    private fun matches(font: PDFont, shownBytes: ByteArray, target: String): Boolean {
        val encoded = runCatching { font.encode(target) }.getOrNull() ?: return false
        return shownBytes.contentEquals(encoded)
    }

    private fun concatStrings(array: COSArray): ByteArray {
        val out = ByteArrayOutputStream()
        array.toList().forEach { if (it is COSString) out.write(it.bytes) }
        return out.toByteArray()
    }

    private fun writeBack(document: PDDocument, page: PDPage, tokens: List<Any?>) {
        val stream = PDStream(document)
        stream.createOutputStream(COSName.FLATE_DECODE).use { os -> ContentStreamWriter(os).writeTokens(tokens) }
        page.setContents(stream)
    }
}
