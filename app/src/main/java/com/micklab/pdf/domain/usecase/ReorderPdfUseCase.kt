package com.micklab.pdf.domain.usecase

import android.net.Uri
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.NoProgress
import com.micklab.pdf.core.ProgressCallback
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.pdf.PdfWorkspace
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * PDF 並べ替え: writes a new PDF whose pages follow [newOrder] (a list of 0-based
 * source page indices). Pages may be dropped or duplicated; the source is
 * untouched.
 */
class ReorderPdfUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
        source: Uri,
        newOrder: List<Int>,
        outputTree: Uri?,
        onProgress: ProgressCallback = NoProgress,
    ): OutputFile = withContext(dispatchers.io) {
        val temp = workspace.copyUriToTemp(source)
        try {
            workspace.load(temp).use { document ->
                val pageCount = document.numberOfPages
                val order = newOrder.filter { it in 0 until pageCount }
                require(order.isNotEmpty()) { "並べ替え後のページ順が空です" }

                val baseName = fileRepository.displayName(source).substringBeforeLast('.')
                PDDocument().use { out ->
                    order.forEachIndexed { i, pageIndex ->
                        coroutineContext.ensureActive()
                        out.importPage(document.getPage(pageIndex))
                        onProgress((i + 1f) / (order.size + 1), "並べ替え中…")
                    }
                    val output = fileRepository.writeFile(
                        outputTree.toDestination(), "${baseName}_並べ替え.pdf", MIME_PDF,
                    ) { os -> out.save(os) }
                    Log.i(PdfToolsApp.TAG, "Reordered -> ${output.displayName}")
                    output
                }
            }
        } finally {
            workspace.delete(temp)
        }
    }
}
