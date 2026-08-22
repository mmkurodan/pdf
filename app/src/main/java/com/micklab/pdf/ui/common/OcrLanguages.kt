package com.micklab.pdf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.micklab.pdf.R
import com.micklab.pdf.domain.edit.AppFont
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.ocr.LlmApiType

/** OCR language codes offered as selectable chips, in display order. Tesseract covers
 *  all of these (each model downloads on demand); the extra scripts beyond the first four
 *  are Tesseract-only. `equ` is Tesseract's math/equation model (best-effort). `jpn_vert`
 *  is vertical Japanese (縦書き): Tesseract uses its `jpn_vert.traineddata`, PaddleOCR
 *  runs the Japanese recognizer in a vertical (column) reading mode. */
val OCR_LANGUAGE_CODES =
    listOf("jpn", "jpn_vert", "eng", "chi_sim", "chi_tra", "kor", "ell", "rus", "ara", "heb", "equ")

/** Subset PaddleOCR has ONNX models for; its settings section only offers these. */
val PADDLE_LANGUAGE_CODES = listOf("jpn", "jpn_vert", "eng", "chi_sim", "chi_tra", "kor", "rus", "ara")

/** Localized display name for an OCR language [code] (falls back to the raw code). */
@Composable
fun ocrLanguageLabel(code: String): String = when (code) {
    "jpn" -> stringResource(R.string.lang_name_jpn)
    "jpn_vert" -> stringResource(R.string.lang_name_jpn_vert)
    "eng" -> stringResource(R.string.lang_name_eng)
    "chi_sim" -> stringResource(R.string.lang_name_chi_sim)
    "chi_tra" -> stringResource(R.string.lang_name_chi_tra)
    "kor" -> stringResource(R.string.lang_name_kor)
    "ell" -> stringResource(R.string.lang_name_ell)
    "rus" -> stringResource(R.string.lang_name_rus)
    "ara" -> stringResource(R.string.lang_name_ara)
    "heb" -> stringResource(R.string.lang_name_heb)
    "equ" -> stringResource(R.string.lang_name_equ)
    else -> code
}

/** Localized display names for the OCR engines (for `ChoiceChipsRow.optionLabel`). */
@Composable
fun ocrEngineLabels(): Map<OcrEngineType, String> = mapOf(
    OcrEngineType.TESSERACT to stringResource(R.string.eng_tesseract),
    OcrEngineType.PADDLE_OCR to stringResource(R.string.eng_paddle),
    OcrEngineType.LLM_VISION to stringResource(R.string.eng_llm_vision),
)

/** Localized display names for the LLM API types. */
@Composable
fun llmApiTypeLabels(): Map<LlmApiType, String> = mapOf(
    LlmApiType.OLLAMA to stringResource(R.string.api_ollama),
    LlmApiType.OPENAI to stringResource(R.string.api_openai),
)

/**
 * Label for a [font] in the picker. Noto families read by the script they cover (their
 * "Noto Sans XX" names don't tell users what characters they support); the non-Noto
 * Japanese display faces keep their own names.
 */
@Composable
fun fontLabel(font: AppFont): String = when (font) {
    AppFont.NOTO_SANS_JP -> stringResource(R.string.lang_name_jpn)
    AppFont.NOTO_SERIF_JP -> stringResource(R.string.font_ja_serif)
    AppFont.NOTO_SANS -> stringResource(R.string.font_latin_greek_cyrillic)
    AppFont.NOTO_SANS_SC -> stringResource(R.string.lang_name_chi_sim)
    AppFont.NOTO_SANS_TC -> stringResource(R.string.lang_name_chi_tra)
    AppFont.NOTO_SANS_KR -> stringResource(R.string.lang_name_kor)
    AppFont.NOTO_SANS_ARABIC -> stringResource(R.string.lang_name_ara)
    AppFont.NOTO_SANS_HEBREW -> stringResource(R.string.lang_name_heb)
    AppFont.NOTO_SANS_MATH -> stringResource(R.string.lang_name_equ)
    AppFont.NOTO_SANS_SYMBOLS2 -> stringResource(R.string.font_symbols)
    else -> font.displayName // M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One
}
