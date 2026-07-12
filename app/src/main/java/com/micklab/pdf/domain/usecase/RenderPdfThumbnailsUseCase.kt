package com.micklab.pdf.domain.usecase

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.pdf.PdfWorkspace
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

/** A single page rendered as a small preview bitmap. */
data class PageBitmap(val index: Int, val bitmap: Bitmap)

/**
 * Renders low-resolution page previews for the thumbnail-based UIs (page
 * selection / reordering). Capped in count and size to stay light on memory.
 */
class RenderPdfThumbnailsUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
        source: Uri,
        maxPages: Int = MAX_PAGES,
        targetWidthPx: Int = TARGET_WIDTH_PX,
    ): List<PageBitmap> = withContext(dispatchers.io) {
        val temp = workspace.copyUriToTemp(source)
        val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val thumbnails = ArrayList<PageBitmap>()
        try {
            val count = min(renderer.pageCount, maxPages)
            for (i in 0 until count) {
                coroutineContext.ensureActive()
                val page = renderer.openPage(i)
                try {
                    val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                    val width = targetWidthPx
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    thumbnails.add(PageBitmap(i, bitmap))
                } finally {
                    page.close()
                }
            }
            thumbnails
        } finally {
            renderer.close()
            pfd.close()
            workspace.delete(temp)
        }
    }

    companion object {
        const val MAX_PAGES = 200
        private const val TARGET_WIDTH_PX = 220
    }
}
