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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Outcome of an in-place text-layer replacement attempt. */
sealed interface TextReplaceResult {
    data object Replaced : TextReplaceResult
    data class Skipped(val reason: String) : TextReplaceResult
}

/**
 * In-place editing / deletion of an existing text layer.
 *
 * The tapped text is located by **decoding each text-show operator's bytes to
 * Unicode via the font's ToUnicode map** (readCode + toUnicode) and matching the
 * target ignoring whitespace (PDFTextStripper inserts positional spaces that
 * aren't in the tokens). This works even for subsetted embedded fonts. A run may
 * span several consecutive tokens; a unique match is required.
 *
 * Replacement is done **in place with the page's own font** — same position,
 * same font, so nothing moves and no font is embedded (no size bloat). It only
 * succeeds when that font can encode the new text; otherwise the edit is skipped
 * with a reason (there is deliberately no re-draw-with-another-font fallback,
 * which would embed a font and could misplace the text). Deletion just blanks
 * the matched tokens.
 */
class PdfTextEditor @Inject constructor() {

    private data class ShowToken(val index: Int, val text: String, val font: PDFont)

    private sealed interface Loc {
        data object NotFound : Loc
        data object Ambiguous : Loc
        data class Found(val indices: List<Int>, val font: PDFont) : Loc
    }

    fun replaceFirst(document: PDDocument, page: PDPage, target: String, replacement: String): TextReplaceResult {
        if (target.isBlank()) return TextReplaceResult.Skipped("対象テキストが空です")
        val resources = page.resources ?: return TextReplaceResult.Skipped("ページのフォント情報がありません")
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)

        return when (val loc = locate(collectShowTokens(tokens, resources), target)) {
            Loc.NotFound -> TextReplaceResult.Skipped("対象テキストが見つかりません（画像内・特殊レイアウトの可能性）")
            Loc.Ambiguous -> TextReplaceResult.Skipped("複数箇所に一致したため、安全のため編集しませんでした")
            is Loc.Found -> {
                if (loc.indices.size != 1) {
                    return TextReplaceResult.Skipped("複数の描画単位にまたがるため、そのままでは編集できません")
                }
                if (!PdfTextEditability.canEdit(loc.font, replacement)) {
                    return TextReplaceResult.Skipped("元のフォントでは置換後の文字を表示できないため編集できません")
                }
                val newBytes = runCatching { loc.font.encode(replacement) }
                    .getOrElse { return TextReplaceResult.Skipped("置換後の文字を元のフォントで表示できません") }
                val idx = loc.indices[0]
                tokens[idx] = if (tokens[idx] is COSArray) COSArray().apply { add(COSString(newBytes)) } else COSString(newBytes)
                writeBack(document, page, tokens)
                TextReplaceResult.Replaced
            }
        }
    }

    /** Deletes the (unique) run that shows [target], leaving everything else untouched. */
    fun blankFirst(document: PDDocument, page: PDPage, target: String): Boolean {
        val resources = page.resources ?: return false
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val loc = locate(collectShowTokens(tokens, resources), target)
        if (loc !is Loc.Found) return false
        loc.indices.forEach { idx -> tokens[idx] = if (tokens[idx] is COSArray) COSArray() else COSString(ByteArray(0)) }
        writeBack(document, page, tokens)
        return true
    }

    /** Text-show tokens in order, each decoded to Unicode with its font. */
    private fun collectShowTokens(tokens: List<Any?>, resources: PDResources): List<ShowToken> {
        val out = ArrayList<ShowToken>()
        val operandIndices = ArrayList<Int>()
        var currentFont: PDFont? = null
        tokens.forEachIndexed { i, token ->
            if (token is Operator) {
                when (token.name) {
                    "Tf" -> operandIndices.firstOrNull { tokens[it] is COSName }?.let { idx ->
                        currentFont = runCatching { resources.getFont(tokens[idx] as COSName) }.getOrNull()
                    }
                    "Tj", "'", "\"" -> operandIndices.lastOrNull { tokens[it] is COSString }?.let { idx ->
                        currentFont?.let { out += ShowToken(idx, decode(it, (tokens[idx] as COSString).bytes), it) }
                    }
                    "TJ" -> operandIndices.lastOrNull { tokens[it] is COSArray }?.let { idx ->
                        currentFont?.let { out += ShowToken(idx, decode(it, concatStrings(tokens[idx] as COSArray)), it) }
                    }
                }
                operandIndices.clear()
            } else {
                operandIndices.add(i)
            }
        }
        return out
    }

    /** Unique contiguous token subsequence whose decoded text equals [target], ignoring whitespace. */
    private fun locate(show: List<ShowToken>, target: String): Loc {
        val goal = target.filterNot { it.isWhitespace() }
        if (goal.isEmpty()) return Loc.NotFound
        val matches = ArrayList<Loc.Found>()
        for (i in show.indices) {
            val acc = StringBuilder()
            for (j in i until show.size) {
                acc.append(show[j].text.filterNot { it.isWhitespace() })
                val current = acc.toString()
                if (current == goal) {
                    matches += Loc.Found((i..j).map { show[it].index }, show[i].font)
                    break
                }
                if (current.length >= goal.length) break
            }
        }
        return when (matches.size) {
            0 -> Loc.NotFound
            1 -> matches[0]
            else -> Loc.Ambiguous
        }
    }

    private fun decode(font: PDFont, bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val input = ByteArrayInputStream(bytes)
        val sb = StringBuilder()
        var guard = bytes.size + 4
        runCatching {
            while (input.available() > 0 && guard-- > 0) {
                val code = font.readCode(input)
                sb.append(font.toUnicode(code) ?: "")
            }
        }
        return sb.toString()
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
