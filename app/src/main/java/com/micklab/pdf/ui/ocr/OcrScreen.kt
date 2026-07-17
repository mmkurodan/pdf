package com.micklab.pdf.ui.ocr

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.R
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.model.TextSource
import com.micklab.pdf.domain.usecase.TextExtractionMode
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.OCR_LANGUAGE_CODES
import com.micklab.pdf.ui.common.ocrEngineLabels
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.PrimaryActionButton
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.common.ocrLanguageLabel
import com.micklab.pdf.ui.navigation.PdfDestination

@Composable
fun OcrScreen(onBack: () -> Unit, viewModel: OcrViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }

    ToolScaffold(title = androidx.compose.ui.res.stringResource(PdfDestination.OCR.titleRes), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.ocr_input_title)) {
                Text(
                    if (ui.source == null) stringResource(R.string.label_no_selection) else ui.sourceName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { pickSource.launch(arrayOf("application/pdf", "image/*")) }) {
                    Text(stringResource(R.string.action_pick_file))
                }
            }

            SectionCard(title = stringResource(R.string.ocr_settings_title)) {
                val engineLabels = ocrEngineLabels()
                ChoiceChipsRow(
                    label = stringResource(R.string.ocr_engine_label),
                    options = ui.availableEngines.ifEmpty { listOf(OcrEngineType.TESSERACT) },
                    selected = ui.engine,
                    optionLabel = { engineLabels[it] ?: it.displayName },
                    onSelect = viewModel::onEngineChanged,
                )
                val modeAuto = stringResource(R.string.ocr_mode_auto)
                val modeEmbedded = stringResource(R.string.ocr_mode_embedded)
                val modeOcr = stringResource(R.string.ocr_mode_ocr)
                ChoiceChipsRow(
                    label = stringResource(R.string.ocr_mode_label),
                    options = TextExtractionMode.entries,
                    selected = ui.mode,
                    optionLabel = {
                        when (it) {
                            TextExtractionMode.AUTO -> modeAuto
                            TextExtractionMode.EMBEDDED_ONLY -> modeEmbedded
                            TextExtractionMode.OCR_ONLY -> modeOcr
                        }
                    },
                    onSelect = viewModel::onModeChanged,
                )
                LanguageChips(selected = ui.languages, onToggle = viewModel::toggleLanguage)
                Text(stringResource(R.string.ocr_dpi, ui.dpi), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = ui.dpi.toFloat(),
                    onValueChange = { viewModel.onDpiChanged(it.toInt()) },
                    valueRange = 100f..400f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = ui.runInBackground, onCheckedChange = viewModel::onToggleBackground)
                    Text("  " + stringResource(R.string.ocr_background), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.ocr_note_models),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryActionButton(
                text = stringResource(R.string.ocr_run),
                enabled = ui.source != null,
                loading = op is OperationState.Running,
                onClick = viewModel::run,
            )

            OperationStatus(op)
            (op as? OperationState.Success)?.data?.let { view ->
                ResultCard(view = view, onCopy = { clipboard.setText(AnnotatedString(it)) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(selected: List<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.ocr_lang_multi), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OCR_LANGUAGE_CODES.forEach { code ->
                FilterChip(
                    selected = code in selected,
                    onClick = { onToggle(code) },
                    label = { Text(ocrLanguageLabel(code)) },
                )
            }
        }
    }
}

private enum class OcrDisplayMode { TEXT, JSON }

@Composable
private fun ResultCard(view: OcrResultView, onCopy: (String) -> Unit) {
    val result = view.result
    val embedded = result.pages.count { it.source == TextSource.EMBEDDED_TEXT_LAYER }
    val ocr = result.pages.count { it.source == TextSource.OCR }
    val none = result.pages.count { it.source == TextSource.NONE }
    var mode by remember { mutableStateOf(OcrDisplayMode.TEXT) }
    val content = if (mode == OcrDisplayMode.TEXT) result.fullText else view.json

    SectionCard(title = stringResource(R.string.ocr_result_title)) {
        Text(stringResource(R.string.ocr_result_engine, result.engine), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.ocr_result_pages, result.pageCount, embedded, ocr, none),
            style = MaterialTheme.typography.bodyMedium,
        )
        val textOnly = stringResource(R.string.ocr_display_text)
        ChoiceChipsRow(
            label = stringResource(R.string.ocr_display_mode),
            options = OcrDisplayMode.entries,
            selected = mode,
            optionLabel = { if (it == OcrDisplayMode.TEXT) textOnly else "JSON" },
            onSelect = { mode = it },
        )
        val copyText = stringResource(R.string.action_copy_text)
        val copyJson = stringResource(R.string.action_copy_json)
        TextButton(onClick = { onCopy(content) }) {
            Icon(Icons.Default.ContentCopy, null)
            Text("  " + if (mode == OcrDisplayMode.TEXT) copyText else copyJson)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .then(if (mode == OcrDisplayMode.JSON) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        ) {
            if (content.isBlank()) {
                Text(
                    stringResource(R.string.ocr_no_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    content,
                    fontFamily = if (mode == OcrDisplayMode.JSON) FontFamily.Monospace else FontFamily.Default,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
