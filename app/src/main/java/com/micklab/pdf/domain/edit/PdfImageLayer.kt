package com.micklab.pdf.domain.edit

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject

/**
 * Images placed as a re-editable **layer inside the page content** — so every
 * viewer renders them (unlike annotation appearances, which android's PdfRenderer
 * doesn't draw) — while staying movable/deletable after saving.
 *
 * Each image is drawn as `q  w 0 0 h x y cm  /MicklabImg_<id> Do  Q`, where the
 * XObject is registered in the page resources under a recognizable name. Re-open
 * detection parses the content for those `Do`s and reads the preceding `cm` for
 * position; move rewrites that `cm`, delete drops the block.
 *
 * NOTE: the `cm` is axis-aligned (correct for un-rotated pages); image placement
 * on rotated pages may need tuning.
 */
object PdfImageLayer {

    private const val PREFIX = "MicklabImg_"

    fun newId(): String = System.nanoTime().toString()

    /** Draws [image] fitted (aspect-preserved) inside user-space [box]=[llx,lly,urx,ury], tagged [id]. */
    fun add(document: PDDocument, page: PDPage, box: FloatArray, image: PDImageXObject, id: String) {
        val (cx, cy, w, h) = fit(box, image)
        val resources = page.resources ?: PDResources().also { page.resources = it }
        val name = COSName.getPDFName(PREFIX + id)
        resources.put(name, image)

        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        tokens += Operator.getOperator("q")
        listOf(w, 0f, 0f, h, cx, cy).forEach { tokens += COSFloat(it) }
        tokens += Operator.getOperator("cm")
        tokens += name
        tokens += Operator.getOperator("Do")
        tokens += Operator.getOperator("Q")
        writeBack(document, page, tokens)
    }

    /** id + user-space rect [llx,lly,urx,ury] for each of our image layers, in page order. */
    data class Placed(val id: String, val box: FloatArray)

    fun list(page: PDPage): List<Placed> {
        val tokens = runCatching { PDFStreamParser(page).apply { parse() }.tokens }.getOrNull() ?: return emptyList()
        val out = ArrayList<Placed>()
        val operands = ArrayList<Any?>()
        var cm: FloatArray? = null
        for (token in tokens) {
            if (token is Operator) {
                when (token.name) {
                    "cm" -> cm = cmMatrix(operands)
                    "Do" -> {
                        val id = imageId(operands)
                        val m = cm
                        if (id != null && m != null) out += Placed(id, floatArrayOf(m[4], m[5], m[4] + m[0], m[5] + m[3]))
                        cm = null
                    }
                    else -> cm = null
                }
                operands.clear()
            } else {
                operands.add(token)
            }
        }
        return out
    }

    /** Places the [id] layer at [box]=[llx,lly,urx,ury] (position and size; keeps b/c = 0). */
    fun moveTo(document: PDDocument, page: PDPage, id: String, box: FloatArray): Boolean {
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val target = locate(tokens, id) ?: return false
        if (target.cmOperands.size != 6) return false
        tokens[target.cmOperands[0]] = COSFloat((box[2] - box[0]).coerceAtLeast(1f)) // width
        tokens[target.cmOperands[3]] = COSFloat((box[3] - box[1]).coerceAtLeast(1f)) // height
        tokens[target.cmOperands[4]] = COSFloat(box[0])
        tokens[target.cmOperands[5]] = COSFloat(box[1])
        writeBack(document, page, tokens)
        return true
    }

    fun remove(document: PDDocument, page: PDPage, id: String): Boolean {
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val target = locate(tokens, id) ?: return false
        val drop = (target.cmOperands + target.cmOp + target.nameIndex + target.doIndex).toHashSet()
        writeBack(document, page, tokens.filterIndexed { i, _ -> i !in drop })
        return true
    }

    private data class Target(val cmOperands: List<Int>, val cmOp: Int, val nameIndex: Int, val doIndex: Int)

    private fun locate(tokens: List<Any?>, id: String): Target? {
        val operandIndices = ArrayList<Int>()
        var cmOperands: List<Int> = emptyList()
        var cmOp = -1
        for (i in tokens.indices) {
            val token = tokens[i]
            if (token is Operator) {
                when (token.name) {
                    "cm" -> {
                        cmOperands = operandIndices.filter { tokens[it] is COSNumber }.takeLast(6)
                        cmOp = i
                    }
                    "Do" -> {
                        val nameIndex = operandIndices.lastOrNull { tokens[it] is COSName }
                        val thisId = (nameIndex?.let { tokens[it] as COSName })?.name
                            ?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
                        if (thisId == id) return Target(cmOperands, cmOp, nameIndex!!, i)
                        cmOperands = emptyList(); cmOp = -1
                    }
                    else -> { cmOperands = emptyList(); cmOp = -1 }
                }
                operandIndices.clear()
            } else {
                operandIndices.add(i)
            }
        }
        return null
    }

    private fun imageId(operands: List<Any?>): String? =
        (operands.lastOrNull { it is COSName } as? COSName)?.name
            ?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    private fun cmMatrix(operands: List<Any?>): FloatArray? {
        val nums = operands.filterIsInstance<COSNumber>().map { it.floatValue() }
        return if (nums.size >= 6) nums.subList(nums.size - 6, nums.size).toFloatArray() else null
    }

    /** cx, cy, w, h — the image fitted (aspect-preserved) inside the user-space [box]. */
    private fun fit(box: FloatArray, image: PDImageXObject): FloatArray {
        val bw = (box[2] - box[0]).coerceAtLeast(1f)
        val bh = (box[3] - box[1]).coerceAtLeast(1f)
        val iw = image.width.toFloat().coerceAtLeast(1f)
        val ih = image.height.toFloat().coerceAtLeast(1f)
        val scale = minOf(bw / iw, bh / ih)
        val w = iw * scale
        val h = ih * scale
        return floatArrayOf(box[0] + (bw - w) / 2f, box[1] + (bh - h) / 2f, w, h)
    }

    private fun writeBack(document: PDDocument, page: PDPage, tokens: List<Any?>) {
        val stream = PDStream(document)
        stream.createOutputStream(COSName.FLATE_DECODE).use { os -> ContentStreamWriter(os).writeTokens(tokens) }
        page.setContents(stream)
    }
}
