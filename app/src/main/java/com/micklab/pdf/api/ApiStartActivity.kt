package com.micklab.pdf.api

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Invisible trampoline that switches the OCR API on from another app.
 *
 * Some OEM ROMs (e.g. ZTE / Nubia "AutoLaunch") refuse a cross-app
 * startForegroundService() with "Blocked by AutoLaunch", but still allow a
 * user-visible activity launch. Once this activity is in front, this app is
 * foreground and may start its own foreground service.
 *
 * Contract: explicit Intent to com.micklab.pdf/.api.ApiStartActivity with
 * action com.micklab.pdf.START_SERVICE (optional int extra "port").
 */
class ApiStartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requested = intent?.getIntExtra(OcrApiService.EXTRA_PORT, -1) ?: -1
        val port = requested.takeIf { it > 0 }
        try {
            val start = OcrApiService.startIntent(applicationContext, port)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(start)
            } else {
                applicationContext.startService(start)
            }
        } catch (e: Exception) {
            Log.w("ApiStartActivity", "could not start OCR API service", e)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
