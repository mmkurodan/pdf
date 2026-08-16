package com.micklab.pdf.domain.edit

import android.content.Context
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.R
import com.micklab.pdf.core.LocaleManager
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
 * A font the app can draw *new text* with. The set spans many scripts so users can
 * type Japanese, Chinese (Simplified/Traditional), Korean, Latin/Greek/Cyrillic,
 * Arabic, Hebrew, and mathematical/misc symbols — pick the font that matches the
 * script. All entries are SIL OFL 1.1, which permits bundling and PDF embedding
 * (including in a paid app) as long as the license notice ships with the app.
 *
 * Note (RTL): Arabic/Hebrew are shaped and bidi-reordered at draw time by [RtlText],
 * so they join and read right-to-left correctly.
 *
 * The artifact must be a TrueType (glyf) instance for [PDType0Font]; the variable
 * fonts here (Noto *) embed via their default instance, the rest are static.
 * [url] points at the raw file in the google/fonts OFL tree.
 */
enum class AppFont(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
) {
    NOTO_SANS_JP(
        "noto_sans_jp", "Noto Sans JP", "NotoSansJP.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansjp/NotoSansJP%5Bwght%5D.ttf",
    ),
    NOTO_SERIF_JP(
        "noto_serif_jp", "Noto Serif JP", "NotoSerifJP.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notoserifjp/NotoSerifJP%5Bwght%5D.ttf",
    ),
    MPLUS_ROUNDED_1C(
        "mplus_rounded_1c", "M PLUS Rounded 1c", "MPLUSRounded1c-Regular.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/mplusrounded1c/MPLUSRounded1c-Regular.ttf",
    ),
    ZEN_KAKU_GOTHIC_NEW(
        "zen_kaku_gothic_new", "Zen Kaku Gothic New", "ZenKakuGothicNew-Regular.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/zenkakugothicnew/ZenKakuGothicNew-Regular.ttf",
    ),
    KLEE_ONE(
        "klee_one", "Klee One", "KleeOne-Regular.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/kleeone/KleeOne-Regular.ttf",
    ),

    // ── Broader script coverage (curated Noto family, all SIL OFL) ──────────────
    /** Latin (Basic + Extended), Greek, and Cyrillic in one family. */
    NOTO_SANS(
        "noto_sans", "Noto Sans", "NotoSans.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans%5Bwdth,wght%5D.ttf",
    ),
    NOTO_SANS_SC(
        "noto_sans_sc", "Noto Sans SC", "NotoSansSC.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf",
    ),
    NOTO_SANS_TC(
        "noto_sans_tc", "Noto Sans TC", "NotoSansTC.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanstc/NotoSansTC%5Bwght%5D.ttf",
    ),
    NOTO_SANS_KR(
        "noto_sans_kr", "Noto Sans KR", "NotoSansKR.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanskr/NotoSansKR%5Bwght%5D.ttf",
    ),
    /** Arabic (RTL): shaped + bidi-reordered at draw time; carries the presentation forms. */
    NOTO_SANS_ARABIC(
        "noto_sans_arabic", "Noto Sans Arabic", "NotoSansArabic.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansarabic/NotoSansArabic%5Bwdth,wght%5D.ttf",
    ),
    /** Hebrew (RTL): bidi-reordered at draw time (no shaping needed). */
    NOTO_SANS_HEBREW(
        "noto_sans_hebrew", "Noto Sans Hebrew", "NotoSansHebrew.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanshebrew/NotoSansHebrew%5Bwdth,wght%5D.ttf",
    ),
    /** Mathematical operators and symbols (Σ √ ∞ ∫ ± ≠ ≤ ≥ …). */
    NOTO_SANS_MATH(
        "noto_sans_math", "Noto Sans Math", "NotoSansMath.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosansmath/NotoSansMath-Regular.ttf",
    ),
    /** Miscellaneous symbols (arrows, geometric shapes, dingbats, …). */
    NOTO_SANS_SYMBOLS2(
        "noto_sans_symbols2", "Noto Sans Symbols 2", "NotoSansSymbols2.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssymbols2/NotoSansSymbols2-Regular.ttf",
    );

    companion object {
        /** The one used when nothing is chosen (and for regenerating edited text). */
        val DEFAULT = NOTO_SANS_JP

        fun byId(id: String): AppFont = entries.firstOrNull { it.id == id } ?: DEFAULT

        /**
         * Best-effort match of an embedded PDF font's BaseFont name (with or without the
         * 6-char subset prefix) back to one of our [AppFont]s, so text we drew keeps its
         * font when it is later moved or restyled. Returns null for foreign fonts we can't
         * reproduce. Matching normalises to letters/digits and looks for the family key,
         * e.g. "ABCDEF+NotoSerifJP-Regular" → NOTO_SERIF_JP.
         */
        fun byEmbeddedName(rawName: String?): AppFont? {
            val norm = rawName?.lowercase()?.filter { it.isLetterOrDigit() }.orEmpty()
            if (norm.isEmpty()) return null
            // Longest key wins so more-specific families take precedence — e.g. a
            // "NotoSansJP" name must not be captured by "Noto Sans" (key "notosans"),
            // which is a prefix of "notosansjp".
            return entries
                .mapNotNull { font ->
                    val key = font.displayName.lowercase().filter { it.isLetterOrDigit() }
                    if (key.isNotEmpty() && norm.contains(key)) font to key.length else null
                }
                .maxByOrNull { it.second }
                ?.first
        }
    }
}

