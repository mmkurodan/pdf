package com.micklab.pdf.domain.edit

import android.content.Context
import com.micklab.pdf.R
import com.micklab.pdf.core.LocaleManager
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
class PdfTextEditor @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    private data class ShowToken(val index: Int, val text: String, val font: PDFont)
    private data class Match(val indices: List<Int>, val font: PDFont)

    fun replace(document: PDDocument, page: PDPage, target: String, occurrence: Int, replacement: String): TextReplaceResult {
        if (target.isBlank()) return TextReplaceResult.Skipped(LocaleManager.string(appContext, R.string.pte_target_empty))
        val resources = page.resources ?: return TextReplaceResult.Skipped(LocaleManager.string(appContext, R.string.pte_no_font_info))
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)

        val match = locateAll(collectShowTokens(tokens, resources), target).getOrNull(occurrence)
            ?: return TextReplaceResult.Skipped(LocaleManager.string(appContext, R.string.pte_target_not_found))
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

    /** Deletes the [occurrence]-th run that shows [target] (and our underline, if any). */
    fun blank(document: PDDocument, page: PDPage, target: String, occurrence: Int): Boolean {
        val resources = page.resources ?: return false
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val match = locateAll(collectShowTokens(tokens, resources), target).getOrNull(occurrence) ?: return false
        match.indices.forEach { idx -> tokens[idx] = if (tokens[idx] is COSArray) COSArray() else COSString(ByteArray(0)) }
        // Also strip our own (marked-content tagged) underline drawn in the same q…Q, so moving
        // or deleting previously-added underlined text never leaves the rule behind.
        val drop = runCatching { ourUnderlineTokens(tokens, match.indices) }.getOrDefault(emptySet())
        val out = if (drop.isEmpty()) tokens else tokens.filterIndexed { i, _ -> i !in drop }
        writeBack(document, page, out)
        return true
    }

    /** Token indices of any [UNDERLINE_MC_TAG] marked-content block sharing the q…Q of [matchIndices]. */
    private fun ourUnderlineTokens(tokens: List<Any?>, matchIndices: List<Int>): Set<Int> {
        val first = matchIndices.minOrNull() ?: return emptySet()
        val last = matchIndices.maxOrNull() ?: return emptySet()
        val (q, qq) = enclosingBlock(tokens, first, last) ?: return emptySet()
        val drop = HashSet<Int>()
        var k = q
        while (k <= qq) {
            val t = tokens[k]
            if (t is Operator && t.name == "BMC") {
                val tagIdx = (k - 1 downTo q).firstOrNull { tokens[it] is COSName }
                if (tagIdx != null && (tokens[tagIdx] as COSName).name == UNDERLINE_MC_TAG) {
                    var depth = 1
                    var e = k + 1
                    while (e <= qq && depth > 0) {
                        val et = tokens[e]
                        if (et is Operator && et.name == "BMC") depth++
                        else if (et is Operator && et.name == "EMC") depth--
                        e++
                    }
                    (tagIdx until e).forEach { drop.add(it) }
                    k = e
                    continue
                }
            }
            k++
        }
        return drop
    }

    /** The innermost balanced q…Q enclosing [firstIdx]…[lastIdx], or null. */
    private fun enclosingBlock(tokens: List<Any?>, firstIdx: Int, lastIdx: Int): Pair<Int, Int>? {
        var depth = 0
        var q = -1
        for (i in firstIdx downTo 0) {
            val t = tokens[i]
            if (t is Operator && t.name == "Q") depth++
            else if (t is Operator && t.name == "q") { if (depth == 0) { q = i; break } else depth-- }
        }
        if (q < 0) return null
        depth = 0
        var qq = -1
        for (i in lastIdx until tokens.size) {
            val t = tokens[i]
            if (t is Operator && t.name == "q") depth++
            else if (t is Operator && t.name == "Q") { if (depth == 0) { qq = i; break } else depth-- }
        }
        return if (qq < 0) null else q to qq
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
