package com.micklab.pdf.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.R
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.NoProgress
import com.micklab.pdf.core.ProgressCallback
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.pdf.PdfWorkspace
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
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
                require(order.isNotEmpty()) { LocaleManager.string(appContext, R.string.uc_reorder_empty_order) }

                val baseName = fileRepository.displayName(source).substringBeforeLast('.')
                PDDocument().use { out ->
                    order.forEachIndexed { i, pageIndex ->
                        coroutineContext.ensureActive()
                        out.importPage(document.getPage(pageIndex))
                        onProgress((i + 1f) / (order.size + 1), LocaleManager.string(appContext, R.string.uc_reorder_progress))
                    }
                    val output = fileRepository.writeFile(
                        outputTree.toDestination(), LocaleManager.string(appContext, R.string.uc_reorder_filename, baseName), MIME_PDF,
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
