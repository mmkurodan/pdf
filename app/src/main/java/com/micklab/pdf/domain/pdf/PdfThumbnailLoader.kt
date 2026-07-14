package com.micklab.pdf.domain.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.data.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * On-demand page thumbnail renderer for lazy grids: a page is only rasterized
 * when it scrolls into view, and results are kept in a small LRU cache.
 *
 * Unscoped (`@Inject constructor` with no scope) so each ViewModel gets its own
 * instance; it keeps one [PdfRenderer] open per source. A [Mutex] serializes
 * access (PdfRenderer allows only one open page at a time). Call [close] when the
 * owning ViewModel is cleared.
 */
class PdfThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val fileRepository: FileRepository,
) {
    private val mutex = Mutex()
    private var tempFile: File? = null
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    private val cache = object : LinkedHashMap<Int, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>): Boolean = size > MAX_CACHE
    }

    /** Opens [uri] and returns its page count (0 on failure). */
    suspend fun open(uri: Uri): Int = withContext(dispatchers.io) {
        mutex.withLock {
            closeLocked()
            runCatching {
                val temp = File(context.cacheDir, "thumb_${System.nanoTime()}.pdf")
                fileRepository.openInput(uri).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                val descriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                val newRenderer = PdfRenderer(descriptor)
                tempFile = temp
                pfd = descriptor
                renderer = newRenderer
                newRenderer.pageCount
            }.getOrElse {
                Log.w(PdfToolsApp.TAG, "Thumbnail loader open failed", it)
                0
            }
        }
    }

    /** Renders (or returns a cached) thumbnail for [index]; null if unavailable. */
    suspend fun render(index: Int, targetWidthPx: Int = TARGET_WIDTH_PX): Bitmap? =
        withContext(dispatchers.io) {
            mutex.withLock {
                cache[index] ?: runCatching {
                    val activeRenderer = renderer ?: return@runCatching null
                    if (index !in 0 until activeRenderer.pageCount) return@runCatching null
                    val page = activeRenderer.openPage(index)
                    try {
                        val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                        val width = targetWidthPx
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        cache[index] = bitmap
                        bitmap
                    } finally {
                        page.close()
                    }
                }.getOrNull()
            }
        }

    /** Page size in PDF points (width, height) for the opened document, or null. */
    suspend fun pageSizePoints(index: Int): Pair<Float, Float>? = withContext(dispatchers.io) {
        mutex.withLock {
            val activeRenderer = renderer ?: return@withLock null
            if (index !in 0 until activeRenderer.pageCount) return@withLock null
            val page = activeRenderer.openPage(index)
            try {
                page.width.toFloat() to page.height.toFloat()
            } finally {
                page.close()
            }
        }
    }

    /** Best-effort release. Safe to call from the main thread (e.g. onCleared). */
    fun close() {
        runCatching { closeLocked() }
    }

    private fun closeLocked() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        runCatching { tempFile?.delete() }
        renderer = null
        pfd = null
        tempFile = null
        cache.clear()
    }

    companion object {
        private const val MAX_CACHE = 48
        private const val TARGET_WIDTH_PX = 240
    }
}
