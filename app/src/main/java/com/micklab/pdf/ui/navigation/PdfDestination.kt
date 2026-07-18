package com.micklab.pdf.ui.navigation

import androidx.annotation.StringRes
import com.micklab.pdf.R

/** All navigable destinations. Titles are string resources so they localize. */
enum class PdfDestination(val route: String, @StringRes val titleRes: Int) {
    HOME("home", R.string.home_title),
    SPLIT("split", R.string.tool_split),
    MERGE("merge", R.string.tool_merge),
    REORDER("reorder", R.string.tool_reorder),
    PDF_TO_IMAGE("pdf_to_image", R.string.tool_pdf_to_image),
    IMAGE_TO_PDF("image_to_pdf", R.string.tool_image_to_pdf),
    EDIT("edit", R.string.tool_edit),
    OCR("ocr", R.string.tool_ocr),
    SUMMARY("summary", R.string.tool_summary),
    PROMPT("prompt", R.string.tool_prompt),
    OCR_SETTINGS("ocr_settings", R.string.tool_ocr_settings),
    MANUAL("manual", R.string.tool_manual),
    PRIVACY("privacy", R.string.tool_privacy),
    LICENSES("licenses", R.string.tool_licenses),
    LANGUAGE("language", R.string.tool_language),
}
