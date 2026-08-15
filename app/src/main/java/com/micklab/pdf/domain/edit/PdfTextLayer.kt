package com.micklab.pdf.domain.edit

import android.content.Context
import android.net.Uri
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.data.repository.FileRepository
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * One tappable unit of the embedded text layer. [occurrence] is the 0-based index
 * among runs on the page with the same text (in content order), so a run can be
 * targeted uniquely even when the same text appears several times.
 */
data class TextRun(
    val text: String,
    val rect: FractionRect,
    val fontSizePt: Float,
    val occurrence: Int,
    val colorRgb: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)

/** A detected image-annotation layer: its box (visual fractions) and stable id. */
data class ImageLayer(val rect: FractionRect, val id: String)

/**
 * Reads the embedded text layer of a PDF as positioned [TextRun]s so the editor
 * can hit-test taps and pre-fill the current wording. Unscoped (one per
 * ViewModel); it keeps a [PDDocument] open for the session. Call [close] when done.
 */
class PdfTextLayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val fileRepository: FileRepository,
) {
    private val mutex = Mutex()
    private var tempFile: File? = null
    private var document: PDDocument? = null

    suspend fun open(uri: Uri) = withContext(dispatchers.io) {
        mutex.withLock {
            closeLocked()
            runCatching {
                val temp = File(context.cacheDir, "textlayer_${System.nanoTime()}.pdf")
                fileRepository.openInput(uri).use { input -> temp.outputStream().use { input.copyTo(it) } }
                tempFile = temp
                document = PDDocument.load(temp)
            }.onFailure { Log.w(PdfToolsApp.TAG, "Text-layer open failed", it) }
            Unit
        }
    }

    /** Positioned text runs on [pageIndex], or empty if unavailable. */
    suspend fun runs(pageIndex: Int): List<TextRun> = withContext(dispatchers.io) {
        mutex.withLock {
            val doc = document ?: return@withLock emptyList()
            if (pageIndex !in 0 until doc.numberOfPages) return@withLock emptyList()
            runCatching { extract(doc, pageIndex) }.getOrDefault(emptyList())
        }
    }

    /** Our image-annotation layers on [pageIndex] (box in visual fractions + id). */
    suspend fun imageLayers(pageIndex: Int): List<ImageLayer> = withContext(dispatchers.io) {
        mutex.withLock {
            val doc = document ?: return@withLock emptyList()
            if (pageIndex !in 0 until doc.numberOfPages) return@withLock emptyList()
            runCatching {
                val page = doc.getPage(pageIndex)
                val crop = page.cropBox
                PdfImageLayer.list(page).map { placed ->
                    ImageLayer(
                        PdfCoordinateMapper.toFractionRect(
                            crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.rotation,
                            placed.box[0], placed.box[1], placed.box[2], placed.box[3],
                        ),
                        placed.id,
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun extract(doc: PDDocument, pageIndex: Int): List<TextRun> {
        val page = doc.getPage(pageIndex)
        val crop = page.cropBox
        val rotated = ((page.rotation % 360) + 360) % 360 == 90 || ((page.rotation % 360) + 360) % 360 == 270
        val visW = if (rotated) crop.height else crop.width
        val visH = if (rotated) crop.width else crop.height
        if (visW <= 0f || visH <= 0f) return emptyList()

        // Content-stream scan: more reliable color, font size, and underline than PDFTextStripper.
        val streamAttrs = runCatching { extractStreamAttrs(page) }.getOrDefault(emptyMap())

        val runs = ArrayList<TextRun>()
        val occurrences = HashMap<String, Int>()
        val streamOccurrences = HashMap<String, Int>()
        val colorByPosition = HashMap<Long, Int>()

        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                colorByPosition[positionColorKey(text)] = runCatching {
                    graphicsState.nonStrokingColor.toRGB() and 0xFFFFFF
                }.getOrElse {
                    runCatching {
                        val comps = graphicsState.nonStrokingColor.components
                        when {
                            comps.size >= 3 -> {
                                val r = (comps[0] * 255).toInt().coerceIn(0, 255)
                                val g = (comps[1] * 255).toInt().coerceIn(0, 255)
                                val b = (comps[2] * 255).toInt().coerceIn(0, 255)
                                (r shl 16) or (g shl 8) or b
                            }
                            comps.size == 1 -> {
                                val v = (comps[0] * 255).toInt().coerceIn(0, 255)
                                (v shl 16) or (v shl 8) or v
                            }
                            else -> 0x000000
                        }
                    }.getOrDefault(0x000000)
                }
                super.processTextPosition(text)
            }

            override fun writeString(text: String, textPositions: List<TextPosition>) {
                val trimmed = text.trim()
                if (trimmed.isEmpty() || textPositions.isEmpty()) return
                var left = Float.MAX_VALUE; var right = -Float.MAX_VALUE
                var top = Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
                var size = 0f; var bold = false; var italic = false
                textPositions.forEach { p ->
                    val x = p.xDirAdj; val yBottom = p.yDirAdj
                    left = min(left, x); right = max(right, x + p.widthDirAdj)
                    top = min(top, yBottom - p.heightDir); bottom = max(bottom, yBottom)
                    size = max(size, p.fontSizeInPt)
                    if (!bold || !italic) runCatching {
                        val pdFont = p.font; val desc = pdFont?.fontDescriptor
                        val fontName = (pdFont?.name ?: "").stripSubsetPrefix()
                        val descName = (desc?.fontName ?: "").stripSubsetPrefix()
                        val names = "$fontName $descName".lowercase()
                        if (!bold) bold = desc?.isForceBold() == true || (desc?.fontWeight ?: 400f) >= 700f ||
                            names.contains("bold") || names.contains("heavy") || names.contains("black") ||
                            names.contains("demi") || names.contains("semibold") || names.contains("extrabold")
                        if (!italic) italic = desc?.isItalic() == true || (desc?.italicAngle ?: 0f) != 0f ||
                            names.contains("italic") || names.contains("oblique") || names.contains("slanted")
                    }
                }
                val key = trimmed.filterNot { it.isWhitespace() }
                val occurrence = occurrences.getOrDefault(key, 0)
                occurrences[key] = occurrence + 1

                // Prefer stream-derived attributes (reliable color, font size, underline).
                val streamOcc = streamOccurrences.getOrDefault(key, 0)
                streamOccurrences[key] = streamOcc + 1
                val attrs = streamAttrs[key to streamOcc]

                val finalColor = attrs?.colorRgb
                    ?: textPositions.firstNotNullOfOrNull { colorByPosition[positionColorKey(it)] }
                    ?: 0x000000
                val finalSize = if (attrs != null && attrs.fontSizePt > 0f) attrs.fontSizePt else size
                val finalUnderline = attrs?.underline ?: false

                runs += TextRun(
                    text = trimmed,
                    rect = FractionRect(
                        (left / visW).coerceIn(0f, 1f), (top / visH).coerceIn(0f, 1f),
                        (right / visW).coerceIn(0f, 1f), (bottom / visH).coerceIn(0f, 1f),
                    ),
                    fontSizePt = finalSize,
                    occurrence = occurrence,
                    colorRgb = finalColor,
                    bold = bold,
                    italic = italic,
                    underline = finalUnderline,
                )
            }
        }
        stripper.setSortByPosition(false)
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(doc)
        return runs
    }

    fun close() { runCatching { closeLocked() } }

    private fun closeLocked() {
        runCatching { document?.close() }
        runCatching { tempFile?.delete() }
        document = null; tempFile = null
    }

    // ---- Content-stream attribute scanner ----

    private data class StreamAttrs(val colorRgb: Int, val fontSizePt: Float, val underline: Boolean)

    /**
     * Parses the page content stream to extract text attributes (color, font size, underline)
     * directly from graphics state, which is more reliable than PDFTextStripper for our
     * embedded Noto CID fonts.  Returns a map keyed by (whitespace-stripped text, occurrence).
     */
    private fun extractStreamAttrs(page: PDPage): Map<Pair<String, Int>, StreamAttrs> {
        val resources = page.resources ?: return emptyMap()
        val tokens = ArrayList<Any?>(PDFStreamParser(page).apply { parse() }.tokens)
        val result = HashMap<Pair<String, Int>, StreamAttrs>()
        val occurrences = HashMap<String, Int>()

        var r = 0f; var g = 0f; var b = 0f  // current non-stroking DeviceRGB
        val colorStack = ArrayDeque<Triple<Float, Float, Float>>()
        var fontSize = 12f
        val fontSizeStack = ArrayDeque<Float>()
        var currentFont: PDFont? = null
        var lastKey: Pair<String, Int>? = null
        val operandIndices = ArrayList<Int>()

        fun toColorRgb() =
            ((r * 255).toInt().coerceIn(0, 255) shl 16) or
            ((g * 255).toInt().coerceIn(0, 255) shl 8) or
            (b * 255).toInt().coerceIn(0, 255)

        fun decodeFont(bytes: ByteArray): String {
            val font = currentFont ?: return ""
            if (bytes.isEmpty()) return ""
            val inp = ByteArrayInputStream(bytes)
            val sb = StringBuilder()
            var guard = bytes.size + 4
            runCatching {
                while (inp.available() > 0 && guard-- > 0) {
                    val code = font.readCode(inp)
                    sb.append(font.toUnicode(code) ?: "")
                }
            }
            return sb.toString()
        }

        fun commitShow(text: String) {
            val trimmed = text.trim()
            if (trimmed.isBlank()) return
            val key = trimmed.filterNot { it.isWhitespace() }
            val occ = occurrences.getOrDefault(key, 0)
            occurrences[key] = occ + 1
            val pair = key to occ
            result[pair] = StreamAttrs(toColorRgb(), fontSize, false)
            lastKey = pair
        }

        tokens.forEachIndexed { i, token ->
            if (token !is Operator) { operandIndices.add(i); return@forEachIndexed }
            when (token.name) {
                "q" -> {
                    colorStack.addLast(Triple(r, g, b))
                    fontSizeStack.addLast(fontSize)
                }
                "Q" -> {
                    colorStack.removeLastOrNull()?.also { (cr, cg, cb) -> r = cr; g = cg; b = cb }
                    fontSizeStack.removeLastOrNull()?.also { fontSize = it }
                }
                // Non-stroking DeviceRGB: r g b rg
                "rg" -> {
                    val nums = operandIndices.mapNotNull { (tokens[it] as? Number)?.toFloat() }
                    if (nums.size >= 3) { r = nums[nums.size - 3]; g = nums[nums.size - 2]; b = nums[nums.size - 1] }
                }
                // Non-stroking DeviceGray: gray g
                "g" -> {
                    val v = operandIndices.lastOrNull()?.let { (tokens[it] as? Number)?.toFloat() }
                    if (v != null) { r = v; g = v; b = v }
                }
                // Non-stroking CMYK: c m y k k
                "k" -> {
                    val nums = operandIndices.mapNotNull { (tokens[it] as? Number)?.toFloat() }
                    if (nums.size >= 4) {
                        val c = nums[nums.size - 4]; val m = nums[nums.size - 3]
                        val y = nums[nums.size - 2]; val k = nums[nums.size - 1]
                        r = (1f - c) * (1f - k); g = (1f - m) * (1f - k); b = (1f - y) * (1f - k)
                    }
                }
                // Set font and size: name size Tf
                "Tf" -> {
                    val nameIdx = operandIndices.firstOrNull { tokens[it] is COSName }
                    if (nameIdx != null) {
                        currentFont = runCatching { resources.getFont(tokens[nameIdx] as COSName) }.getOrNull()
                    }
                    val sizeNum = operandIndices.lastOrNull { tokens[it] is Number }
                        ?.let { (tokens[it] as Number).toFloat() }
                    if (sizeNum != null && sizeNum > 0f) fontSize = sizeNum
                }
                // Text show operators
                "Tj", "'", "\"" -> {
                    val strIdx = operandIndices.lastOrNull { tokens[it] is COSString }
                    if (strIdx != null) commitShow(decodeFont((tokens[strIdx] as COSString).bytes))
                }
                "TJ" -> {
                    val arrIdx = operandIndices.lastOrNull { tokens[it] is COSArray }
                    if (arrIdx != null) {
                        val buf = ByteArrayOutputStream()
                        (tokens[arrIdx] as COSArray).toList().forEach { if (it is COSString) buf.write(it.bytes) }
                        commitShow(decodeFont(buf.toByteArray()))
                    }
                }
                // Underline detection: our app tags its underline with UNDERLINE_MC_TAG
                "BMC" -> {
                    val nameIdx = operandIndices.lastOrNull { tokens[it] is COSName }
                    if (nameIdx != null && (tokens[nameIdx] as COSName).name == UNDERLINE_MC_TAG) {
                        lastKey?.let { k -> result[k]?.let { a -> result[k] = a.copy(underline = true) } }
                    }
                }
            }
            operandIndices.clear()
        }
        return result
    }
}

/** Strip the 6-uppercase-letter subset prefix PDF embedders add (e.g. "ABCDEF+FontName" → "FontName"). */
private fun String.stripSubsetPrefix(): String =
    if (length > 7 && this[6] == '+' && substring(0, 6).all { it.isUpperCase() }) substring(7) else this

/** Rounded (x, y) position key used to associate a captured fill colour with a run. */
private fun positionColorKey(p: TextPosition): Long =
    (p.xDirAdj.toLong() shl 20) or (p.yDirAdj.toLong() and 0xFFFFFL)
