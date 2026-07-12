package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.model.OcrBlock
import com.micklab.pdf.domain.model.OcrEngineType
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR via a vision LLM over HTTP (Ollama or OpenAI-compatible), delegating all
 * transport to [LlmClient]. The page image is sent with an extraction prompt and
 * the model's text response becomes the OCR result. Opt-in (uses the network);
 * the Tesseract engine remains fully offline.
 */
@Singleton
class LlmVisionOcrEngine @Inject constructor(
    private val llmClient: LlmClient,
    private val dispatchers: DispatcherProvider,
) : OcrEngine {

    override val type: OcrEngineType = OcrEngineType.LLM_VISION

    override suspend fun isAvailable(languages: List<String>): Boolean = llmClient.ping()

    override suspend fun recognize(bitmap: Bitmap, languages: List<String>): OcrPageOutcome =
        withContext(dispatchers.io) {
            val base64 = llmClient.encodeJpegBase64(bitmap)
            val text = llmClient.chat(buildPrompt(languages), base64)

            val blocks = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { OcrBlock(text = it, confidence = 0f, boundingBox = null) }
                .toList()

            Log.i(PdfToolsApp.TAG, "LLM OCR: ${text.length} chars")
            OcrPageOutcome(text = text, averageConfidence = 0f, blocks = blocks)
        }

    private fun buildPrompt(languages: List<String>): String {
        val names = languages.joinToString("・") { LANGUAGE_NAMES[it] ?: it }.ifEmpty { "自動判別" }
        return "この画像に含まれる文字を、レイアウトの読み順を保ったまますべて忠実に書き起こしてください。" +
            "対象言語: $names。説明・注釈・コードブロック・前置きは付けず、本文テキストのみを出力してください。"
    }

    private companion object {
        val LANGUAGE_NAMES = mapOf(
            "jpn" to "日本語",
            "eng" to "英語",
            "chi_sim" to "中国語",
            "kor" to "韓国語",
        )
    }
}
