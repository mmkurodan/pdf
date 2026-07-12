package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import android.util.Base64
import com.micklab.pdf.core.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Thin HTTP client for a vision/text LLM, adapted from llamachat's LLM API.
 * Supports Ollama (`/api/chat`, `/api/tags`) and OpenAI-compatible
 * (`/v1/chat/completions`, `/v1/models`) servers. Shared by the OCR engine and
 * the summarization feature.
 */
@Singleton
class LlmClient @Inject constructor(
    private val settingsStore: LlmSettingsStore,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) {
    /** True if the server responds to its model-listing endpoint. */
    suspend fun ping(): Boolean = withContext(dispatchers.io) {
        runCatching {
            val settings = settingsStore.get()
            httpGetStatus(modelsUrl(settings), settings.apiKey) in 200..299
        }.getOrDefault(false)
    }

    /** Model names available on the server (Ollama `/api/tags` or OpenAI `/v1/models`). */
    suspend fun listModels(): List<String> = withContext(dispatchers.io) {
        val settings = settingsStore.get()
        val response = httpGet(modelsUrl(settings), settings.apiKey)
        val root = json.parseToJsonElement(response).jsonObject
        val array = when (settings.apiType) {
            LlmApiType.OLLAMA -> root["models"]?.jsonArray
            LlmApiType.OPENAI -> root["data"]?.jsonArray
        }.orEmpty()
        val field = if (settings.apiType == LlmApiType.OLLAMA) "name" else "id"
        array.mapNotNull { element ->
            runCatching { element.jsonObject[field]?.jsonPrimitive?.content }.getOrNull()
        }.distinct()
    }

    /** Runs a chat completion with [prompt] and an optional page image. */
    suspend fun chat(prompt: String, imageBase64: String? = null): String =
        withContext(dispatchers.io) {
            val settings = settingsStore.get()
            when (settings.apiType) {
                LlmApiType.OLLAMA -> ollamaChat(settings, prompt, imageBase64)
                LlmApiType.OPENAI -> openAiChat(settings, prompt, imageBase64)
            }.trim()
        }

    fun encodeJpegBase64(bitmap: Bitmap, maxDimension: Int = MAX_DIMENSION, quality: Int = JPEG_QUALITY): String {
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun ollamaChat(settings: LlmSettings, prompt: String, imageBase64: String?): String {
        val body = buildJsonObject {
            put("model", settings.model)
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                    if (imageBase64 != null) putJsonArray("images") { add(imageBase64) }
                }
            }
        }
        val response = httpPostJson("${settings.baseUrl.trimEnd('/')}/api/chat", body.toString(), settings.apiKey)
        return json.parseToJsonElement(response).jsonObject["message"]
            ?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw IOException("Ollama 応答を解析できません")
    }

    private fun openAiChat(settings: LlmSettings, prompt: String, imageBase64: String?): String {
        val body = buildJsonObject {
            put("model", settings.model)
            put("stream", false)
            put("max_tokens", 4096)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    if (imageBase64 == null) {
                        put("content", prompt)
                    } else {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64,$imageBase64")
                                }
                            }
                        }
                    }
                }
            }
        }
        val response =
            httpPostJson("${settings.baseUrl.trimEnd('/')}/v1/chat/completions", body.toString(), settings.apiKey)
        return json.parseToJsonElement(response).jsonObject["choices"]
            ?.jsonArray?.getOrNull(0)?.jsonObject?.get("message")
            ?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw IOException("OpenAI 互換応答を解析できません")
    }

    private fun modelsUrl(settings: LlmSettings): String {
        val base = settings.baseUrl.trimEnd('/')
        return when (settings.apiType) {
            LlmApiType.OLLAMA -> "$base/api/tags"
            LlmApiType.OPENAI -> "$base/v1/models"
        }
    }

    private fun httpGet(urlString: String, apiKey: String): String {
        val connection = openConnection(urlString, "GET", apiKey, PING_TIMEOUT_MS)
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("LLM API エラー (HTTP $code): ${text.take(200)}")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGetStatus(urlString: String, apiKey: String): Int {
        val connection = openConnection(urlString, "GET", apiKey, PING_TIMEOUT_MS)
        return try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPostJson(urlString: String, body: String, apiKey: String): String {
        val connection = openConnection(urlString, "POST", apiKey, READ_TIMEOUT_MS).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("LLM API エラー (HTTP $code): ${text.take(300)}")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(urlString: String, method: String, apiKey: String, readTimeout: Int): HttpURLConnection =
        (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            this.readTimeout = readTimeout
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 180_000 // generation can be slow
        const val PING_TIMEOUT_MS = 10_000
        const val MAX_DIMENSION = 1536
        const val JPEG_QUALITY = 90
    }
}
