package com.micklab.pdf.domain.usecase

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.NoProgress
import com.micklab.pdf.core.ProgressCallback
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.ImageFormat
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.pdf.PdfWorkspace
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PDF 画像化: rasterizes pages to PNG/JPEG with the platform [PdfRenderer].
 * Output resolution follows [dpi] (a PDF point is 1/72 inch, so scale = dpi/72).
 */
class PdfToImagesUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
        source: Uri,
        pages: List<Int>?,
        dpi: Int,
        format: ImageFormat,
        jpegQuality: Int = 90,
        outputTree: Uri?,
        onProgress: ProgressCallback = NoProgress,
    ): List<OutputFile> = withContext(dispatchers.io) {
        // A real file guarantees a seekable descriptor for PdfRenderer.
        val temp = workspace.copyUriToTemp(source)
        val outputs = mutableListOf<OutputFile>()
        val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        // PdfRenderer / Page are AutoCloseable (not Closeable); close explicitly.
        val renderer = PdfRenderer(pfd)
        try {
            val indices = (pages?.filter { it in 0 until renderer.pageCount }
                ?.takeIf { it.isNotEmpty() })
                ?: (0 until renderer.pageCount).toList()
            val baseName = fileRepository.displayName(source).substringBeforeLast('.')
            val destination = outputTree.toDestination()
            val scale = (dpi.coerceIn(MIN_DPI, MAX_DPI)) / POINTS_PER_INCH

            indices.forEachIndexed { i, pageIndex ->
                coroutineContext.ensureActive()
                val page = renderer.openPage(pageIndex)
                try {
                    val (w, h) = clampToBudget(
                        (page.width * scale).roundToInt().coerceAtLeast(1),
                        (page.height * scale).roundToInt().coerceAtLeast(1),
                    )
                    val bitmap = try {
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    } catch (e: OutOfMemoryError) {
                        throw IOException("メモリ不足のため画像化できません。DPI を下げて再試行してください（現在 ${dpi}dpi）。", e)
                    }
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val name = "${baseName}_p${pageIndex + 1}.${format.extension}"
                        outputs += fileRepository.writeFile(destination, name, format.mimeType) { os ->
                            bitmap.compress(format.toCompressFormat(), jpegQuality, os)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
                onProgress((i + 1f) / indices.size, "ページ ${pageIndex + 1} を画像化中…")
            }
            Log.i(PdfToolsApp.TAG, "Rasterized ${outputs.size} page(s) at ${dpi}dpi")
            outputs
        } finally {
            renderer.close()
            pfd.close()
            workspace.delete(temp)
        }
    }

    /** Keeps a single bitmap under [MAX_PIXELS], scaling both sides if needed. */
    private fun clampToBudget(w: Int, h: Int): Pair<Int, Int> {
        val pixels = w.toLong() * h.toLong()
        if (pixels <= MAX_PIXELS) return w to h
        val factor = sqrt(MAX_PIXELS.toDouble() / pixels)
        return (w * factor).roundToInt().coerceAtLeast(1) to (h * factor).roundToInt().coerceAtLeast(1)
    }

    private fun ImageFormat.toCompressFormat(): Bitmap.CompressFormat = when (this) {
        ImageFormat.PNG -> Bitmap.CompressFormat.PNG
        ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
    }

    companion object {
        private const val POINTS_PER_INCH = 72f
        private const val MIN_DPI = 36
        private const val MAX_DPI = 600
        // ~12M px ceiling (~48 MB ARGB) to stay clear of OOM on modest devices.
        private const val MAX_PIXELS = 12_000_000L
    }
}
