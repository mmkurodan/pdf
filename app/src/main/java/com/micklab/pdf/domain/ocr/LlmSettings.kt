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
    /** Model used by LLM Vision OCR (LlmVisionOcrEngine). */
    val model: String = DEFAULT_MODEL,
    /** Model used for text operations: summarization, AI prompt, AI-OCR text editing. */
    val textModel: String = DEFAULT_MODEL,
    val apiKey: String = "",
) {
    /**
     * True while the vision (OCR) [model] is still the unconfigured "default"
     * placeholder. Vision needs a real multimodal model, so callers warn on this.
     */
    val isVisionModelUnset: Boolean
        get() = model.isBlank() || model.trim().equals(DEFAULT_MODEL, ignoreCase = true)

    /** Model actually used for text operations: [textModel], falling back to [model] when blank. */
    val effectiveTextModel: String
        get() = textModel.takeIf { it.isNotBlank() } ?: model

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
        textModel = prefs.getString(KEY_TEXT_MODEL, null)?.takeIf { it.isNotBlank() } ?: LlmSettings.DEFAULT_MODEL,
        apiKey = prefs.getString(KEY_KEY, "") ?: "",
    )

    fun save(settings: LlmSettings) {
        prefs.edit()
            .putString(KEY_TYPE, settings.apiType.name)
            .putString(KEY_URL, settings.baseUrl.trim())
            .putString(KEY_MODEL, settings.model.trim())
            .putString(KEY_TEXT_MODEL, settings.textModel.trim())
            .putString(KEY_KEY, settings.apiKey.trim())
            .apply()
    }

    private companion object {
        const val KEY_TYPE = "api_type"
        const val KEY_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_TEXT_MODEL = "text_model"
        const val KEY_KEY = "api_key"
    }
}

/**
 * Remembers which LLM models have already completed at least one successful call,
 * so the "the model may need loading" confirmation is shown only on the first run
 * of a model (or right after switching to a not-yet-used model), never again once
 * that model has responded successfully.
 */
@Singleton
class LlmModelLoadStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("pdf_llm_loaded", Context.MODE_PRIVATE)

    fun isLoaded(model: String): Boolean {
        val name = model.trim()
        return name.isNotEmpty() && prefs.getStringSet(KEY_LOADED, emptySet()).orEmpty().contains(name)
    }

    fun markLoaded(model: String) {
        val name = model.trim()
        if (name.isEmpty()) return
        val current = prefs.getStringSet(KEY_LOADED, emptySet()).orEmpty()
        if (name in current) return
        prefs.edit().putStringSet(KEY_LOADED, current + name).apply()
    }

    private companion object {
        const val KEY_LOADED = "loaded_models"
    }
}
