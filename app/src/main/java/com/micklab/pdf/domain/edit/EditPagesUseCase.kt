package com.micklab.pdf.domain.edit

import android.net.Uri
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.data.repository.OutputDestination
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.pdf.PdfWorkspace
import com.micklab.pdf.domain.usecase.MIME_PDF
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Structural page edits on the working PDF: insert a blank page or delete one. Each call
 * writes a new PDF into the edit-preview cache (the input is never mutated) and returns it,
 * so it slots into the editor's working-source model exactly like a bake/[ApplyEditsUseCase].
 */
class EditPagesUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
) {
    /**
     * Inserts a blank page so it ends up at [index] (0-based; `>= pageCount` appends). The new
     * page copies the size of the page it displaces and gets a [backgroundColorRgb] fill tagged
     * for [PdfContentEditor.setBackground] to replace later.
     */
    suspend fun insertBlankPage(source: Uri, index: Int, backgroundColorRgb: Int): OutputFile =
        edit(source) { document ->
            val count = document.numberOfPages
            val at = index.coerceIn(0, count)
            val ref = document.getPage(at.coerceAtMost(count - 1))
            val box = PDRectangle(ref.mediaBox.width, ref.mediaBox.height)
            val page = PDPage(box)
            fillBackground(document, page, box, backgroundColorRgb)
            if (at >= count) document.addPage(page) else document.pages.insertBefore(page, document.getPage(at))
        }

    /** Removes page [index] (0-based). No-op if the document has a single page. */
    suspend fun deletePage(source: Uri, index: Int): OutputFile =
        edit(source) { document ->
            if (document.numberOfPages > 1) document.removePage(index.coerceIn(0, document.numberOfPages - 1))
        }

    private suspend fun edit(source: Uri, block: (PDDocument) -> Unit): OutputFile = withContext(dispatchers.io) {
        val temp = workspace.copyUriToTemp(source)
        try {
            workspace.load(temp).use { document ->
                block(document)
                fileRepository.writeFile(
                    OutputDestination.Cache(EDIT_DIR),
                    "pages_${System.currentTimeMillis()}.pdf",
                    MIME_PDF,
                ) { document.save(it) }
            }
        } finally {
            workspace.delete(temp)
        }
    }

    private fun fillBackground(document: PDDocument, page: PDPage, box: PDRectangle, rgb: Int) {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        PDPageContentStream(document, page, AppendMode.APPEND, true).use { cs ->
            cs.beginMarkedContent(COSName.getPDFName(BG_MC_TAG))
            cs.setNonStrokingColor(r, g, b)
            cs.addRect(0f, 0f, box.width, box.height)
            cs.fill()
            cs.endMarkedContent()
        }
    }

    private companion object {
        const val EDIT_DIR = "edit_preview"
    }
}
