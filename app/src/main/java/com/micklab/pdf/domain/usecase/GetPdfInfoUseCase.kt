package com.micklab.pdf.domain.usecase

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.pdf.PdfWorkspace
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Returns the page count of a PDF (fast; via the platform renderer). */
class GetPdfInfoUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(source: Uri): Int = withContext(dispatchers.io) {
        val temp = workspace.copyUriToTemp(source)
        val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            renderer.pageCount
        } finally {
            renderer.close()
            pfd.close()
            workspace.delete(temp)
        }
    }
}
