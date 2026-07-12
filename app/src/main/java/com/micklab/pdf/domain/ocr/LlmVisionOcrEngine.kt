package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.model.OcrBlock
import com.micklab.pdf.domain.model.OcrEngineType
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
 * OCR via a local/remote vision LLM over HTTP — the functionality adapted from
 * llamachat's LLM API, talking to an Ollama-compatible or OpenAI-compatible
 * server (e.g. `/root/llama`'s on-device server running a `gemma3:4b` vision
 * model). The page image is sent as base64 with an extraction prompt.
 *
 * This engine intentionally uses the network (it is opt-in and selectable); the
 * Tesseract engine remains fully offline.
 */
@Singleton
class LlmVisionOcrEngine @Inject constructor(
    private val settingsStore: LlmSettingsStore,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : OcrEngine {

    override val type: OcrEngineType = OcrEngineType.LLM_VISION

    override suspend fun isAvailable(languages: List<String>): Boolean = withContext(dispatchers.io) {
        val settings = settingsStore.get()
        runCatching {
            val base = settings.baseUrl.trimEnd('/')
            val url = when (settings.apiType) {
                LlmApiType.OLLAMA -> "$base/api/tags"
                LlmApiType.OPENAI -> "$base/v1/models"
            }
            httpGetStatus(url, settings.apiKey) in 200..299
        }.getOrDefault(false)
    }

    override suspend fun recognize(bitmap: Bitmap, languages: List<String>): OcrPageOutcome =
        withContext(dispatchers.io) {
            val settings = settingsStore.get()
            val base64 = bitmap.toJpegBase64(MAX_DIMENSION, JPEG_QUALITY)
            val prompt = buildPrompt(languages)

            val text = when (settings.apiType) {
                LlmApiType.OLLAMA -> requestOllama(settings, prompt, base64)
                LlmApiType.OPENAI -> requestOpenAi(settings, prompt, base64)
            }.trim()

            val blocks = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { OcrBlock(text = it, confidence = 0f, boundingBox = null) }
                .toList()

            Log.i(PdfToolsApp.TAG, "LLM OCR (${settings.apiType}/${settings.model}): ${text.length} chars")
            OcrPageOutcome(text = text, averageConfidence = 0f, blocks = blocks)
        }

    private fun buildPrompt(languages: List<String>): String {
        val names = languages.joinToString("・") { LANGUAGE_NAMES[it] ?: it }.ifEmpty { "自動判別" }
        return "この画像に含まれる文字を、レイアウトの読み順を保ったまますべて忠実に書き起こしてください。" +
            "対象言語: $names。説明・注釈・コードブロック・前置きは付けず、本文テキストのみを出力してください。"
    }

    private fun requestOllama(settings: LlmSettings, prompt: String, base64: String): String {
        val body = buildJsonObject {
            put("model", settings.model)
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                    putJsonArray("images") { add(base64) }
                }
            }
        }
        val response = httpPostJson("${settings.baseUrl.trimEnd('/')}/api/chat", body.toString(), settings.apiKey)
        return json.parseToJsonElement(response).jsonObject["message"]
            ?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw IOException("Ollama 応答を解析できません")
    }

    private fun requestOpenAi(settings: LlmSettings, prompt: String, base64: String): String {
        val body = buildJsonObject {
            put("model", settings.model)
            put("stream", false)
            put("max_tokens", 4096)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$base64")
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

    private fun httpPostJson(urlString: String, body: String, apiKey: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("LLM API エラー (HTTP $code): ${text.take(300)}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGetStatus(urlString: String, apiKey: String): Int {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = PING_TIMEOUT_MS
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun Bitmap.toJpegBase64(maxDimension: Int, quality: Int): String {
        val longest = max(width, height)
        val scaled = if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            Bitmap.createScaledBitmap(
                this,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            this
        }
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        if (scaled !== this) scaled.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 180_000 // vision generation can be slow
        const val PING_TIMEOUT_MS = 8_000
        const val MAX_DIMENSION = 1536
        const val JPEG_QUALITY = 90
        val LANGUAGE_NAMES = mapOf(
            "jpn" to "日本語",
            "eng" to "英語",
            "chi_sim" to "中国語",
            "kor" to "韓国語",
        )
    }
}
