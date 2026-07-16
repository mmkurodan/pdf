package com.micklab.pdf.ui.summary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.usecase.DocumentSummary
import com.micklab.pdf.domain.usecase.SummaryMethod
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.PrimaryActionButton
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.navigation.PdfDestination

private val COMMON_LANGUAGES = listOf("jpn" to "日本語", "eng" to "英語", "chi_sim" to "中国語(簡)", "kor" to "韓国語")

@Composable
fun SummaryScreen(onBack: () -> Unit, viewModel: SummaryViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }

    ToolScaffold(title = androidx.compose.ui.res.stringResource(PdfDestination.SUMMARY.titleRes), onBack = onBack) { padding ->
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

            SectionCard(title = "要約方式") {
                ChoiceChipsRow(
                    label = "",
                    options = SummaryMethod.entries,
                    selected = ui.method,
                    optionLabel = {
                        when (it) {
                            SummaryMethod.OCR_THEN_LLM -> "OCR → LLM で要約"
                            SummaryMethod.LLM_VISION -> "LLM Vision が直接要約"
                        }
                    },
                    onSelect = viewModel::onMethodChanged,
                )
                if (ui.method == SummaryMethod.OCR_THEN_LLM) {
                    ChoiceChipsRow(
                        label = "OCR エンジン",
                        options = ui.availableEngines.ifEmpty { listOf(OcrEngineType.TESSERACT) },
                        selected = ui.engine,
                        optionLabel = { it.displayName },
                        onSelect = viewModel::onEngineChanged,
                    )
                    LanguageChips(selected = ui.languages, onToggle = viewModel::toggleLanguage)
                }
                Text("レンダリング解像度: ${ui.dpi} DPI", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = ui.dpi.toFloat(),
                    onValueChange = { viewModel.onDpiChanged(it.toInt()) },
                    valueRange = 100f..400f,
                )
            }

            SectionCard(title = "LLM 接続") {
                Text(
                    "${ui.llmSettings.apiType.displayName}\n${ui.llmSettings.baseUrl}\nモデル: ${ui.llmSettings.model}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "※ 接続先・モデルは「OCR / テキスト抽出」画面の LLM 設定を使用します。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryActionButton(
                text = "要約する",
                enabled = ui.source != null,
                loading = op is OperationState.Running,
                onClick = viewModel::run,
            )

            OperationStatus(op)
            (op as? OperationState.Success)?.data?.let { summary ->
                SummaryResult(summary = summary, onCopy = { clipboard.setText(AnnotatedString(it)) })
            }
        }
    }
}

@Composable
private fun SummaryResult(summary: DocumentSummary, onCopy: (String) -> Unit) {
    SectionCard(title = "全体の要約") {
        Text("方式: ${summary.method}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(summary.overallSummary, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onCopy(summary.overallSummary) }) {
            Icon(Icons.Default.ContentCopy, null)
            Text("  全体の要約をコピー")
        }
    }

    SectionCard(title = "ページごとの要約 (${summary.pages.size})") {
        summary.pages.forEachIndexed { index, page ->
            if (index > 0) HorizontalDivider()
            Text("ページ ${page.pageNumber}", style = MaterialTheme.typography.titleSmall)
            Text(page.summary, style = MaterialTheme.typography.bodyMedium)
        }
        val allText = buildString {
            appendLine("【全体】")
            appendLine(summary.overallSummary)
            appendLine()
            summary.pages.forEach { appendLine("【P${it.pageNumber}】"); appendLine(it.summary); appendLine() }
        }
        TextButton(onClick = { onCopy(allText) }) {
            Icon(Icons.Default.ContentCopy, null)
            Text("  すべてコピー")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(selected: List<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("言語（複数選択可）", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMON_LANGUAGES.forEach { (code, name) ->
                FilterChip(
                    selected = code in selected,
                    onClick = { onToggle(code) },
                    label = { Text(name) },
                )
            }
        }
    }
}
