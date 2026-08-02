package com.micklab.pdf.domain.edit

import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.data.repository.OutputDestination
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.usecase.MIME_PDF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Creates a blank PDF in cache with configurable page size and background color. */
class CreateBlankPdfUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
        pageCount: Int = 1,
        widthPt: Float = PDRectangle.A4.width,
        heightPt: Float = PDRectangle.A4.height,
        backgroundColorRgb: Int = 0xFFFFFF,
    ): OutputFile = withContext(dispatchers.io) {
        val r = ((backgroundColorRgb shr 16) and 0xFF) / 255f
        val g = ((backgroundColorRgb shr 8) and 0xFF) / 255f
        val b = (backgroundColorRgb and 0xFF) / 255f
        PDDocument().use { document ->
            repeat(pageCount.coerceAtLeast(1)) {
                val mediaBox = PDRectangle(widthPt.coerceAtLeast(10f), heightPt.coerceAtLeast(10f))
                val page = PDPage(mediaBox)
                document.addPage(page)
                // Tag the background with BG_MC_TAG so setBackground() can find and replace it later.
                PDPageContentStream(document, page, AppendMode.APPEND, true).use { cs ->
                    cs.beginMarkedContent(COSName.getPDFName(BG_MC_TAG))
                    cs.setNonStrokingColor(r, g, b)
                    cs.addRect(0f, 0f, mediaBox.width, mediaBox.height)
                    cs.fill()
                    cs.endMarkedContent()
                }
            }
            fileRepository.writeFile(
                OutputDestination.Cache(BLANK_DIR),
                "blank_${System.currentTimeMillis()}.pdf",
                MIME_PDF,
            ) { document.save(it) }
        }
    }

    private companion object {
        const val BLANK_DIR = "edit_preview"
    }
}

// Shared constant so CreateBlankPdfUseCase and PdfContentEditor use the same tag.
internal const val BG_MC_TAG = "MicklabBG"
