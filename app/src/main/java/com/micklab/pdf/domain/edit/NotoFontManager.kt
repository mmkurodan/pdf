package com.micklab.pdf.domain.edit

import android.content.Context
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the CJK font used to *draw new text* into PDFs.
 *
 * Offline-first: if the font is bundled at `assets/fonts/[FONT_FILE]` it is used
 * directly and no network is ever needed. Otherwise the ~5–10 MB TrueType file
 * is downloaded once (the same sanctioned "model download" exception used by the
 * OCR models) and cached in `filesDir/fonts`; editing then works fully offline.
 * PDFBox subsets the font on embed, so output PDFs stay small.
 *
 * Licensing: Noto Sans JP is SIL OFL 1.1 (bundling and PDF embedding are
 * permitted, including in a paid app, provided the license notice ships with the
 * app).
 *
 * NOTE: the artifact must be a TrueType (glyf) instance for [PDType0Font]. The
 * exact URL needs on-device validation (#0 spike); [load] falls back to a
 * non-subset embed if subsetting the given file fails.
 */
@Singleton
class NotoFontManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "fonts")
    private val downloadedFile: File get() = File(dir, FONT_FILE)

    /** True if the font is present either bundled (assets) or already downloaded. */
    fun isAvailable(): Boolean = isBundled() || (downloadedFile.isFile && downloadedFile.length() > 0)

    fun isBundled(): Boolean =
        runCatching { context.assets.open("$ASSET_DIR/$FONT_FILE").use { true } }.getOrDefault(false)

    /** Blocking; call from a background dispatcher. [onProgress] is 0f..1f. No-op if already available. */
    fun download(onProgress: (Float) -> Unit = {}) {
        if (isAvailable()) { onProgress(1f); return }
        if (!dir.exists()) dir.mkdirs()
        val connection = (URL(FONT_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("フォントのダウンロードに失敗しました (HTTP ${connection.responseCode})")
            }
            val total = connection.contentLengthLong
            val temp = File(dir, "$FONT_FILE.download")
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
            if (downloadedFile.exists()) downloadedFile.delete()
            if (!temp.renameTo(downloadedFile)) {
                temp.delete()
                throw IOException("フォントの保存に失敗しました")
            }
            Log.i(PdfToolsApp.TAG, "Downloaded font ${downloadedFile.name} (${downloadedFile.length()} bytes)")
        } finally {
            connection.disconnect()
        }
    }

    /** Loads (and subsets) the font into [document]. Requires [isAvailable]. */
    fun load(document: PDDocument): PDType0Font {
        val loadWith = { subset: Boolean -> openFontStream().use { PDType0Font.load(document, it, subset) } }
        return runCatching { loadWith(true) }.getOrElse { loadWith(false) }
    }

    private fun openFontStream(): InputStream =
        runCatching { context.assets.open("$ASSET_DIR/$FONT_FILE") }.getOrElse {
            if (downloadedFile.isFile && downloadedFile.length() > 0) downloadedFile.inputStream()
            else throw IOException("フォント未取得です。先にダウンロードしてください。")
        }

    private companion object {
        const val FONT_FILE = "NotoSansJP.ttf"
        const val ASSET_DIR = "fonts"
        // Variable TrueType from Google Fonts; its default instance embeds via
        // PDType0Font. Validate on-device and swap for a static TTF if needed.
        const val FONT_URL =
            "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansjp/NotoSansJP%5Bwght%5D.ttf"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
