package com.micklab.pdf.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists Expert-mode (local OCR API server) preferences. */
@Singleton
class ExpertSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("expert_settings", Context.MODE_PRIVATE)

    var apiEnabled: Boolean
        get() = prefs.getBoolean(KEY_API_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_API_ENABLED, v).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(v) = prefs.edit().putInt(KEY_PORT, v).apply()

    companion object {
        const val DEFAULT_PORT = 8765
        private const val KEY_API_ENABLED = "api_enabled"
        private const val KEY_PORT = "api_port"
    }
}
