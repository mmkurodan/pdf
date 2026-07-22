package com.micklab.pdf.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small app-wide preference store (distinct from feature-specific stores like LlmSettingsStore). */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("pdf_app_prefs", Context.MODE_PRIVATE)

    /** True once the user ticked "don't show again" on the startup "no OCR models" prompt. */
    var modelPromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_MODEL_PROMPT_DISMISSED, false)
        set(value) { prefs.edit().putBoolean(KEY_MODEL_PROMPT_DISMISSED, value).apply() }

    private companion object {
        const val KEY_MODEL_PROMPT_DISMISSED = "model_prompt_dismissed"
    }
}
