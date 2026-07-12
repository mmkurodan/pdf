package com.micklab.pdf.domain.pdf

import android.content.Context
import android.net.Uri
import com.micklab.pdf.data.repository.FileRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scratch space + PDFBox loading helpers.
 *
 * Large documents are never fully buffered in memory: inputs are streamed to a
 * temp file and PDFBox is pointed at it with a mixed heap/scratch-file
 * [MemoryUsageSetting] (大容量 PDF はストリーム方式で処理).
 */
@Singleton
class PdfWorkspace @Inject constructor(
    @ApplicationContext context: Context,
    private val fileRepository: FileRepository,
) {
    private val dir = File(context.cacheDir, "pdfwork").apply { mkdirs() }

    fun tempFile(prefix: String, suffix: String): File = File.createTempFile(prefix, suffix, dir)

    /** Streams a content Uri to a private temp file (enables random access). */
    fun copyUriToTemp(uri: Uri, suffix: String = ".pdf"): File {
        val target = tempFile("src_", suffix)
        fileRepository.openInput(uri).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    fun memorySetting(): MemoryUsageSetting = MemoryUsageSetting.setupMixed(MAX_HEAP_BYTES)

    fun load(file: File): PDDocument = PDDocument.load(file, memorySetting())

    fun delete(vararg files: File) {
        files.forEach { runCatching { it.delete() } }
    }

    companion object {
        // Up to 32 MB on the heap, then spill to the scratch file.
        private const val MAX_HEAP_BYTES = 32L * 1024 * 1024
    }
}
