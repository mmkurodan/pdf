package com.micklab.pdf.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.edit.ApplyEditsResult
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.OutputFolderSection
import com.micklab.pdf.ui.common.PrimaryActionButton
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.common.formatSize
import com.micklab.pdf.ui.common.openOutput
import com.micklab.pdf.ui.common.shareOutputs
import com.micklab.pdf.ui.navigation.PdfDestination

private val TEXT_COLORS = listOf(0x000000 to "黒", 0xD32F2F to "赤", 0x1976D2 to "青", 0x388E3C to "緑")

@Composable
fun EditScreen(onBack: () -> Unit, viewModel: EditViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::addImage)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onOutputTreePicked(uri)
    }

    ToolScaffold(title = PdfDestination.EDIT.title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "入力（PDF）") {
                Text(
                    if (ui.source == null) "ファイルが選択されていません" else ui.sourceName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (ui.pageCount > 0) {
                    Text("ページ数: ${ui.pageCount}", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { pickSource.launch(arrayOf("application/pdf")) }) {
                    Text("PDF を選択")
                }
            }

            FontCard(ui = ui, onDownload = viewModel::downloadFont)

            SectionCard(title = "配置") {
                OutlinedTextField(
                    value = ui.page.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let(viewModel::onPageChanged) },
                    label = { Text("ページ番号（1〜${ui.pageCount.coerceAtLeast(1)}）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                ChoiceChipsRow(
                    label = "位置",
                    options = Anchor.entries,
                    selected = ui.anchor,
                    optionLabel = { it.label },
                    onSelect = viewModel::onAnchorChanged,
                )
            }

            SectionCard(title = "テキストを追加") {
                OutlinedTextField(
                    value = ui.text,
                    onValueChange = viewModel::onTextChanged,
                    label = { Text("追加する文字") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("文字サイズ: ${ui.fontSizePt.toInt()} pt", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Slider(
                    value = ui.fontSizePt,
                    onValueChange = viewModel::onFontSizeChanged,
                    valueRange = 8f..48f,
                )
                ChoiceChipsRow(
                    label = "色",
                    options = TEXT_COLORS.map { it.first },
                    selected = ui.colorRgb,
                    optionLabel = { rgb -> TEXT_COLORS.firstOrNull { it.first == rgb }?.second ?: "色" },
                    onSelect = viewModel::onColorChanged,
                )
                Button(
                    onClick = viewModel::addText,
                    enabled = ui.source != null && ui.text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null); Text("  テキストを項目に追加")
                }
            }

            SectionCard(title = "画像を追加") {
                Text(
                    "選んだ画像を、現在の「ページ番号」「位置」に重ねます（背景は保持）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { pickImage.launch(arrayOf("image/*")) },
                    enabled = ui.source != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, null); Text("  画像を選んで追加")
                }
            }

            if (ui.ops.isNotEmpty()) {
                SectionCard(title = "編集項目 (${ui.ops.size})") {
                    ui.ops.forEach { pending ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pending.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { viewModel.removeOp(pending.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除")
                            }
                        }
                    }
                }
            }

            OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })

            PrimaryActionButton(
                text = "適用して保存",
                enabled = ui.source != null && ui.ops.isNotEmpty(),
                loading = op is OperationState.Running,
                onClick = viewModel::run,
            )

            OperationStatus(op)
            (op as? OperationState.Success)?.data?.let { result ->
                ResultCard(
                    result = result,
                    onOpen = { context.openOutput(result.output) },
                    onShare = { context.shareOutputs(listOf(result.output)) },
                )
            }
        }
    }
}

@Composable
private fun FontCard(ui: EditUiState, onDownload: () -> Unit) {
    SectionCard(title = "日本語フォント（テキスト追加に使用）") {
        when (ui.fontStage) {
            FontStage.AVAILABLE -> Text("取得済み。オフラインで利用できます。", style = MaterialTheme.typography.bodyMedium)
            FontStage.DOWNLOADING -> {
                Text("取得中… ${(ui.fontProgress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { ui.fontProgress }, modifier = Modifier.fillMaxWidth())
            }
            FontStage.UNKNOWN, FontStage.ERROR -> {
                Text(
                    "テキスト追加には日本語フォント(Noto Sans JP)が必要です。初回のみダウンロードし、以後はオフラインで動作します。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ui.fontStage == FontStage.ERROR && ui.fontError.isNotBlank()) {
                    Text(ui.fontError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null); Text("  フォントを取得")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: ApplyEditsResult, onOpen: () -> Unit, onShare: () -> Unit) {
    SectionCard(title = "結果") {
        Text("保存: ${result.output.displayName}", style = MaterialTheme.typography.bodyMedium)
        Text("サイズ: ${formatSize(result.output.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
        Text(
            "適用 ${result.ops.count { it.applied }} / ${result.ops.size}",
            style = MaterialTheme.typography.bodyMedium,
        )
        result.ops.forEach { r ->
            Text(
                "${if (r.applied) "✓" else "―"} ${r.detail}",
                style = MaterialTheme.typography.labelSmall,
                color = if (r.applied) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Text("  開く") }
            TextButton(onClick = onShare) { Icon(Icons.Default.Share, null); Text("  共有") }
        }
    }
}
