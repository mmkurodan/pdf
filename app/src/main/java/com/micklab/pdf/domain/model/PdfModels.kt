package com.micklab.pdf.domain.model

import android.net.Uri

/** Raster output format for the "PDF → 画像化" feature. */
enum class ImageFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
}

/** How "PDF 分解（ページ抽出）" writes its result. */
enum class SplitMode {
    /** All selected pages collected into one new PDF. */
    SELECTED_INTO_ONE,

    /** One output PDF per selected page. */
    EACH_PAGE_SEPARATE,
}

/** Lightweight page metadata (points; 1pt = 1/72 inch). */
data class PdfPageInfo(
    val index: Int,
    val widthPt: Float,
    val heightPt: Float,
)

/** A file produced by a use case, ready to be shown / shared. */
data class OutputFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
)
