package com.micklab.pdf.domain.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.micklab.pdf.PdfToolsApp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Official Tesseract model repositories the app can download from. */
enum class OcrModelVariant(val displayName: String, val baseUrl: String) {
    /** Smaller, mobile-friendly (recommended). */
    FAST("高速版 (fast)", "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/"),

    /** Most accurate, largest. */
    BEST("高精度版 (best)", "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/"),
}

/**
 * Owns the on-device Tesseract data directory and keeps it fully offline.
 *
 * Tesseract expects a *base* directory that contains a `tessdata/` subfolder,
 * i.e. `<base>/tessdata/jpn.traineddata`. [tessBasePath] returns that base.
 *
 * Two ways to populate it, both offline (no network / no external API):
 *  1. Bundle `*.traineddata` in `assets/tessdata/` — copied out on first use.
 *  2. Let the user pick a folder of `*.traineddata` via SAF ([importFromTree]).
 */
@Singleton
class OcrModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseDir: File = File(context.filesDir, "tesseract")
    private val dataDir: File = File(baseDir, "tessdata")

    /** Path to pass to `TessBaseAPI.init(path, lang)`. */
    fun tessBasePath(): String = baseDir.absolutePath

    /** Languages currently installed on device (e.g. {"jpn", "eng"}). */
    fun availableLanguages(): Set<String> {
        ensureAssetsCopied()
        val files = dataDir.listFiles() ?: return emptySet()
        return files
            .filter { it.isFile && it.name.endsWith(TRAINEDDATA_SUFFIX) }
            .map { it.name.removeSuffix(TRAINEDDATA_SUFFIX) }
            .toSet()
    }

    fun hasAllLanguages(languages: List<String>): Boolean {
        val available = availableLanguages()
        return languages.isNotEmpty() && languages.all { it in available }
    }

    fun isInstalled(language: String): Boolean =
        File(dataDir, "$language$TRAINEDDATA_SUFFIX").let { it.isFile && it.length() > 0 }

    /**
     * Downloads one language model from the chosen [variant] into private storage.
     * Blocking IO — call from a background dispatcher. Writes atomically (temp +
     * rename) so a partial download can't leave a corrupt file. [onProgress] is
     * 0f..1f (only meaningful when the server reports content length).
     */
    fun downloadLanguage(
        language: String,
        variant: OcrModelVariant = OcrModelVariant.FAST,
        onProgress: (Float) -> Unit = {},
    ) {
        if (!dataDir.exists()) dataDir.mkdirs()
        val url = URL("${variant.baseUrl}$language$TRAINEDDATA_SUFFIX")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("ダウンロードに失敗しました (HTTP ${connection.responseCode}): $language")
            }
            val total = connection.contentLengthLong
            val tempFile = File(dataDir, "$language$TRAINEDDATA_SUFFIX.download")
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
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
            val target = File(dataDir, "$language$TRAINEDDATA_SUFFIX")
            if (target.exists()) target.delete()
            if (!tempFile.renameTo(target)) {
                tempFile.delete()
                throw IOException("保存に失敗しました: $language")
            }
            Log.i(PdfToolsApp.TAG, "Downloaded $language model (${target.length()} bytes)")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Copies any `*.traineddata` bundled under `assets/tessdata/` into the app's
     * private storage. No-op for files already present. Safe to call repeatedly.
     */
    fun ensureAssetsCopied() {
        if (!dataDir.exists()) dataDir.mkdirs()
        val assetNames = runCatching { context.assets.list(ASSET_DIR) }.getOrNull().orEmpty()
        for (name in assetNames) {
            if (!name.endsWith(TRAINEDDATA_SUFFIX)) continue
            val target = File(dataDir, name)
            if (target.exists() && target.length() > 0) continue
            runCatching {
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                Log.i(PdfToolsApp.TAG, "Copied bundled traineddata: $name")
            }.onFailure { Log.w(PdfToolsApp.TAG, "Failed to copy asset $name", it) }
        }
    }

    /**
     * Imports `*.traineddata` from a user-selected SAF tree into private storage.
     * @return number of files imported.
     */
    fun importFromTree(treeUri: Uri): Int {
        if (!dataDir.exists()) dataDir.mkdirs()
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var imported = 0
        for (child in tree.listFiles()) {
            val name = child.name ?: continue
            if (!child.isFile || !name.endsWith(TRAINEDDATA_SUFFIX)) continue
            runCatching {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    File(dataDir, name).outputStream().use { output -> input.copyTo(output) }
                }
                imported++
                Log.i(PdfToolsApp.TAG, "Imported traineddata: $name")
            }.onFailure { Log.w(PdfToolsApp.TAG, "Failed to import $name", it) }
        }
        return imported
    }

    companion object {
        private const val ASSET_DIR = "tessdata"
        private const val TRAINEDDATA_SUFFIX = ".traineddata"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
