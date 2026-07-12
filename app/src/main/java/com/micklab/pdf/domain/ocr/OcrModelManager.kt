package com.micklab.pdf.domain.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.micklab.pdf.PdfToolsApp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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
    }
}
