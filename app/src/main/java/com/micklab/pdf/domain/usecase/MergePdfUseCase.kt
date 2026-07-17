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
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * PDF 統合（結合）: concatenates [sources] (in order) into a single PDF using
 * PDFBox's [PDFMergerUtility] with scratch-file memory so big inputs are safe.
 */
class MergePdfUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) {
    suspend operator fun invoke(
        sources: List<Uri>,
        outputTree: Uri?,
        onProgress: ProgressCallback = NoProgress,
    ): OutputFile = withContext(dispatchers.io) {
        require(sources.size >= 2) { LocaleManager.string(appContext, R.string.uc_merge_need_two) }

        val temps = mutableListOf<File>()
        val mergedTemp = workspace.tempFile("merged_", ".pdf")
        try {
            val merger = PDFMergerUtility()
            merger.destinationFileName = mergedTemp.absolutePath
            sources.forEachIndexed { i, uri ->
                coroutineContext.ensureActive()
                val temp = workspace.copyUriToTemp(uri)
                temps += temp
                merger.addSource(temp)
                onProgress((i + 1f) / (sources.size + 1), LocaleManager.string(appContext, R.string.uc_merge_loading, i + 1, sources.size))
            }

            onProgress(0.9f, LocaleManager.string(appContext, R.string.uc_merge_merging))
            merger.mergeDocuments(workspace.memorySetting())

            val name = LocaleManager.string(appContext, R.string.uc_merge_filename, sources.size)
            val output = fileRepository.writeFile(outputTree.toDestination(), name, MIME_PDF) { os ->
                mergedTemp.inputStream().use { it.copyTo(os) }
            }
            Log.i(PdfToolsApp.TAG, "Merged ${sources.size} files -> ${output.displayName}")
            output
        } finally {
            workspace.delete(mergedTemp, *temps.toTypedArray())
        }
    }
}
