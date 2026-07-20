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

private const val HF_BASE = "https://huggingface.co/SWHL/RapidOCR/resolve/main/"
private const val PADDLE_DICT_BASE = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/"
private const val PADDLE_UTILS_BASE = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/"

/**
 * A per-language recognition model + its character dictionary. Detection is shared
 * across profiles (one multilingual DB detector); only recognition is language-specific.
 *
 *  - [JAPAN] PP-OCRv1 CRNN (input height 32). `japan_dict.txt` already contains Latin,
 *    so it also handles English embedded in Japanese documents.
 *  - [LATIN] PP-OCRv4 rec (input height 48) with `ppocr_keys_v1.txt` (full ASCII). Far
 *    more accurate on English than the Japanese model, which is trained mostly on
 *    Japanese and only recognises Latin as a minor side effect.
 */
enum class PaddleRecProfile(
    val recFileName: String,
    val recUrl: String,
    val dictFileName: String,
    val dictUrl: String,
    val recHeight: Int,
) {
    JAPAN(
        recFileName = "japan_rec_crnn.onnx",
        recUrl = "${HF_BASE}PP-OCRv1/japan_rec_crnn.onnx",
        dictFileName = "japan_dict.txt",
        dictUrl = "${PADDLE_DICT_BASE}japan_dict.txt",
        recHeight = 32,
    ),
    LATIN(
        recFileName = "ch_PP-OCRv4_rec_infer.onnx",
        recUrl = "${HF_BASE}PP-OCRv4/ch_PP-OCRv4_rec_infer.onnx",
        dictFileName = "ppocr_keys_v1.txt",
        dictUrl = "${PADDLE_UTILS_BASE}ppocr_keys_v1.txt",
        recHeight = 48,
    ),
}

/**
 * Downloads the PP-OCR **ONNX** models used by the on-device [PaddleOcrEngine]
 * (via ONNX Runtime): a shared text detector plus one recognition model + dictionary
 * per [PaddleRecProfile]. One-time download; recognition then runs fully offline.
 */
@Singleton
class PaddleModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "paddleocr")

    val detModelPath: File get() = File(dir, DET_FILE)
    fun recModelPath(profile: PaddleRecProfile): File = File(dir, profile.recFileName)
    fun dictPath(profile: PaddleRecProfile): File = File(dir, profile.dictFileName)

    /**
     * Recognition profile for the given OCR [languages]. Any Japanese selection uses the
     * Japanese model (its dict covers embedded Latin); a purely English selection uses the
     * dedicated Latin model. Anything else falls back to Japanese.
     */
    fun profileFor(languages: List<String>): PaddleRecProfile = when {
        languages.any { it.equals("jpn", ignoreCase = true) } -> PaddleRecProfile.JAPAN
        languages.isNotEmpty() && languages.all { it.equals("eng", ignoreCase = true) } -> PaddleRecProfile.LATIN
        else -> PaddleRecProfile.JAPAN
    }

    /** True when the detector + the given profile's recognition model and dict are present. */
    fun isDownloaded(profile: PaddleRecProfile): Boolean = assetsFor(profile).all { it.present() }

    /** True when every profile is fully downloaded (used by the settings status tile). */
    fun isDownloaded(): Boolean = allAssets().all { it.present() }

    fun downloadedFiles(): List<String> = allAssets().filter { it.present() }.map { it.fileName }

    /** Blocking; call from a background dispatcher. [onProgress] is (file, 0f..1f). */
    fun downloadAll(onProgress: (fileName: String, fraction: Float) -> Unit) {
        if (!dir.exists()) dir.mkdirs()
        val assets = allAssets()
        assets.forEachIndexed { index, asset ->
            val target = File(dir, asset.fileName)
            if (target.exists() && target.length() > 0) {
                onProgress(asset.fileName, (index + 1f) / assets.size)
                return@forEachIndexed
            }
            downloadFile(asset.url, target) { fileFraction ->
                onProgress(asset.fileName, (index + fileFraction) / assets.size)
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

    /** Detector + the profile's recognition model + dict. */
    private fun assetsFor(profile: PaddleRecProfile): List<Asset> = listOf(
        detAsset,
        Asset(profile.recFileName, profile.recUrl),
        Asset(profile.dictFileName, profile.dictUrl),
    )

    /** Every unique file across the detector and all profiles (detector first, deduped). */
    private fun allAssets(): List<Asset> {
        val byName = LinkedHashMap<String, Asset>()
        byName[detAsset.fileName] = detAsset
        for (profile in PaddleRecProfile.entries) {
            byName[profile.recFileName] = Asset(profile.recFileName, profile.recUrl)
            byName[profile.dictFileName] = Asset(profile.dictFileName, profile.dictUrl)
        }
        return byName.values.toList()
    }

    private fun Asset.present(): Boolean = File(dir, fileName).let { it.isFile && it.length() > 0 }

    private data class Asset(val fileName: String, val url: String)

    private val detAsset = Asset(DET_FILE, "${HF_BASE}PP-OCRv4/$DET_FILE")

    private companion object {
        const val DET_FILE = "ch_PP-OCRv4_det_infer.onnx"

        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
