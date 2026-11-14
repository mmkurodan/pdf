package com.micklab.pdf.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide view of the OCR API server, written by [OcrApiService] and read by
 * the home screen. Needed because the server can be switched on/off from outside
 * the UI (another app's Intent, the notification action, boot, the watchdog).
 */
object OcrApiState {

    data class Status(
        /** The persisted "keep the API up" intent (survives process death / reboot). */
        val enabled: Boolean = false,
        /** The socket is actually listening in this process. */
        val running: Boolean = false,
        val port: Int = OcrApiServer.DEFAULT_PORT,
        val error: String? = null,
    )

    private const val MAX_LOG_LINES = 200

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun update(transform: (Status) -> Status) = _status.update(transform)

    fun log(message: String) {
        val line = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "  " + message
        _log.update { (it + line).takeLast(MAX_LOG_LINES) }
    }

    fun clearLog() { _log.value = emptyList() }
}
