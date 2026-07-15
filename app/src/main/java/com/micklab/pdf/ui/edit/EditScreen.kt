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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import kotlin.math.min

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

            // Unified text field: adding new text and editing an existing/selected run share it.
            SectionCard(title = "テキスト") {
                when (val sel = ui.selected) {
                    is EditorObject.TextObject -> {
                        OutlinedTextField(
                            value = sel.text, onValueChange = viewModel::onSelectedTextChanged,
                            label = { Text("文字（改行可）") }, modifier = Modifier.fillMaxWidth(),
                        )
                        SizeSlider(sel.fontSizePt, viewModel::onSelectedSizeChanged)
                        ColorChips(sel.colorRgb, viewModel::onSelectedColorChanged)
                        DecideDeleteRow(viewModel::deselect, viewModel::deleteSelected)
                    }

                    is EditorObject.EditObject -> {
                        Text(
                            "元の文: ${sel.target}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = sel.delete, onCheckedChange = viewModel::onSelectedDeleteChanged)
                            Text("  元の文字を削除（置換しない）", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (sel.delete) {
                            Text(
                                "この文字を PDF から削除します。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedTextField(
                                value = sel.replacement, onValueChange = viewModel::onReplacementChanged,
                                label = { Text("置換後の文") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            )
                            Text(
                                "同じ文字集合なら元の書体・位置のまま置換します。表示できない文字や、プレビュー上でドラッグして移動した場合は、文全体を既定フォントで再生成します。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DecideDeleteRow(viewModel::deselect, viewModel::deleteSelected)
                    }

                    else -> {
                        Text(
                            "文字を入力→「追加」でプレビューに配置。ドラッグで移動、色・サイズを変えたら「決定」。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = ui.textInput, onValueChange = viewModel::onTextInputChanged,
                            label = { Text("追加する文字（改行可）") }, modifier = Modifier.fillMaxWidth(),
                        )
                        SizeSlider(ui.fontSizePt, viewModel::onFontSizeChanged)
                        ColorChips(ui.colorRgb, viewModel::onColorChanged)
                        Button(
                            onClick = viewModel::addText,
                            enabled = ui.source != null && ui.textInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, null); Text("  追加（プレビューに配置）")
                        }
                    }
                }
            }

            SectionCard(title = "画像") {
                val sel = ui.selected
                if (sel is EditorObject.ImageObject) {
                    Text("画像: ${sel.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "ドラッグで移動。よければ「決定」で確定します。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DecideDeleteRow(viewModel::deselect, viewModel::deleteSelected)
                } else {
                    Text(
                        "画像を選ぶとプレビューに配置されます（背景は保持）。",
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
private fun SizeSlider(value: Float, onChange: (Float) -> Unit) {
    Text("文字サイズ: ${value.toInt()} pt", style = MaterialTheme.typography.bodyMedium)
    Slider(value = value, onValueChange = onChange, valueRange = 8f..200f)
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
private fun DecideDeleteRow(onDecide: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onDecide, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Check, null); Text("  決定")
        }
        OutlinedButton(onClick = onCancel) { Icon(Icons.Default.Close, null); Text("  取消") }
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
            val imagePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
            ui.objects.filter { it.pageIndex == pageIndex }.forEach { obj ->
                val left = obj.rect.left * size.width
                val top = obj.rect.top * size.height
                val w = (obj.rect.right - obj.rect.left) * size.width
                val h = (obj.rect.bottom - obj.rect.top) * size.height
                val selected = obj.id == ui.selectedId
                val deleteMode = obj is EditorObject.EditObject && obj.delete
                val native = drawContext.canvas.nativeCanvas
                when (obj) {
                    is EditorObject.TextObject ->
                        drawLines(native, obj.text, left, top, pxPerPoint * obj.fontSizePt, obj.colorRgb)
                    is EditorObject.EditObject ->
                        // Label the pending action instead of overlaying the replacement on the
                        // original (baked into the page image), which would look duplicated.
                        native.drawText(
                            when { obj.delete -> "削除"; obj.moved -> "移動"; else -> "置換" }, left + 6f, top + 34f,
                            Paint().apply {
                                color = if (obj.delete) 0xFFD32F2F.toInt() else 0xFF3F51B5.toInt()
                                textSize = 30f
                                isAntiAlias = true
                            },
                        )
                    is EditorObject.ImageObject -> {
                        val bmp = obj.thumbnail
                        if (bmp != null) {
                            val scale = min(w / bmp.width, h / bmp.height)
                            val dw = bmp.width * scale
                            val dh = bmp.height * scale
                            val dl = left + (w - dw) / 2f
                            val dt = top + (h - dh) / 2f
                            native.drawBitmap(bmp, null, android.graphics.RectF(dl, dt, dl + dw, dt + dh), imagePaint)
                        } else {
                            native.drawText("画像", left + 6f, top + h / 2f, Paint().apply {
                                color = 0xFF555555.toInt(); textSize = 28f; isAntiAlias = true
                            })
                        }
                    }
                }
                val base = if (deleteMode) Color(0xFFD32F2F) else Color(0xFF3F51B5)
                drawRect(
                    color = base.copy(alpha = if (selected) 1f else 0.5f),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = if (selected) 5f else 2f),
                )
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
