package com.micklab.pdf.domain.usecase

import android.net.Uri
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.NoProgress
import com.micklab.pdf.core.ProgressCallback
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.data.repository.OutputDestination
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.model.SplitMode
import com.micklab.pdf.domain.pdf.PdfWorkspace
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * PDF 分解（ページ抽出）: pulls [pages] out of [source] into either one combined
 * PDF or one PDF per page. Pages are imported (deep-copied), so the source is
 * never modified.
 */
class SplitPdfUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
        source: Uri,
        pages: List<Int>,
        mode: SplitMode,
        outputTree: Uri?,
        onProgress: ProgressCallback = NoProgress,
    ): List<OutputFile> = withContext(dispatchers.io) {
        val temp = workspace.copyUriToTemp(source)
        val outputs = mutableListOf<OutputFile>()
        try {
            workspace.load(temp).use { document ->
                val valid = pages.filter { it in 0 until document.numberOfPages }
                require(valid.isNotEmpty()) { "抽出するページがありません" }
                val baseName = fileRepository.displayName(source).substringBeforeLast('.')
                val destination = outputTree.toDestination()

                when (mode) {
                    SplitMode.SELECTED_INTO_ONE -> {
                        PDDocument().use { out ->
                            valid.forEachIndexed { i, pageIndex ->
                                coroutineContext.ensureActive()
                                out.importPage(document.getPage(pageIndex))
                                onProgress((i + 1f) / (valid.size + 1), "ページ抽出中…")
                            }
                            outputs += fileRepository.writeFile(
                                destination, "${baseName}_抽出.pdf", MIME_PDF,
                            ) { os -> out.save(os) }
                        }
                    }

                    SplitMode.EACH_PAGE_SEPARATE -> {
                        valid.forEachIndexed { i, pageIndex ->
                            coroutineContext.ensureActive()
                            PDDocument().use { out ->
                                out.importPage(document.getPage(pageIndex))
                                outputs += fileRepository.writeFile(
                                    destination, "${baseName}_p${pageIndex + 1}.pdf", MIME_PDF,
                                ) { os -> out.save(os) }
                            }
                            onProgress((i + 1f) / valid.size, "ページ ${pageIndex + 1} を書き出し中…")
                        }
                    }
                }
            }
            Log.i(PdfToolsApp.TAG, "Split produced ${outputs.size} file(s)")
            outputs
        } finally {
            workspace.delete(temp)
        }
    }
}
