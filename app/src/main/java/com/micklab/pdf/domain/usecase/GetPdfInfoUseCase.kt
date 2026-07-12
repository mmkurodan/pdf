package com.micklab.pdf.domain.usecase

import android.net.Uri
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.pdf.PdfWorkspace
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Returns the page count of a PDF.
 *
 * Uses pure-Java PDFBox (not the native [android.graphics.pdf.PdfRenderer]) so a
 * malformed PDF surfaces as a catchable exception instead of a native crash when
 * the user merely picks a file.
 */
class GetPdfInfoUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(source: Uri): Int = withContext(dispatchers.io) {
        val temp = workspace.copyUriToTemp(source)
        try {
            workspace.load(temp).use { it.numberOfPages }
        } finally {
            workspace.delete(temp)
        }
    }
}
