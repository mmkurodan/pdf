package com.micklab.pdf.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.NoProgress
import com.micklab.pdf.core.ProgressCallback
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/** How each image maps to a PDF page. */
enum class PagePreset { FIT_A4, MATCH_IMAGE }

/**
 * 画像 → PDF 化: builds a single PDF from [images] in the given order. Images are
 * downsampled to bound memory, flattened onto white (JPEG has no alpha), then
 * embedded as JPEG to keep the file small.
 */
class ImagesToPdfUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
        images: List<Uri>,
        preset: PagePreset,
        outputTree: Uri?,
        jpegQuality: Float = 0.9f,
        onProgress: ProgressCallback = NoProgress,
    ): OutputFile = withContext(dispatchers.io) {
        require(images.isNotEmpty()) { "画像が選択されていません" }

        PDDocument().use { document ->
            images.forEachIndexed { i, uri ->
                coroutineContext.ensureActive()
                val bitmap = decodeDownsampled(uri) ?: return@forEachIndexed
                val opaque = bitmap.flattenOntoWhite()
                try {
                    val image = JPEGFactory.createFromImage(document, opaque, jpegQuality)
                    val page = when (preset) {
                        PagePreset.FIT_A4 -> PDPage(PDRectangle.A4)
                        PagePreset.MATCH_IMAGE -> {
                            val wPt = image.width * POINTS_PER_INCH / ASSUMED_DPI
                            val hPt = image.height * POINTS_PER_INCH / ASSUMED_DPI
                            PDPage(PDRectangle(wPt, hPt))
                        }
                    }
                    document.addPage(page)
                    PDPageContentStream(document, page).use { cs ->
                        val box = page.mediaBox
                        val scale = min(
                            (box.width - MARGIN * 2) / image.width,
                            (box.height - MARGIN * 2) / image.height,
                        )
                        val drawW = image.width * scale
                        val drawH = image.height * scale
                        val x = (box.width - drawW) / 2f
                        val y = (box.height - drawH) / 2f
                        cs.drawImage(image, x, y, drawW, drawH)
                    }
                } finally {
                    if (opaque != bitmap) opaque.recycle()
                    bitmap.recycle()
                }
                onProgress((i + 1f) / images.size, "画像 ${i + 1}/${images.size} を追加中…")
            }

            val name = "画像_${images.size}枚.pdf"
            val output = fileRepository.writeFile(outputTree.toDestination(), name, MIME_PDF) { os ->
                document.save(os)
            }
            Log.i(PdfToolsApp.TAG, "Built PDF from ${images.size} image(s) -> ${output.displayName}")
            output
        }
    }

    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        fileRepository.openInput(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return fileRepository.openInput(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxDim || h / 2 >= maxDim) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private fun Bitmap.flattenOntoWhite(): Bitmap {
        if (!hasAlpha()) return this
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@flattenOntoWhite, 0f, 0f, null)
        }
        return result
    }

    companion object {
        private const val POINTS_PER_INCH = 72f
        private const val ASSUMED_DPI = 150f
        private const val MARGIN = 18f // ~0.25 inch on A4
        private const val MAX_DIMENSION = 2400
    }
}
