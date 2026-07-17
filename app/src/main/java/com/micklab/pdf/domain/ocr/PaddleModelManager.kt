package com.micklab.pdf.domain.ocr

import android.content.Context
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.R
import com.micklab.pdf.core.LocaleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the PP-OCR **ONNX** models used by the on-device [PaddleOcrEngine]
 * (via ONNX Runtime): text detection, Japanese recognition, and the character
 * dictionary. One-time download; recognition then runs fully offline.
 */
@Singleton
class PaddleModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "paddleocr")

    val detModelPath: File get() = File(dir, DET_FILE)
    val recModelPath: File get() = File(dir, REC_FILE)
    val dictPath: File get() = File(dir, DICT_FILE)

    fun isDownloaded(): Boolean = ASSETS.all { File(dir, it.fileName).let { f -> f.isFile && f.length() > 0 } }

    fun downloadedFiles(): List<String> =
        ASSETS.map { it.fileName }.filter { File(dir, it).let { f -> f.isFile && f.length() > 0 } }

    /** Blocking; call from a background dispatcher. [onProgress] is (file, 0f..1f). */
    fun downloadAll(onProgress: (fileName: String, fraction: Float) -> Unit) {
        if (!dir.exists()) dir.mkdirs()
        ASSETS.forEachIndexed { index, asset ->
            val target = File(dir, asset.fileName)
            if (target.exists() && target.length() > 0) {
                onProgress(asset.fileName, (index + 1f) / ASSETS.size)
                return@forEachIndexed
            }
            downloadFile(asset.url, target) { fileFraction ->
                onProgress(asset.fileName, (index + fileFraction) / ASSETS.size)
            }
        }
    }

    private fun downloadFile(urlString: String, target: File, onProgress: (Float) -> Unit) {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException(LocaleManager.string(context, R.string.pmm_download_failed, connection.responseCode, target.name))
            }
            val total = connection.contentLengthLong
            val temp = File(dir, "${target.name}.download")
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException(LocaleManager.string(context, R.string.pmm_save_failed, target.name))
            }
            Log.i(PdfToolsApp.TAG, "Downloaded Paddle model ${target.name} (${target.length()} bytes)")
        } finally {
            connection.disconnect()
        }
    }

    private data class Asset(val fileName: String, val url: String)

    private companion object {
        const val DET_FILE = "ch_PP-OCRv4_det_infer.onnx"
        const val REC_FILE = "japan_rec_crnn.onnx"
        const val DICT_FILE = "japan_dict.txt"

        const val HF_BASE = "https://huggingface.co/SWHL/RapidOCR/resolve/main/"
        const val PADDLE_RAW = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/"

        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000

        val ASSETS = listOf(
            Asset(DET_FILE, "${HF_BASE}PP-OCRv4/$DET_FILE"),
            Asset(REC_FILE, "${HF_BASE}PP-OCRv1/$REC_FILE"),
            Asset(DICT_FILE, "$PADDLE_RAW$DICT_FILE"),
        )
    }
}
