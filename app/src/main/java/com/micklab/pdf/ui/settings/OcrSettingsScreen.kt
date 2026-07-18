package com.micklab.pdf.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.R
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.ocr.LlmApiType
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.OCR_LANGUAGE_CODES
import com.micklab.pdf.ui.common.llmApiTypeLabels
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.common.ocrLanguageLabel
import com.micklab.pdf.ui.navigation.PdfDestination

@Composable
fun OcrSettingsScreen(onBack: () -> Unit, viewModel: OcrSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val busy = op is OperationState.Running

    val pickModelDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importTesseract)
    }

    ToolScaffold(title = androidx.compose.ui.res.stringResource(PdfDestination.OCR_SETTINGS.titleRes), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.set_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TesseractSection(
                downloadLanguages = ui.downloadLanguages,
                installed = ui.installedLanguages,
                busy = busy,
                onToggleLanguage = viewModel::toggleDownloadLanguage,
                onDownload = { viewModel.downloadTesseract() },
                onImport = { pickModelDir.launch(null) },
            )

            LlmSection(
                settings = ui.llmSettings,
                models = ui.llmModels,
                apiAvailable = ui.llmApiAvailable,
                busy = busy,
                onApiType = viewModel::onLlmApiTypeChanged,
                onBaseUrl = viewModel::onLlmBaseUrlChanged,
                onModel = viewModel::onLlmModelChanged,
                onApiKey = viewModel::onLlmApiKeyChanged,
                onFetchModels = viewModel::fetchLlmModels,
                onTest = viewModel::testLlmConnection,
                onLaunchApi = viewModel::launchLlmApi,
                onRecheck = viewModel::refreshLlmStatus,
            )

            PaddleSection(
                downloaded = ui.paddleDownloaded,
                busy = busy,
                onDownload = viewModel::downloadPaddleModels,
            )

            OperationStatus(op)
            (op as? OperationState.Success)?.data?.let { message ->
                Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TesseractSection(
    downloadLanguages: List<String>,
    installed: Set<String>,
    busy: Boolean,
    onToggleLanguage: (String) -> Unit,
    onDownload: () -> Unit,
    onImport: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.set_tess_title)) {
        Text(
            if (installed.isEmpty()) stringResource(R.string.set_tess_none)
            else stringResource(R.string.set_tess_installed, installed.sorted().joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.set_download_langs), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OCR_LANGUAGE_CODES.forEach { code ->
                val mark = if (code in installed) " ✓" else ""
                FilterChip(
                    selected = code in downloadLanguages,
                    onClick = { onToggleLanguage(code) },
                    label = { Text(ocrLanguageLabel(code) + mark) },
                )
            }
        }
        Button(onClick = onDownload, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
            Text("  " + stringResource(R.string.set_tess_download))
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.set_tess_import))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmSection(
    settings: com.micklab.pdf.domain.ocr.LlmSettings,
    models: List<String>,
    apiAvailable: Boolean,
    busy: Boolean,
    onApiType: (LlmApiType) -> Unit,
    onBaseUrl: (String) -> Unit,
    onModel: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onFetchModels: () -> Unit,
    onTest: () -> Unit,
    onLaunchApi: () -> Unit,
    onRecheck: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.set_llm_title)) {
        Text(
            stringResource(R.string.set_llm_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLaunchApi, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Text("  " + stringResource(R.string.set_launch_llm))
        }
        LlmStatusTile(available = apiAvailable, onClick = onRecheck)
        val apiLabels = llmApiTypeLabels()
        ChoiceChipsRow(
            label = stringResource(R.string.set_api_type),
            options = LlmApiType.entries,
            selected = settings.apiType,
            optionLabel = { apiLabels[it] ?: it.displayName },
            onSelect = onApiType,
        )
        OutlinedTextField(
            value = settings.baseUrl,
            onValueChange = onBaseUrl,
            label = { Text(stringResource(R.string.set_base_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onFetchModels, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.set_fetch_models))
        }
        if (models.isNotEmpty()) {
            Text(stringResource(R.string.set_model_select), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { model ->
                    FilterChip(
                        selected = model == settings.model,
                        onClick = { onModel(model) },
                        label = { Text(model) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = settings.model,
            onValueChange = onModel,
            label = { Text(stringResource(R.string.set_model_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.apiKey,
            onValueChange = onApiKey,
            label = { Text(stringResource(R.string.set_api_key)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onTest, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.set_test_conn))
        }
    }
}

/** Green "available" / amber "not available" API status tile; tap to re-check. */
@Composable
private fun LlmStatusTile(available: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (available) Color(0xFF81C784) else Color(0xFFFFF59D))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (available) R.string.set_llm_available else R.string.set_llm_unavailable),
            color = Color(0xFF000000),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun PaddleSection(downloaded: Boolean, busy: Boolean, onDownload: () -> Unit) {
    SectionCard(title = stringResource(R.string.set_paddle_title)) {
        Text(
            if (downloaded) stringResource(R.string.set_paddle_done)
            else stringResource(R.string.set_paddle_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onDownload, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
            Text("  " + stringResource(R.string.set_paddle_download))
        }
        Text(
            stringResource(R.string.set_paddle_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
