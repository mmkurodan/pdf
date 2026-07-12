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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.model.TextSource
import com.micklab.pdf.domain.ocr.LlmApiType
import com.micklab.pdf.domain.ocr.LlmSettings
import com.micklab.pdf.domain.usecase.TextExtractionMode
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.PrimaryActionButton
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.navigation.PdfDestination

private val COMMON_LANGUAGES = listOf("jpn" to "日本語", "eng" to "英語", "chi_sim" to "中国語(簡)", "kor" to "韓国語")

@Composable
fun OcrScreen(onBack: () -> Unit, viewModel: OcrViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val modelOp by viewModel.modelOperation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }
    val pickModelDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importModels)
    }

    ToolScaffold(title = PdfDestination.OCR.title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "入力（PDF または 画像）") {
                Text(
                    if (ui.source == null) "ファイルが選択されていません" else ui.sourceName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { pickSource.launch(arrayOf("application/pdf", "image/*")) }) {
                    Text("ファイルを選択")
                }
            }

            SectionCard(title = "OCR 設定") {
                ChoiceChipsRow(
                    label = "エンジン",
                    options = ui.availableEngines.ifEmpty { listOf(OcrEngineType.TESSERACT) },
                    selected = ui.engine,
                    optionLabel = { it.displayName },
                    onSelect = viewModel::onEngineChanged,
                )
                ChoiceChipsRow(
                    label = "抽出モード",
                    options = TextExtractionMode.entries,
                    selected = ui.mode,
                    optionLabel = {
                        when (it) {
                            TextExtractionMode.AUTO -> "自動（埋め込み優先）"
                            TextExtractionMode.EMBEDDED_ONLY -> "埋め込みのみ"
                            TextExtractionMode.OCR_ONLY -> "OCR のみ"
                        }
                    },
                    onSelect = viewModel::onModeChanged,
                )
                LanguageChips(
                    selected = ui.languages,
                    installed = ui.installedLanguages,
                    onToggle = viewModel::toggleLanguage,
                )
                Text("レンダリング解像度: ${ui.dpi} DPI", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = ui.dpi.toFloat(),
                    onValueChange = { viewModel.onDpiChanged(it.toInt()) },
                    valueRange = 100f..400f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = ui.runInBackground, onCheckedChange = viewModel::onToggleBackground)
                    Text("  バックグラウンド実行（WorkManager）", style = MaterialTheme.typography.bodyMedium)
                }
            }

            val modelBusy = modelOp is OperationState.Running
            when (ui.engine) {
                OcrEngineType.TESSERACT -> TesseractModelSection(
                    installed = ui.installedLanguages,
                    ready = ui.tesseractReady,
                    busy = modelBusy,
                    onDownload = { viewModel.downloadModels() },
                    onImport = { pickModelDir.launch(null) },
                )

                OcrEngineType.LLM_VISION -> LlmSettingsSection(
                    settings = ui.llmSettings,
                    models = ui.llmModels,
                    busy = modelBusy,
                    onApiType = viewModel::onLlmApiTypeChanged,
                    onBaseUrl = viewModel::onLlmBaseUrlChanged,
                    onModel = viewModel::onLlmModelChanged,
                    onApiKey = viewModel::onLlmApiKeyChanged,
                    onFetchModels = viewModel::fetchLlmModels,
                    onTest = viewModel::testLlmConnection,
                )

                OcrEngineType.PADDLE_OCR -> PaddleModelSection(
                    downloaded = ui.paddleDownloaded,
                    busy = modelBusy,
                    onDownload = viewModel::downloadPaddleModels,
                )
            }

            OperationStatus(modelOp)
            (modelOp as? OperationState.Success)?.data?.let { message ->
                Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            PrimaryActionButton(
                text = "テキストを抽出",
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

@Composable
private fun TesseractModelSection(
    installed: Set<String>,
    ready: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onImport: () -> Unit,
) {
    SectionCard(title = "OCR モデル (traineddata)") {
        Text(
            if (installed.isEmpty()) {
                "未取込です。下のボタンで公式リポジトリからダウンロードできます（初回のみ通信、取込後は完全オフライン）。"
            } else {
                "取込済み: ${installed.sorted().joinToString(", ")}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onDownload, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
            Text("  選択中の言語モデルをダウンロード")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("端末内のフォルダから取り込む")
        }
        if (!ready) {
            Text(
                "※ 選択中の言語モデルが未取込です。上のボタンで取得してください。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmSettingsSection(
    settings: LlmSettings,
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
            "Ollama / OpenAI 互換サーバに接続し、ページ画像を送って OCR します（例: /root/llama の端末内サーバや Ollama）。",
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
private fun PaddleModelSection(downloaded: Boolean, busy: Boolean, onDownload: () -> Unit) {
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
            "初回のみ通信。取得後は完全オフラインで動作（日本語＋英数字）。ONNX Runtime で det→rec を実行します。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(selected: List<String>, installed: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("言語（複数選択可）", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMON_LANGUAGES.forEach { (code, name) ->
                val installedMark = if (code in installed) " ✓" else ""
                FilterChip(
                    selected = code in selected,
                    onClick = { onToggle(code) },
                    label = { Text("$name$installedMark") },
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

    SectionCard(title = "抽出結果") {
        Text("エンジン: ${result.engine}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "ページ: ${result.pageCount}　（埋め込み $embedded / OCR $ocr / なし $none）",
            style = MaterialTheme.typography.bodyMedium,
        )
        ChoiceChipsRow(
            label = "表示形式",
            options = OcrDisplayMode.entries,
            selected = mode,
            optionLabel = { if (it == OcrDisplayMode.TEXT) "テキストのみ" else "JSON" },
            onSelect = { mode = it },
        )
        TextButton(onClick = { onCopy(content) }) {
            Icon(Icons.Default.ContentCopy, null)
            Text(if (mode == OcrDisplayMode.TEXT) "  テキストをコピー" else "  JSON をコピー")
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
                    "（テキストがありません）",
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
