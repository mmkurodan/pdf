package com.micklab.pdf.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.ocr.OcrEngineRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Minimal HTTP/1.1 server that exposes a single OCR endpoint.
 * Modelled after OllamaApiServer.java in the companion llama project:
 * raw ServerSocket + ThreadPoolExecutor, no external library.
 *
 * Endpoint:  POST /ocr   (multipart/form-data)
 * Fields:    file (binary), engine (tesseract|paddleocr|llm), lang (e.g. eng,jpn)
 */
class OcrApiServer(
    private val context: Context,
    private val ocrRegistry: OcrEngineRegistry,
) {
    companion object {
        const val DEFAULT_PORT = 8765
        private const val TAG = "OcrApiServer"
        private const val MAX_BODY_BYTES = 50 * 1024 * 1024   // 50 MB
        private const val RENDER_DPI = 150
        private const val MAX_SIDE = 4000
    }

    private var port = DEFAULT_PORT
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val connections = ArrayList<Socket>()
    private val connLock = Any()
    private var listener: ServerListener? = null

    private val pool = ThreadPoolExecutor(
        2, 16, 60L, TimeUnit.SECONDS, LinkedBlockingQueue(64),
        ThreadPoolExecutor.CallerRunsPolicy(),
    )

    interface ServerListener {
        fun onServerStarted(port: Int)
        fun onServerStopped()
        fun onServerError(message: String)
        fun onRequest(method: String, path: String)
    }

    fun setPort(p: Int) { port = p }
    fun setListener(l: ServerListener) { listener = l }
    fun isRunning() = running.get()

    fun start() {
        Thread({ runServer() }, "OcrApiServer-accept").apply { isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        synchronized(connLock) { connections.forEach { runCatching { it.close() } }; connections.clear() }
        listener?.onServerStopped()
    }

    fun disconnectAll() = synchronized(connLock) {
        connections.forEach { runCatching { it.close() } }
        connections.clear()
    }

    // ── Server loop ──────────────────────────────────────────────────────────

    private fun runServer() {
        try {
            serverSocket = ServerSocket(port)
            running.set(true)
            Log.i(TAG, "Listening on 0.0.0.0:$port")
            listener?.onServerStarted(port)
            while (running.get()) {
                val sock = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                synchronized(connLock) { connections.add(sock) }
                pool.execute {
                    try { handleConnection(sock) }
                    catch (e: Exception) { Log.d(TAG, "Connection closed: ${e.message}") }
                    finally { synchronized(connLock) { connections.remove(sock) }; runCatching { sock.close() } }
                }
            }
        } catch (e: Exception) {
            if (running.get()) { Log.e(TAG, "Server error", e); listener?.onServerError(e.message ?: "error") }
        } finally {
            running.set(false)
        }
    }

    // ── Request parsing ───────────────────────────────────────────────────────

    private fun handleConnection(sock: Socket) {
        val inp = sock.getInputStream()
        val out = sock.getOutputStream()

        val reqLine = readLine(inp) ?: return
        val parts = reqLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val rawPath = parts[1]
        val path = rawPath.substringBefore("?").lowercase()

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(inp) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }

        listener?.onRequest(method, path)

        when {
            method == "OPTIONS"                   -> cors204(out)
            method == "GET" && path == "/health"  -> sendJson(out, 200, """{"status":"ok"}""")
            method == "POST" && path == "/ocr"    -> handleOcr(inp, out, headers)
            else                                  -> sendJson(out, 404, """{"error":"Not found"}""")
        }
    }

    // ── /ocr handler ─────────────────────────────────────────────────────────

    private fun handleOcr(inp: InputStream, out: OutputStream, headers: Map<String, String>) {
        val ct = headers["content-type"] ?: ""
        if (!ct.contains("multipart/form-data", ignoreCase = true)) {
            sendJson(out, 400, """{"error":"Expected multipart/form-data"}"""); return
        }
        val boundary = ct.substringAfter("boundary=", "").trim()
        if (boundary.isEmpty()) { sendJson(out, 400, """{"error":"Missing boundary"}"""); return }

        val len = headers["content-length"]?.toLongOrNull() ?: -1L
        val body = readBody(inp, len)

        val parts = parseMultipart(body, boundary)
        val fileBytes = parts["file"] ?: run { sendJson(out, 400, """{"error":"Missing 'file' field"}"""); return }
        val fileCt = parts["__ct_file__"]?.toString(Charsets.ISO_8859_1) ?: "application/octet-stream"

        val engine = parts["engine"]?.toString(Charsets.UTF_8)?.trim() ?: "tesseract"
        val langParam = parts["lang"]?.toString(Charsets.UTF_8)?.trim() ?: "eng"
        val languages = langParam.split(",", "+").map { it.trim() }.filter { it.isNotEmpty() }

        val engineType = when (engine.lowercase()) {
            "paddleocr", "paddle"     -> OcrEngineType.PADDLE_OCR
            "llm", "llmvision", "llm_vision" -> OcrEngineType.LLM_VISION
            else                      -> OcrEngineType.TESSERACT
        }

        val ocrEngine = try { ocrRegistry.engine(engineType) } catch (e: Exception) {
            sendJson(out, 400, jsonError("Unknown engine: $engine")); return
        }

        val isPdf = fileCt.contains("pdf", ignoreCase = true) || fileBytes.isPdfMagic()

        try {
            val pages = JSONArray()
            if (isPdf) {
                val tmp = File.createTempFile("ocr_", ".pdf", context.cacheDir)
                try {
                    tmp.writeBytes(fileBytes)
                    val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    try {
                        for (i in 0 until renderer.pageCount) {
                            val bm = renderPdfPage(renderer, i)
                            val res = runBlocking { ocrEngine.recognize(bm, languages) }
                            bm.recycle()
                            pages.put(JSONObject().apply {
                                put("page", i + 1)
                                put("text", res.text)
                                put("confidence", res.averageConfidence)
                                put("source", "OCR")
                            })
                        }
                    } finally { renderer.close(); pfd.close() }
                } finally { tmp.delete() }
            } else {
                val bm = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
                    ?: run { sendJson(out, 400, jsonError("Could not decode image")); return }
                val res = runBlocking { ocrEngine.recognize(bm, languages) }
                bm.recycle()
                pages.put(JSONObject().apply {
                    put("page", 1)
                    put("text", res.text)
                    put("confidence", res.averageConfidence)
                    put("source", "OCR")
                })
            }

            val resp = JSONObject().apply {
                put("pages", pages)
                put("engine", engineType.displayName)
                put("languages", JSONArray(languages))
                put("pageCount", pages.length())
            }
            sendJson(out, 200, resp.toString())
        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            sendJson(out, 500, jsonError(e.message ?: "OCR failed"))
        }
    }

    // ── PDF rendering ─────────────────────────────────────────────────────────

    private fun renderPdfPage(renderer: PdfRenderer, idx: Int): Bitmap {
        val page = renderer.openPage(idx)
        return try {
            val scale = RENDER_DPI / 72f
            val w = (page.width * scale).roundToInt().coerceIn(1, MAX_SIDE)
            val h = (page.height * scale).roundToInt().coerceIn(1, MAX_SIDE)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bm ->
                bm.eraseColor(Color.WHITE)
                page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        } finally { page.close() }
    }

    // ── Multipart parser ──────────────────────────────────────────────────────

    private fun parseMultipart(body: ByteArray, boundary: String): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        val delim = ("--$boundary").toByteArray(Charsets.ISO_8859_1)
        val end  = ("--$boundary--").toByteArray(Charsets.ISO_8859_1)

        var pos = indexOf(body, delim, 0) ?: return result

        while (pos < body.size) {
            pos += delim.size
            // skip CRLF after delimiter
            if (pos + 1 < body.size && body[pos] == CR && body[pos + 1] == LF) pos += 2
            else if (pos < body.size && body[pos] == LF) pos++

            // check final boundary
            if (startsWith(body, end, pos - end.size)) break
            if (startsWith(body, byteArrayOf('-'.code.toByte(), '-'.code.toByte()), pos)) break

            // read part headers
            val partHeaders = mutableMapOf<String, String>()
            while (pos < body.size) {
                val eol = crlfAt(body, pos) ?: break
                val line = String(body, pos, eol - pos, Charsets.UTF_8)
                pos = eol + 2
                if (line.isEmpty()) break
                val c = line.indexOf(':')
                if (c > 0) partHeaders[line.substring(0, c).trim().lowercase()] = line.substring(c + 1).trim()
            }

            // part body: everything up to \r\n--boundary
            val nextDelim = indexOf(body, delim, pos) ?: break
            var bodyEnd = nextDelim
            if (bodyEnd >= 2 && body[bodyEnd - 2] == CR && body[bodyEnd - 1] == LF) bodyEnd -= 2
            else if (bodyEnd >= 1 && body[bodyEnd - 1] == LF) bodyEnd--
            val partBody = body.copyOfRange(pos, bodyEnd.coerceAtLeast(pos))
            pos = nextDelim

            val disposition = partHeaders["content-disposition"] ?: continue
            val name = disposition.substringAfter("name=\"").substringBefore("\"")
            result[name] = partBody
            val partCt = partHeaders["content-type"]
            if (partCt != null && name == "file") result["__ct_file__"] = partCt.toByteArray(Charsets.ISO_8859_1)
        }
        return result
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int? {
        outer@ for (i in from..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return null
    }

    private fun startsWith(arr: ByteArray, pre: ByteArray, at: Int): Boolean {
        if (at < 0 || at + pre.size > arr.size) return false
        for (i in pre.indices) if (arr[at + i] != pre[i]) return false
        return true
    }

    private fun crlfAt(arr: ByteArray, from: Int): Int? {
        for (i in from until arr.size - 1) if (arr[i] == CR && arr[i + 1] == LF) return i
        return null
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private fun readLine(inp: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = inp.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == LF.toInt() && prev == CR.toInt()) { sb.deleteCharAt(sb.length - 1); return sb.toString() }
            sb.append(b.toChar()); prev = b
        }
    }

    private fun readBody(inp: InputStream, length: Long): ByteArray {
        if (length in 1..MAX_BODY_BYTES) {
            val buf = ByteArray(length.toInt())
            var read = 0
            while (read < buf.size) {
                val n = inp.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            return if (read == buf.size) buf else buf.copyOf(read)
        }
        val bos = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        var total = 0L
        var n: Int
        while (inp.read(tmp).also { n = it } >= 0 && total < MAX_BODY_BYTES) { bos.write(tmp, 0, n); total += n }
        return bos.toByteArray()
    }

    private fun cors204(out: OutputStream) = sendResponse(out, 204, "No Content", "text/plain", "",
        mapOf("Access-Control-Allow-Methods" to "POST, GET, OPTIONS",
              "Access-Control-Allow-Headers" to "Content-Type, Authorization"))

    private fun sendJson(out: OutputStream, status: Int, body: String) =
        sendResponse(out, status, statusText(status), "application/json", body)

    private fun sendResponse(out: OutputStream, status: Int, st: String, ct: String, body: String,
                             extra: Map<String, String> = emptyMap()) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val sb = buildString {
            append("HTTP/1.1 $status $st\r\n")
            append("Content-Type: $ct; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            extra.forEach { (k, v) -> append("$k: $v\r\n") }
            append("\r\n")
        }
        out.write(sb.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun statusText(code: Int) = when (code) {
        200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"
        404 -> "Not Found"; 500 -> "Internal Server Error"; else -> "Error"
    }

    private fun jsonError(msg: String) = """{"error":${JSONObject.quote(msg)}}"""
    private fun ByteArray.isPdfMagic() = size >= 4 &&
        this[0] == '%'.code.toByte() && this[1] == 'P'.code.toByte() &&
        this[2] == 'D'.code.toByte() && this[3] == 'F'.code.toByte()

    private val CR = '\r'.code.toByte()
    private val LF = '\n'.code.toByte()
}
