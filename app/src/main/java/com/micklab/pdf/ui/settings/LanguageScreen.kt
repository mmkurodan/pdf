package com.micklab.pdf.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.micklab.pdf.R
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.navigation.PdfDestination

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val current = LocaleManager.current(context)

    ToolScaffold(title = stringResource(PdfDestination.LANGUAGE.titleRes), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.tool_language)) {
                LanguageOption(stringResource(R.string.lang_system), current == LocaleManager.SYSTEM) {
                    apply(context, LocaleManager.SYSTEM)
                }
                LanguageOption(stringResource(R.string.lang_japanese), current == LocaleManager.JAPANESE) {
                    apply(context, LocaleManager.JAPANESE)
                }
                LanguageOption(stringResource(R.string.lang_english), current == LocaleManager.ENGLISH) {
                    apply(context, LocaleManager.ENGLISH)
                }
                Text(
                    stringResource(R.string.lang_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun apply(context: Context, lang: String) {
    LocaleManager.set(context, lang)
    context.findActivity()?.recreate()
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
