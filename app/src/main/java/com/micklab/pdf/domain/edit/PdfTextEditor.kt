package com.micklab.pdf.domain.edit

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Outcome of an in-place text-layer replacement. */
sealed interface TextReplaceResult {
    data object Applied : TextReplaceResult
    data class Skipped(val reason: String) : TextReplaceResult
}

/**
 * In-place editing of an existing text layer, performed ONLY when it is
 * genuinely safe. The target is located by re-encoding it with the page's own
 * font and byte-matching the shown text (never by guessing), and the same font
 * must be embedded and able to encode the replacement. If the match is missing,
 * ambiguous (more than one), or the font can't render the new text, the edit is
 * skipped rather than faked — there is no white-out fallback (product decision).
 *
 * Only text-showing operators on the page's own content stream are handled
 * (Tj / ' / " with a COSString, and TJ with a COSArray); anything else — text in
 * form XObjects, split across kerned runs that don't byte-match — is skipped.
 */
class PdfTextEditor @Inject constructor() {

    private enum class Match { NONE, FOUND_NOT_EDITABLE, EDITABLE }

    fun replaceFirst(document: PDDocument, page: PDPage, target: String, replacement: String): TextReplaceResult {
        if (target.isEmpty()) return TextReplaceResult.Skipped("対象テキストが空です")
        val resources = page.resources ?: return TextReplaceResult.Skipped("ページのフォント情報がありません")

        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val operandIndices = ArrayList<Int>()
        var currentFont: PDFont? = null
        val editableMatches = ArrayList<Int>()   // operand-token indices that can be replaced
        var matchFont: PDFont? = null
        var located = false                       // target found at all, even if not editable

        tokens.forEachIndexed { i, token ->
            if (token is Operator) {
                when (token.name) {
                    "Tf" -> operandIndices.firstOrNull { tokens[it] is COSName }?.let { idx ->
                        currentFont = runCatching { resources.getFont(tokens[idx] as COSName) }.getOrNull()
                    }

                    "Tj", "'", "\"" -> operandIndices.lastOrNull { tokens[it] is COSString }?.let { idx ->
                        val m = evaluate(currentFont, (tokens[idx] as COSString).bytes, target, replacement)
                        if (m != Match.NONE) located = true
                        if (m == Match.EDITABLE) { editableMatches += idx; matchFont = currentFont }
                    }

                    "TJ" -> operandIndices.lastOrNull { tokens[it] is COSArray }?.let { idx ->
                        val m = evaluate(currentFont, concatStrings(tokens[idx] as COSArray), target, replacement)
                        if (m != Match.NONE) located = true
                        if (m == Match.EDITABLE) { editableMatches += idx; matchFont = currentFont }
                    }
                }
                operandIndices.clear()
            } else {
                operandIndices.add(i)
            }
        }

        if (editableMatches.size != 1) {
            return TextReplaceResult.Skipped(
                when {
                    editableMatches.size > 1 -> "複数箇所に一致したため、安全のため編集しませんでした"
                    located -> "対象は見つかりましたが、埋め込みフォントでない/置換文字を描画できないため編集しませんでした"
                    else -> "対象テキストが見つかりません（画像内・特殊レイアウトの可能性）"
                },
            )
        }

        val font = matchFont ?: return TextReplaceResult.Skipped("フォントを特定できませんでした")
        val newBytes = runCatching { font.encode(replacement) }
            .getOrElse { return TextReplaceResult.Skipped("置換文字をこのフォントで描画できません") }

        val idx = editableMatches[0]
        tokens[idx] = when (tokens[idx]) {
            is COSArray -> COSArray().apply { add(COSString(newBytes)) }
            else -> COSString(newBytes)
        }

        val stream = PDStream(document)
        stream.createOutputStream(COSName.FLATE_DECODE).use { os -> ContentStreamWriter(os).writeTokens(tokens) }
        page.setContents(stream)
        return TextReplaceResult.Applied
    }

    /** Whether [shownBytes] render [target] under [font], and if [replacement] can be re-encoded. */
    private fun evaluate(font: PDFont?, shownBytes: ByteArray, target: String, replacement: String): Match {
        if (font == null) return Match.NONE
        val encodedTarget = runCatching { font.encode(target) }.getOrNull() ?: return Match.NONE
        if (!shownBytes.contentEquals(encodedTarget)) return Match.NONE
        return if (PdfTextEditability.canEdit(font, replacement)) Match.EDITABLE else Match.FOUND_NOT_EDITABLE
    }

    private fun concatStrings(array: COSArray): ByteArray {
        val out = ByteArrayOutputStream()
        array.toList().forEach { if (it is COSString) out.write(it.bytes) }
        return out.toByteArray()
    }
}
