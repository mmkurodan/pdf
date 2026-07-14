package com.micklab.pdf.ui.edit

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

private val TEXT_COLORS = listOf(
    0x000000 to "黒", 0xFFFFFF to "白", 0x757575 to "灰",
    0xD32F2F to "赤", 0xF57C00 to "橙", 0xFBC02D to "黄",
    0x388E3C to "緑", 0x1976D2 to "青", 0x7B1FA2 to "紫",
)

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
                OutlinedButton(onClick = { pickSource.launch(arrayOf("application/pdf")) }) {
                    Text("PDF を選択")
                }
            }

            if (ui.source != null) {
                PreviewCard(
                    ui = ui,
                    onPrev = viewModel::prevPage,
                    onNext = viewModel::nextPage,
                    onTap = viewModel::onCanvasTap,
                    onDragStart = viewModel::onDragStart,
                    onDrag = viewModel::onDrag,
                )
            }

            FontCard(ui = ui, onDownload = viewModel::downloadFont)

            SectionCard(title = "テキストを追加") {
                Text(
                    "文字を入力して「追加」→ プレビュー上に置かれるのでドラッグで移動します。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ui.textInput,
                    onValueChange = viewModel::onTextInputChanged,
                    label = { Text("追加する文字（改行可）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("文字サイズ: ${ui.fontSizePt.toInt()} pt", style = MaterialTheme.typography.bodyMedium)
                Slider(value = ui.fontSizePt, onValueChange = viewModel::onFontSizeChanged, valueRange = 8f..200f)
                ColorChips(selected = ui.colorRgb, onSelect = viewModel::onColorChanged)
                Button(
                    onClick = viewModel::addText,
                    enabled = ui.source != null && ui.textInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null); Text("  追加（プレビューに配置）")
                }
            }

            SectionCard(title = "画像を追加") {
                Text(
                    "画像を選ぶとプレビュー上に置かれます。ドラッグで移動できます（背景は保持）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { pickImage.launch(arrayOf("image/*")) },
                    enabled = ui.source != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null); Text("  画像を選んで配置")
                }
            }

            ui.selected?.let { selected ->
                SelectedCard(
                    selected = selected,
                    onText = viewModel::onSelectedTextChanged,
                    onSize = viewModel::onSelectedSizeChanged,
                    onColor = viewModel::onSelectedColorChanged,
                    onReplacement = viewModel::onReplacementChanged,
                    onDelete = viewModel::deleteSelected,
                    onDeselect = viewModel::deselect,
                )
            }

            OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })

            PrimaryActionButton(
                text = "適用して保存",
                enabled = ui.source != null && ui.objects.isNotEmpty(),
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
private fun PreviewCard(
    ui: EditUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTap: (Float, Float) -> Unit,
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    SectionCard(title = "プレビュー（タップで選択・ドラッグで移動）") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrev, enabled = ui.page > 1) { Text("◀ 前") }
            Text(
                "ページ ${ui.page} / ${ui.pageCount.coerceAtLeast(1)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onNext, enabled = ui.pageCount == 0 || ui.page < ui.pageCount) { Text("次 ▶") }
        }
        val bitmap = ui.previewBitmap
        if (bitmap != null) {
            PageCanvas(bitmap = bitmap, ui = ui, onTap = onTap, onDragStart = onDragStart, onDrag = onDrag)
        } else {
            Text("プレビューを読み込み中…", style = MaterialTheme.typography.labelSmall)
        }
        Text(
            "文字（テキストレイヤー）をタップすると、その文を読み取って編集できます。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageCanvas(
    bitmap: Bitmap,
    ui: EditUiState,
    onTap: (Float, Float) -> Unit,
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val pageIndex = ui.page - 1
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
            .pointerInput(ui.page, ui.source) {
                detectTapGestures { offset -> onTap(offset.x / size.width, offset.y / size.height) }
            }
            .pointerInput(ui.page, ui.source) {
                detectDragGestures(
                    onDragStart = { offset -> onDragStart(offset.x / size.width, offset.y / size.height) },
                    onDrag = { change, delta ->
                        change.consume()
                        onDrag(delta.x / size.width, delta.y / size.height)
                    },
                )
            },
    ) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxPerPoint = if (ui.pageWidthPt > 0f) size.width / ui.pageWidthPt else 0f
            ui.objects.filter { it.pageIndex == pageIndex }.forEach { obj ->
                val left = obj.rect.left * size.width
                val top = obj.rect.top * size.height
                val w = (obj.rect.right - obj.rect.left) * size.width
                val h = (obj.rect.bottom - obj.rect.top) * size.height
                val selected = obj.id == ui.selectedId
                drawRect(
                    color = if (selected) Color(0xFF3F51B5) else Color(0x883F51B5),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = if (selected) 5f else 2f),
                )
                val native = drawContext.canvas.nativeCanvas
                when (obj) {
                    is EditorObject.TextObject ->
                        drawLines(native, obj.text, left, top, pxPerPoint * obj.fontSizePt, obj.colorRgb)
                    is EditorObject.EditObject ->
                        drawLines(native, obj.replacement, left, top, pxPerPoint * obj.fontSizePt, 0x1976D2)
                    is EditorObject.ImageObject ->
                        native.drawText("画像", left + 6f, top + h / 2f, Paint().apply {
                            color = 0xFF555555.toInt(); textSize = 28f; isAntiAlias = true
                        })
                }
            }
        }
    }
}

private fun drawLines(canvas: android.graphics.Canvas, text: String, x: Float, top: Float, sizePx: Float, rgb: Int) {
    if (sizePx < 2f) return
    val paint = Paint().apply {
        color = (0xFF000000.toInt()) or rgb
        textSize = sizePx.coerceAtLeast(8f)
        isAntiAlias = true
    }
    text.split(Regex("\\r?\\n")).forEachIndexed { i, line ->
        canvas.drawText(line, x, top + sizePx * (i + 1), paint)
    }
}

@Composable
private fun SelectedCard(
    selected: EditorObject,
    onText: (String) -> Unit,
    onSize: (Float) -> Unit,
    onColor: (Int) -> Unit,
    onReplacement: (String) -> Unit,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
) {
    SectionCard(title = "選択中の項目") {
        when (selected) {
            is EditorObject.TextObject -> {
                OutlinedTextField(
                    value = selected.text,
                    onValueChange = onText,
                    label = { Text("テキスト（改行可）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("文字サイズ: ${selected.fontSizePt.toInt()} pt", style = MaterialTheme.typography.bodyMedium)
                Slider(value = selected.fontSizePt, onValueChange = onSize, valueRange = 8f..200f)
                ColorChips(selected = selected.colorRgb, onSelect = onColor)
            }

            is EditorObject.EditObject -> {
                Text("元の文: ${selected.target}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = selected.replacement,
                    onValueChange = onReplacement,
                    label = { Text("置換後の文（改行可）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "埋め込みフォントで置換できない場合は、元のテキストを削除して既定フォントで描き直します。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is EditorObject.ImageObject ->
                Text("画像: ${selected.name}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("  削除") }
            TextButton(onClick = onDeselect) { Text("選択解除") }
        }
    }
}

@Composable
private fun ColorChips(selected: Int, onSelect: (Int) -> Unit) {
    ChoiceChipsRow(
        label = "色",
        options = TEXT_COLORS.map { it.first },
        selected = selected,
        optionLabel = { rgb -> TEXT_COLORS.firstOrNull { it.first == rgb }?.second ?: "色" },
        onSelect = onSelect,
    )
}

@Composable
private fun FontCard(ui: EditUiState, onDownload: () -> Unit) {
    SectionCard(title = "日本語フォント（テキスト追加・編集に使用）") {
        when (ui.fontStage) {
            FontStage.AVAILABLE -> Text("取得済み。オフラインで利用できます。", style = MaterialTheme.typography.bodyMedium)
            FontStage.DOWNLOADING -> {
                Text("取得中… ${(ui.fontProgress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { ui.fontProgress }, modifier = Modifier.fillMaxWidth())
            }
            FontStage.UNKNOWN, FontStage.ERROR -> {
                Text(
                    "テキストの追加・編集には日本語フォント(Noto Sans JP)が必要です。初回のみダウンロードし、以後はオフラインで動作します。",
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
        Text("適用 ${result.ops.count { it.applied }} / ${result.ops.size}", style = MaterialTheme.typography.bodyMedium)
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