/**
 * Owns the fonts used to *draw new text* into PDFs. Offline-first: a font bundled
 * under `assets/fonts/` is used directly; otherwise its TrueType file is downloaded
 * once (the sanctioned "model download" exception) into `filesDir/fonts` and reused
 * offline. PDFBox subsets on embed, so output PDFs stay small.
 *
 * Multiple fonts are supported so the editor can offer a per-text choice; see
 * [AppFont]. Replaces the former single-font NotoFontManager.
 */
@Singleton
class FontManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "fonts")

    fun all(): List<AppFont> = AppFont.entries

    private fun fileOf(font: AppFont): File = File(dir, font.fileName)

    fun isBundled(font: AppFont): Boolean =
        runCatching { context.assets.open("$ASSET_DIR/${font.fileName}").use { true } }.getOrDefault(false)

    fun isAvailable(font: AppFont): Boolean =
        isBundled(font) || fileOf(font).let { it.isFile && it.length() > 0 }

    /** Ids of every font currently usable offline (bundled or downloaded). */
    fun availableIds(): Set<String> =
        AppFont.entries.filter { isAvailable(it) }.map { it.id }.toSet()

    /** Blocking; call from a background dispatcher. [onProgress] is 0f..1f. No-op if already available. */
    fun download(font: AppFont, onProgress: (Float) -> Unit = {}) {
        if (isAvailable(font)) { onProgress(1f); return }
        if (!dir.exists()) dir.mkdirs()
        val connection = (URL(font.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException(LocaleManager.string(context, R.string.nfm_download_failed, connection.responseCode))
            }
            val total = connection.contentLengthLong
            val target = fileOf(font)
            val temp = File(dir, "${font.fileName}.download")
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
                throw IOException(LocaleManager.string(context, R.string.nfm_save_failed))
            }
            Log.i(PdfToolsApp.TAG, "Downloaded font ${target.name} (${target.length()} bytes)")
        } finally {
            connection.disconnect()
        }
    }

    /** Loads (and subsets) the font identified by [fontId] into [document]. Requires availability. */
    fun load(document: PDDocument, fontId: String): PDType0Font {
        val font = AppFont.byId(fontId)
        val loadWith = { subset: Boolean -> openStream(font).use { PDType0Font.load(document, it, subset) } }
        return runCatching { loadWith(true) }.getOrElse { loadWith(false) }
    }

    private fun openStream(font: AppFont): InputStream =
        runCatching { context.assets.open("$ASSET_DIR/${font.fileName}") }.getOrElse {
            val f = fileOf(font)
            if (f.isFile && f.length() > 0) f.inputStream()
            else throw IOException(LocaleManager.string(context, R.string.nfm_not_downloaded))
        }

    private companion object {
        const val ASSET_DIR = "fonts"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
