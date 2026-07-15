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

    /** Located, but can't be edited in place (multi-token, or font can't encode the new
     *  text): caller should delete the run and regenerate it with the default font. */
    data object NeedsRedraw : TextReplaceResult

    data class Skipped(val reason: String) : TextReplaceResult
}

/**
 * In-place editing / deletion of an existing text layer, targeted by
 * **(text, occurrence)** rather than text alone, so duplicate strings on a page
 * can each be addressed uniquely.
 *
 * Runs are located by decoding each text-show operator's bytes to Unicode via the
 * font's ToUnicode map (works for subsetted fonts) and matching the target
 * ignoring whitespace; matches are collected in content order (non-overlapping)
 * and the caller's [occurrence] selects one. A run may span several consecutive
 * tokens.
 *
 * - Single-token match with a font that can encode the replacement -> in place
 *   ([Replaced]); nothing moves and no font is embedded.
 * - Otherwise [NeedsRedraw] (caller regenerates the whole run with the default font).
 * - Deletion blanks the matched tokens.
 */
class PdfTextEditor @Inject constructor() {

    private data class ShowToken(val index: Int, val text: String, val font: PDFont)
    private data class Match(val indices: List<Int>, val font: PDFont)

    fun replace(document: PDDocument, page: PDPage, target: String, occurrence: Int, replacement: String): TextReplaceResult {
        if (target.isBlank()) return TextReplaceResult.Skipped("対象テキストが空です")
        val resources = page.resources ?: return TextReplaceResult.Skipped("ページのフォント情報がありません")
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)

        val match = locateAll(collectShowTokens(tokens, resources), target).getOrNull(occurrence)
            ?: return TextReplaceResult.Skipped("対象テキストが見つかりません（画像内・特殊レイアウトの可能性）")
        if (match.indices.size != 1 || !PdfTextEditability.canEdit(match.font, replacement)) {
            return TextReplaceResult.NeedsRedraw
        }
        val newBytes = runCatching { match.font.encode(replacement) }
            .getOrElse { return TextReplaceResult.NeedsRedraw }
        val idx = match.indices[0]
        tokens[idx] = if (tokens[idx] is COSArray) COSArray().apply { add(COSString(newBytes)) } else COSString(newBytes)
        writeBack(document, page, tokens)
        return TextReplaceResult.Replaced
    }

    /** Deletes the [occurrence]-th run that shows [target]. */
    fun blank(document: PDDocument, page: PDPage, target: String, occurrence: Int): Boolean {
        val resources = page.resources ?: return false
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val match = locateAll(collectShowTokens(tokens, resources), target).getOrNull(occurrence) ?: return false
        match.indices.forEach { idx -> tokens[idx] = if (tokens[idx] is COSArray) COSArray() else COSString(ByteArray(0)) }
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

    /** All non-overlapping token subsequences whose decoded text equals [target] (whitespace-insensitive), in content order. */
    private fun locateAll(show: List<ShowToken>, target: String): List<Match> {
        val goal = target.filterNot { it.isWhitespace() }
        if (goal.isEmpty()) return emptyList()
        val result = ArrayList<Match>()
        var i = 0
        while (i < show.size) {
            val acc = StringBuilder()
            var matchedEnd = -1
            var j = i
            while (j < show.size) {
                acc.append(show[j].text.filterNot { it.isWhitespace() })
                val current = acc.toString()
                if (current == goal) { matchedEnd = j; break }
                if (current.length >= goal.length) break
                j++
            }
            if (matchedEnd >= 0) {
                result += Match((i..matchedEnd).map { show[it].index }, show[i].font)
                i = matchedEnd + 1
            } else {
                i++
            }
        }
        return result
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
