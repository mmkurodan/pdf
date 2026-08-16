package com.micklab.pdf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.micklab.pdf.R
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.ocr.LlmApiType

/** OCR language codes offered as selectable chips, in display order. Tesseract covers
 *  all of these (each model downloads on demand); the extra scripts beyond the first four
 *  are Tesseract-only. `equ` is Tesseract's math/equation model (best-effort). */
val OCR_LANGUAGE_CODES =
    listOf("jpn", "eng", "chi_sim", "chi_tra", "kor", "ell", "rus", "ara", "heb", "equ")

/** Subset PaddleOCR can recognize; its settings section only offers these. */
val PADDLE_LANGUAGE_CODES = listOf("jpn", "eng", "chi_sim", "kor")

/** Localized display name for an OCR language [code] (falls back to the raw code). */
@Composable
fun ocrLanguageLabel(code: String): String = when (code) {
    "jpn" -> stringResource(R.string.lang_name_jpn)
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
