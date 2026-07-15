package com.micklab.pdf.domain.edit

import android.content.Context
import android.net.Uri
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.data.repository.FileRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * One tappable unit of the embedded text layer. [occurrence] is the 0-based index
 * among runs on the page with the same text (in content order), so a run can be
 * targeted uniquely even when the same text appears several times.
 */
data class TextRun(val text: String, val rect: FractionRect, val fontSizePt: Float, val occurrence: Int)

/**
 * Reads the embedded text layer of a PDF as positioned [TextRun]s so the editor
 * can hit-test taps and pre-fill the current wording. Unscoped (one per
 * ViewModel); it keeps a [PDDocument] open for the session. Call [close] when done.
 *
 * NOTE: the mapping from PDFTextStripper's direction-adjusted coordinates to
 * visual page fractions is best-effort and worth confirming on-device.
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

    private fun extract(doc: PDDocument, pageIndex: Int): List<TextRun> {
        val page = doc.getPage(pageIndex)
        val crop = page.cropBox
        val rotated = ((page.rotation % 360) + 360) % 360 == 90 || ((page.rotation % 360) + 360) % 360 == 270
        val visW = if (rotated) crop.height else crop.width
        val visH = if (rotated) crop.width else crop.height
        if (visW <= 0f || visH <= 0f) return emptyList()

        val runs = ArrayList<TextRun>()
        val occurrences = HashMap<String, Int>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: List<TextPosition>) {
                val trimmed = text.trim()
                if (trimmed.isEmpty() || textPositions.isEmpty()) return
                var left = Float.MAX_VALUE
                var right = -Float.MAX_VALUE
                var top = Float.MAX_VALUE
                var bottom = -Float.MAX_VALUE
                var size = 0f
                textPositions.forEach { p ->
                    val x = p.xDirAdj
                    val yBottom = p.yDirAdj
                    left = min(left, x)
                    right = max(right, x + p.widthDirAdj)
                    top = min(top, yBottom - p.heightDir)
                    bottom = max(bottom, yBottom)
                    size = max(size, p.fontSizeInPt)
                }
                // Key on the same whitespace-stripped text that the editor matches on,
                // in content order, so occurrence indices line up at apply time.
                val key = trimmed.filterNot { it.isWhitespace() }
                val occurrence = occurrences.getOrDefault(key, 0)
                occurrences[key] = occurrence + 1
                runs += TextRun(
                    text = trimmed,
                    rect = FractionRect(
                        (left / visW).coerceIn(0f, 1f),
                        (top / visH).coerceIn(0f, 1f),
                        (right / visW).coerceIn(0f, 1f),
                        (bottom / visH).coerceIn(0f, 1f),
                    ),
                    fontSizePt = size,
                    occurrence = occurrence,
                )
            }
        }
        // Content order (not position order) so occurrence indices match the editor's token scan.
        stripper.setSortByPosition(false)
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(doc)
        return runs
    }

    fun close() {
        runCatching { closeLocked() }
    }

    private fun closeLocked() {
        runCatching { document?.close() }
        runCatching { tempFile?.delete() }
        document = null
        tempFile = null
    }
}
