package com.micklab.pdf.domain.edit

import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.data.repository.OutputDestination
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.usecase.MIME_PDF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Creates a blank white A4 PDF in the cache so the editor can start a new document. */
class CreateBlankPdfUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(pageCount: Int = 1): OutputFile = withContext(dispatchers.io) {
        PDDocument().use { document ->
            repeat(pageCount.coerceAtLeast(1)) {
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { cs ->
                    cs.setNonStrokingColor(1f, 1f, 1f)
                    cs.addRect(0f, 0f, page.mediaBox.width, page.mediaBox.height)
                    cs.fill()
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
        // Reuse the (FileProvider-configured) preview cache dir.
        const val BLANK_DIR = "edit_preview"
    }
}
