package com.micklab.pdf.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.data.repository.OutputDestination
import com.micklab.pdf.domain.model.DocumentTextResult
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.usecase.ExtractDocumentTextUseCase
import com.micklab.pdf.domain.usecase.TextExtractionMode
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Background OCR / text extraction for large documents.
 *
 * The heavy per-page work stays coroutine-based inside the use case; wrapping it
 * in a [CoroutineWorker] means it survives the screen going away and process
 * pressure (Coroutine + Worker 併用). The JSON result is written to a file and
 * its Uri is returned via output data (WorkManager's Data has a ~10 KB cap, so
 * the payload itself is never put in Data).
 */
@HiltWorker
class PdfProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val extractDocumentText: ExtractDocumentTextUseCase,
    private val fileRepository: FileRepository,
    private val json: Json,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val sourceStr = inputData.getString(KEY_SOURCE_URI)
            ?: return@coroutineScope Result.failure(errorData("入力ファイルがありません"))
        val engine = runCatching {
            OcrEngineType.valueOf(inputData.getString(KEY_ENGINE) ?: OcrEngineType.TESSERACT.name)
        }.getOrDefault(OcrEngineType.TESSERACT)
        val languages = (inputData.getString(KEY_LANGUAGES) ?: "jpn+eng")
            .split('+').map { it.trim() }.filter { it.isNotEmpty() }
        val mode = runCatching {
            TextExtractionMode.valueOf(inputData.getString(KEY_MODE) ?: TextExtractionMode.AUTO.name)
        }.getOrDefault(TextExtractionMode.AUTO)
        val dpi = inputData.getInt(KEY_DPI, 200)

        // Progress is sampled by a monitor coroutine; a benign data race on these
        // fields is fine for a progress bar/label.
        var latestFraction = 0f
        var latestLabel = ""
        val monitor = launch {
            while (isActive) {
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to (latestFraction * 100).toInt(),
                        KEY_PROGRESS_LABEL to latestLabel,
                    ),
                )
                delay(PROGRESS_INTERVAL_MS)
            }
        }

        try {
            val result: DocumentTextResult = extractDocumentText(
                source = Uri.parse(sourceStr),
                engineType = engine,
                languages = languages,
                mode = mode,
                renderDpi = dpi,
            ) { fraction, label -> latestFraction = fraction; latestLabel = label }

            val jsonText = json.encodeToString(result)
            val output = fileRepository.writeFile(
                OutputDestination.Cache("outputs"),
                "ocr_${System.currentTimeMillis()}.json",
                "application/json",
            ) { os -> os.write(jsonText.toByteArray(Charsets.UTF_8)) }

            Log.i(PdfToolsApp.TAG, "OCR worker done: ${result.pageCount} page(s)")
            Result.success(
                workDataOf(
                    KEY_RESULT_URI to output.uri.toString(),
                    KEY_PAGE_COUNT to result.pageCount,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(PdfToolsApp.TAG, "OCR worker failed", e)
            Result.failure(errorData(e.message ?: "処理に失敗しました"))
        } finally {
            monitor.cancel()
        }
    }

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

    companion object {
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_ENGINE = "engine"
        const val KEY_LANGUAGES = "languages"
        const val KEY_MODE = "mode"
        const val KEY_DPI = "dpi"

        const val KEY_PROGRESS = "progress"
        const val KEY_PROGRESS_LABEL = "progress_label"
        const val KEY_RESULT_URI = "result_uri"
        const val KEY_PAGE_COUNT = "page_count"
        const val KEY_ERROR = "error"

        private const val PROGRESS_INTERVAL_MS = 300L

        fun buildInputData(
            source: Uri,
            engine: OcrEngineType,
            languages: List<String>,
            mode: TextExtractionMode,
            dpi: Int,
        ): Data = workDataOf(
            KEY_SOURCE_URI to source.toString(),
            KEY_ENGINE to engine.name,
            KEY_LANGUAGES to languages.joinToString("+"),
            KEY_MODE to mode.name,
            KEY_DPI to dpi,
        )
    }
}
