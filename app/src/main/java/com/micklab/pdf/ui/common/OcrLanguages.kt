package com.micklab.pdf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.micklab.pdf.R

/** OCR language codes offered as selectable chips, in display order. */
val OCR_LANGUAGE_CODES = listOf("jpn", "eng", "chi_sim", "kor")

/** Localized display name for an OCR language [code] (falls back to the raw code). */
@Composable
fun ocrLanguageLabel(code: String): String = when (code) {
    "jpn" -> stringResource(R.string.lang_name_jpn)
    "eng" -> stringResource(R.string.lang_name_eng)
    "chi_sim" -> stringResource(R.string.lang_name_chi_sim)
    "kor" -> stringResource(R.string.lang_name_kor)
    else -> code
}
