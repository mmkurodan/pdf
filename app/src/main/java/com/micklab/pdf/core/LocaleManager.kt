package com.micklab.pdf.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-app display language: [SYSTEM] (default), [JAPANESE] or [ENGLISH]. The
 * choice is stored locally and applied by wrapping the activity's base context
 * ([wrap] from attachBaseContext); the app default (values/) is Japanese and
 * English lives in values-en/, so following the system shows Japanese on JP
 * devices and English elsewhere.
 */
object LocaleManager {
    const val SYSTEM = "system"
    const val JAPANESE = "ja"
    const val ENGLISH = "en"

    private const val PREFS = "pdf_locale_prefs"
    private const val KEY = "lang"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    fun wrap(context: Context): Context {
        val lang = current(context)
        if (lang == SYSTEM) return context
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config)
    }
}
