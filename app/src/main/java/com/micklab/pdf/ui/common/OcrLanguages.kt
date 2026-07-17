package com.micklab.pdf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.micklab.pdf.R
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.ocr.LlmApiType

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
