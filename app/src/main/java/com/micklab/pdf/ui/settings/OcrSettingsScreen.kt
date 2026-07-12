package com.micklab.pdf.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.ocr.LlmApiType
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.navigation.PdfDestination

private val COMMON_LANGUAGES = listOf("jpn" to "日本語", "eng" to "英語", "chi_sim" to "中国語(簡)", "kor" to "韓国語")

@Composable
fun OcrSettingsScreen(onBack: () -> Unit, viewModel: OcrSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val busy = op is OperationState.Running

    val pickModelDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importTesseract)
    }

    ToolScaffold(title = PdfDestination.OCR_SETTINGS.title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "OCR エンジンのモデル取得と LLM 接続をここで管理します（OCR 実行画面とは独立）。",
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
                busy = busy,
                onApiType = viewModel::onLlmApiTypeChanged,
                onBaseUrl = viewModel::onLlmBaseUrlChanged,
                onModel = viewModel::onLlmModelChanged,
                onApiKey = viewModel::onLlmApiKeyChanged,
                onFetchModels = viewModel::fetchLlmModels,
                onTest = viewModel::testLlmConnection,
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
    SectionCard(title = "Tesseract 学習データ") {
        Text(
            if (installed.isEmpty()) "未取込です。取得したい言語を選んでダウンロードしてください。"
            else "取込済み: ${installed.sorted().joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("ダウンロードする言語", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMON_LANGUAGES.forEach { (code, name) ->
                val mark = if (code in installed) " ✓" else ""
                FilterChip(
                    selected = code in downloadLanguages,
                    onClick = { onToggleLanguage(code) },
                    label = { Text("$name$mark") },
                )
            }
        }
        Button(onClick = onDownload, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
            Text("  選択した言語モデルをダウンロード")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("端末内のフォルダから取り込む")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmSection(
    settings: com.micklab.pdf.domain.ocr.LlmSettings,
    models: List<String>,
    busy: Boolean,
    onApiType: (LlmApiType) -> Unit,
    onBaseUrl: (String) -> Unit,
    onModel: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onFetchModels: () -> Unit,
    onTest: () -> Unit,
) {
    SectionCard(title = "LLM 接続設定（Gemma 等）") {
        Text(
            "Ollama / OpenAI 互換サーバに接続します（OCR の LLM Vision・PDF サマリで共有）。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceChipsRow(
            label = "API 種別",
            options = LlmApiType.entries,
            selected = settings.apiType,
            optionLabel = { it.displayName },
            onSelect = onApiType,
        )
        OutlinedTextField(
            value = settings.baseUrl,
            onValueChange = onBaseUrl,
            label = { Text("ベース URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onFetchModels, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("モデル一覧を取得（/api/tags）")
        }
        if (models.isNotEmpty()) {
            Text("モデル選択", style = MaterialTheme.typography.labelLarge)
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
            label = { Text("モデル名（既定: default → 一覧から選択）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.apiKey,
            onValueChange = onApiKey,
            label = { Text("API キー（任意 / OpenAI 互換用）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onTest, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("接続確認")
        }
    }
}

@Composable
private fun PaddleSection(downloaded: Boolean, busy: Boolean, onDownload: () -> Unit) {
    SectionCard(title = "PaddleOCR モデル（ONNX）") {
        Text(
            if (downloaded) "モデル取得済み（ONNX det/rec + 日本語辞書）。オンデバイスで OCR できます。"
            else "未取得。下のボタンで PP-OCR の ONNX モデル（det/rec + 日本語辞書, 約 8MB）を取得します。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onDownload, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
            Text("  PaddleOCR モデルをダウンロード")
        }
        Text(
            "初回のみ通信。取得後は完全オフラインで動作（日本語＋英数字, 横書き向け）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
