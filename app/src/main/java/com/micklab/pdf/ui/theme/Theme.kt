package com.micklab.pdf.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = md_primary,
    onPrimary = md_on_primary,
    primaryContainer = md_primary_container,
    onPrimaryContainer = md_on_primary_container,
    secondary = md_secondary,
    tertiary = md_tertiary,
)

private val DarkColors = darkColorScheme(
    primary = md_dark_primary,
    onPrimary = md_dark_on_primary,
    primaryContainer = md_dark_primary_container,
    onPrimaryContainer = md_dark_on_primary_container,
    secondary = md_dark_secondary,
    tertiary = md_dark_tertiary,
)

/**
 * App theme. Honors the system light/dark setting (ダークモード対応) and uses
 * Material You dynamic color on Android 12+ when available.
 */
@Composable
fun PdfToolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
