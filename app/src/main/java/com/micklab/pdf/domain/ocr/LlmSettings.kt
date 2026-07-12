package com.micklab.pdf.domain.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Which HTTP API dialect the LLM server speaks. */
enum class LlmApiType(val displayName: String) {
    /** Ollama native API: POST /api/chat, images as base64 array. */
    OLLAMA("Ollama (/api/chat)"),

    /** OpenAI-compatible: POST /v1/chat/completions, image_url data URIs. */
    OPENAI("OpenAI互換 (/v1/chat/completions)"),
}

/** Connection settings for the LLM-vision OCR backend. */
data class LlmSettings(
    val apiType: LlmApiType = LlmApiType.OLLAMA,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
) {
    companion object {
        // Matches llamachat's default; /root/llama's on-device server also serves here.
        const val DEFAULT_BASE_URL = "http://127.0.0.1:11434"
        // "default" is a placeholder; the user picks a real model from /api/tags.
        const val DEFAULT_MODEL = "default"
    }
}

/** Persists [LlmSettings] in SharedPreferences. */
@Singleton
class LlmSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("pdf_llm_prefs", Context.MODE_PRIVATE)

    fun get(): LlmSettings = LlmSettings(
        apiType = runCatching { LlmApiType.valueOf(prefs.getString(KEY_TYPE, null) ?: LlmApiType.OLLAMA.name) }
            .getOrDefault(LlmApiType.OLLAMA),
        baseUrl = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: LlmSettings.DEFAULT_BASE_URL,
        model = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: LlmSettings.DEFAULT_MODEL,
        apiKey = prefs.getString(KEY_KEY, "") ?: "",
    )

    fun save(settings: LlmSettings) {
        prefs.edit()
            .putString(KEY_TYPE, settings.apiType.name)
            .putString(KEY_URL, settings.baseUrl.trim())
            .putString(KEY_MODEL, settings.model.trim())
            .putString(KEY_KEY, settings.apiKey.trim())
            .apply()
    }

    private companion object {
        const val KEY_TYPE = "api_type"
        const val KEY_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_KEY = "api_key"
    }
}
