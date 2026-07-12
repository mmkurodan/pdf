package com.micklab.pdf.domain.ocr

import android.content.Context
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the PaddleOCR mobile models (Paddle-Lite `.nb` det/rec/cls + the
 * character dictionary) into private storage, staged for the pluggable
 * [PaddleOcrEngine]. Inference itself requires integrating the Paddle-Lite
 * runtime (an extension point); the download makes the models locally available.
 */
@Singleton
class PaddleModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "paddleocr")

    val modelDir: File get() = dir

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
                throw IOException("ダウンロード失敗 (HTTP ${connection.responseCode}): ${target.name}")
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
                throw IOException("保存に失敗しました: ${target.name}")
            }
            Log.i(PdfToolsApp.TAG, "Downloaded Paddle model ${target.name} (${target.length()} bytes)")
        } finally {
            connection.disconnect()
        }
    }

    private data class Asset(val fileName: String, val url: String)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val LITE_BASE = "https://paddleocr.bj.bcebos.com/dygraph_v2.0/lite/"
        val ASSETS = listOf(
            Asset("ch_ppocr_mobile_v2.0_det_opt.nb", "${LITE_BASE}ch_ppocr_mobile_v2.0_det_opt.nb"),
            Asset("ch_ppocr_mobile_v2.0_rec_opt.nb", "${LITE_BASE}ch_ppocr_mobile_v2.0_rec_opt.nb"),
            Asset("ch_ppocr_mobile_v2.0_cls_opt.nb", "${LITE_BASE}ch_ppocr_mobile_v2.0_cls_opt.nb"),
            Asset(
                "ppocr_keys_v1.txt",
                "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/ppocr_keys_v1.txt",
            ),
        )
    }
}
