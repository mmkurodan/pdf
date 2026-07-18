package com.micklab.pdf.ui.prompt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.R
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.usecase.PromptResult
import com.micklab.pdf.domain.usecase.PromptScope
import com.micklab.pdf.domain.usecase.SummaryMethod
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.OCR_LANGUAGE_CODES
import com.micklab.pdf.ui.common.llmApiTypeLabels
import com.micklab.pdf.ui.common.ocrEngineLabels
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.PrimaryActionButton
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.common.ocrLanguageLabel
import com.micklab.pdf.ui.navigation.PdfDestination

@Composable
fun PromptScreen(onBack: () -> Unit, viewModel: PromptViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }

    ToolScaffold(title = stringResource(PdfDestination.PROMPT.titleRes), onBack = onBack) { padding ->
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

            SectionCard(title = stringResource(R.string.prm_prompt_title)) {
                OutlinedTextField(
                    value = ui.prompt,
                    onValueChange = viewModel::onPromptChanged,
                    label = { Text(stringResource(R.string.prm_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.prm_prompt_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = stringResource(R.string.prm_scope_title)) {
                val scopeWhole = stringResource(R.string.prm_scope_whole)
                val scopePage = stringResource(R.string.prm_scope_page)
                ChoiceChipsRow(
                    label = "",
                    options = PromptScope.entries,
                    selected = ui.scope,
                    optionLabel = {
                        when (it) {
                            PromptScope.WHOLE_DOCUMENT -> scopeWhole
                            PromptScope.PER_PAGE -> scopePage
                        }
                    },
                    onSelect = viewModel::onScopeChanged,
                )
            }

            SectionCard(title = stringResource(R.string.sum_method_title)) {
                val methodOcrLlm = stringResource(R.string.sum_method_ocr_llm)
                val methodVision = stringResource(R.string.sum_method_vision)
                ChoiceChipsRow(
                    label = "",
                    options = SummaryMethod.entries,
                    selected = ui.method,
                    optionLabel = {
                        when (it) {
                            SummaryMethod.OCR_THEN_LLM -> methodOcrLlm
                            SummaryMethod.LLM_VISION -> methodVision
                        }
                    },
                    onSelect = viewModel::onMethodChanged,
                )
                if (ui.method == SummaryMethod.OCR_THEN_LLM) {
                    val engineLabels = ocrEngineLabels()
                    ChoiceChipsRow(
                        label = stringResource(R.string.sum_ocr_engine),
                        options = ui.availableEngines.ifEmpty { listOf(OcrEngineType.TESSERACT) },
                        selected = ui.engine,
                        optionLabel = { engineLabels[it] ?: it.displayName },
                        onSelect = viewModel::onEngineChanged,
                    )
                    LanguageChips(selected = ui.languages, onToggle = viewModel::toggleLanguage)
                }
                Text(stringResource(R.string.ocr_dpi, ui.dpi), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = ui.dpi.toFloat(),
                    onValueChange = { viewModel.onDpiChanged(it.toInt()) },
                    valueRange = 100f..400f,
                )
            }

            SectionCard(title = stringResource(R.string.sum_llm_title)) {
                val apiLabel = llmApiTypeLabels()[ui.llmSettings.apiType] ?: ui.llmSettings.apiType.displayName
                Text(
                    stringResource(R.string.sum_llm_info, apiLabel, ui.llmSettings.baseUrl, ui.llmSettings.model),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.sum_llm_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryActionButton(
                text = stringResource(R.string.prm_run),
                enabled = ui.source != null && ui.prompt.isNotBlank(),
                loading = op is OperationState.Running,
                onClick = viewModel::run,
            )

            OperationStatus(op)
            (op as? OperationState.Success)?.data?.let { result ->
                PromptResultView(result = result, onCopy = { clipboard.setText(AnnotatedString(it)) })
            }
        }
    }
}

@Composable
private fun PromptResultView(result: PromptResult, onCopy: (String) -> Unit) {
    val scopeLabel = when (result.scope) {
        PromptScope.WHOLE_DOCUMENT -> stringResource(R.string.prm_scope_whole)
        PromptScope.PER_PAGE -> stringResource(R.string.prm_scope_page)
    }
    val methodLabel = when (result.method) {
        SummaryMethod.OCR_THEN_LLM -> stringResource(R.string.sum_method_ocr_llm)
        SummaryMethod.LLM_VISION -> stringResource(R.string.sum_method_vision)
    }

    if (result.wholeAnswer.isNotBlank()) {
        SectionCard(title = stringResource(R.string.prm_whole_title)) {
            Text(
                stringResource(R.string.prm_detail, scopeLabel, methodLabel, result.engineLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(result.wholeAnswer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onCopy(result.wholeAnswer) }) {
                Icon(Icons.Default.ContentCopy, null)
                Text("  " + stringResource(R.string.prm_copy))
            }
        }
    }

    if (result.pages.isNotEmpty()) {
        SectionCard(title = stringResource(R.string.prm_pages_title, result.pages.size)) {
            Text(
                stringResource(R.string.prm_detail, scopeLabel, methodLabel, result.engineLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.pages.forEachIndexed { index, page ->
                if (index > 0) HorizontalDivider()
                Text(stringResource(R.string.cd_page, page.pageNumber), style = MaterialTheme.typography.titleSmall)
                Text(page.answer, style = MaterialTheme.typography.bodyMedium)
            }
            val pageHeaders = result.pages.map { stringResource(R.string.prm_export_page_header, it.pageNumber) }
            val allText = buildString {
                result.pages.forEachIndexed { i, p -> appendLine(pageHeaders[i]); appendLine(p.answer); appendLine() }
            }
            TextButton(onClick = { onCopy(allText) }) {
                Icon(Icons.Default.ContentCopy, null)
                Text("  " + stringResource(R.string.prm_copy_all))
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
