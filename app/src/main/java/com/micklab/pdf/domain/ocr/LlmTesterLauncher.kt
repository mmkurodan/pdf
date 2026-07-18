package com.micklab.pdf.domain.ocr

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launches the companion **LLM Tester** app (`com.micklab.llama`) that hosts an
 * on-device Ollama-compatible API server, mirroring the "Launch LLM API" button
 * in llamachat / reversy. Starts its foreground service (so the API comes up)
 * and opens the app; if it isn't installed, falls back to its Play Store page.
 */
@Singleton
class LlmTesterLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Result { LAUNCHED, STORE_OPENED, FAILED }

    /** Requests the API server on [port], then brings the LLM Tester app forward. */
    fun launch(port: Int): Result {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launchIntent == null) {
            return if (openPlayStore()) Result.STORE_OPENED else Result.FAILED
        }
        requestApiStart(port)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            Result.LAUNCHED
        } catch (e: ActivityNotFoundException) {
            Log.w(PdfToolsApp.TAG, "LLM Tester launch activity not found", e)
            if (openPlayStore()) Result.STORE_OPENED else Result.FAILED
        }
    }

    /** Port from the configured base URL, or the Ollama default. */
    fun resolvePort(baseUrl: String): Int {
        val port = runCatching { Uri.parse(baseUrl.trim()).port }.getOrDefault(-1)
        return if (port > 0) port else DEFAULT_PORT
    }

    private fun requestApiStart(port: Int) {
        val intent = Intent()
            .setClassName(PACKAGE, SERVICE_CLASS)
            .setAction(ACTION_START)
            .putExtra("port", port)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // The app may be missing the service, or the OS may block a background
            // start — the user can still enable the API server in LLM Tester itself.
            Log.w(PdfToolsApp.TAG, "LLM Tester API start not allowed", e)
        }
    }

    private fun openPlayStore(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(PdfToolsApp.TAG, "Unable to open LLM Tester Play Store page", e)
            false
        }
    }

    private companion object {
        const val PACKAGE = "com.micklab.llama"
        const val SERVICE_CLASS = "com.micklab.llama.OllamaForegroundService"
        const val ACTION_START = "com.micklab.llama.START_SERVICE"
        const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.micklab.llama"
        const val DEFAULT_PORT = 11434
    }
}
