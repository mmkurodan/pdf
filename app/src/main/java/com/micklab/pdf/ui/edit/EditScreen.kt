package com.micklab.pdf.ui.edit

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.R
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.edit.ApplyEditsResult
import com.micklab.pdf.domain.edit.scaledAboutCenter
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

private val TEXT_COLOR_RGBS = listOf(
    0x000000, 0xFFFFFF, 0x757575,
    0xD32F2F, 0xF57C00, 0xFBC02D,
    0x388E3C, 0x1976D2, 0x7B1FA2,
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

    ToolScaffold(title = androidx.compose.ui.res.stringResource(PdfDestination.EDIT.titleRes), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.edit_input_title)) {
                Text(
                    if (ui.source == null) stringResource(R.string.label_no_selection) else ui.sourceName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickSource.launch(arrayOf("application/pdf")) }) {
                        Text(stringResource(R.string.action_pick_pdf))
                    }
                    OutlinedButton(onClick = viewModel::createBlank) {
                        Text(stringResource(R.string.edit_create_blank))
                    }
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
            SectionCard(title = stringResource(R.string.edit_text_section)) {
                when (val sel = ui.selected) {
                    is EditorObject.TextObject -> {
                        OutlinedTextField(
                            value = sel.text, onValueChange = viewModel::onSelectedTextChanged,
                            label = { Text(stringResource(R.string.edit_text_field_label)) }, modifier = Modifier.fillMaxWidth(),
                        )
                        SizeSlider(sel.fontSizePt, viewModel::onSelectedSizeChanged)
                        ColorChips(sel.colorRgb, viewModel::onSelectedColorChanged)
                        StyleToggles(
                            sel.bold, sel.italic, sel.underline,
                            viewModel::onSelectedBoldChanged, viewModel::onSelectedItalicChanged, viewModel::onSelectedUnderlineChanged,
                        )
                        RotationSlider(sel.rotationDeg, viewModel::onSelectedRotationChanged)
                        UrlField(sel.url, viewModel::onSelectedUrlChanged)
                        DecideDeleteRow(viewModel::commitPreview, viewModel::deleteSelected)
                    }

                    is EditorObject.EditObject -> {
                        Text(
                            stringResource(R.string.edit_original_text, sel.target),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = sel.delete, onCheckedChange = viewModel::onSelectedDeleteChanged)
                            Text("  " + stringResource(R.string.edit_delete_original), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (sel.delete) {
                            Text(
                                stringResource(R.string.edit_delete_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedTextField(
                                value = sel.replacement, onValueChange = viewModel::onReplacementChanged,
                                label = { Text(stringResource(R.string.edit_replacement_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            )
                            SizeSlider(sel.fontSizePt, viewModel::onSelectedSizeChanged)
                            ColorChips(sel.colorRgb, viewModel::onSelectedColorChanged)
                            Text(
                                stringResource(R.string.edit_replace_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DecideDeleteRow(viewModel::commitPreview, viewModel::deleteSelected)
                    }

                    else -> {
                        Text(
                            stringResource(R.string.edit_add_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = ui.textInput, onValueChange = viewModel::onTextInputChanged,
                            label = { Text(stringResource(R.string.edit_add_field_label)) }, modifier = Modifier.fillMaxWidth(),
                        )
                        SizeSlider(ui.fontSizePt, viewModel::onFontSizeChanged)
                        ColorChips(ui.colorRgb, viewModel::onColorChanged)
                        StyleToggles(
                            ui.bold, ui.italic, ui.underline,
                            viewModel::onBoldChanged, viewModel::onItalicChanged, viewModel::onUnderlineChanged,
                        )
                        RotationSlider(ui.rotationDeg, viewModel::onRotationChanged)
                        UrlField(ui.url, viewModel::onUrlChanged)
                        Button(
                            onClick = viewModel::addText,
                            enabled = ui.source != null && ui.textInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, null); Text("  " + stringResource(R.string.edit_add_button))
                        }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.edit_image_section)) {
                val sel = ui.selected
                if (sel is EditorObject.ImageObject) {
                    Text(
                        if (sel.annotationId != null) stringResource(R.string.edit_image_layer)
                        else stringResource(R.string.edit_image_name, sel.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (sel.delete) stringResource(R.string.edit_image_delete_note)
                        else stringResource(R.string.edit_image_move_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!sel.delete) {
                        ScaleSlider(sel.scale, viewModel::onSelectedScaleChanged)
                        if (sel.annotationId == null) {
                            RotationSlider(sel.rotationDeg, viewModel::onSelectedImageRotationChanged)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::commitPreview, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Check, null); Text("  " + stringResource(R.string.edit_decide))
                        }
                        if (sel.annotationId != null && !sel.delete) {
                            OutlinedButton(onClick = { viewModel.onSelectedDeleteChanged(true) }) { Text(stringResource(R.string.action_delete)) }
                        }
                        OutlinedButton(onClick = viewModel::deleteSelected) { Icon(Icons.Default.Close, null); Text(stringResource(R.string.edit_cancel)) }
                    }
                } else {
                    Text(
                        stringResource(R.string.edit_image_pick_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { pickImage.launch(arrayOf("image/*")) },
                        enabled = ui.source != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null); Text("  " + stringResource(R.string.edit_image_pick_button))
                    }
                }
            }

            if (ui.objects.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.edit_layers_title, ui.objects.size)) {
                    ui.objects.forEach { obj ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                layerLabel(obj),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.select(obj.id) }
                                    .padding(vertical = 8.dp),
                                color = if (obj.id == ui.selectedId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(onClick = { viewModel.removeObject(obj.id) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_cancel))
                            }
                        }
                    }
                    Button(onClick = viewModel::commitPreview, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Check, null); Text("  " + stringResource(R.string.edit_commit_button))
                    }
                }
            }

            OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })

            PrimaryActionButton(
                text = stringResource(R.string.edit_run),
                enabled = ui.source != null && (ui.objects.isNotEmpty() || ui.committed),
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
    Text(stringResource(R.string.edit_font_size, value.toInt()), style = MaterialTheme.typography.bodyMedium)
    Slider(value = value, onValueChange = onChange, valueRange = 8f..200f)
}

@Composable
private fun ColorChips(selected: Int, onSelect: (Int) -> Unit) {
    val colorLabel = stringResource(R.string.edit_color_label)
    val names = mapOf(
        0x000000 to stringResource(R.string.color_black),
        0xFFFFFF to stringResource(R.string.color_white),
        0x757575 to stringResource(R.string.color_gray),
        0xD32F2F to stringResource(R.string.color_red),
        0xF57C00 to stringResource(R.string.color_orange),
        0xFBC02D to stringResource(R.string.color_yellow),
        0x388E3C to stringResource(R.string.color_green),
        0x1976D2 to stringResource(R.string.color_blue),
        0x7B1FA2 to stringResource(R.string.color_purple),
    )
    ChoiceChipsRow(
        label = colorLabel,
        options = TEXT_COLOR_RGBS,
        selected = selected,
        optionLabel = { rgb -> names[rgb] ?: colorLabel },
        onSelect = onSelect,
    )
}

@Composable
private fun RotationSlider(value: Int, onChange: (Int) -> Unit) {
    Text(stringResource(R.string.edit_rotation, value), style = MaterialTheme.typography.bodyMedium)
    Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..360f)
}

@Composable
private fun ScaleSlider(value: Float, onChange: (Float) -> Unit) {
    Text(stringResource(R.string.edit_scale, (value * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
    Slider(value = value, onValueChange = onChange, valueRange = 0.2f..3f)
}

@Composable
private fun UrlField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.edit_url_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StyleToggles(
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    onBold: (Boolean) -> Unit,
    onItalic: (Boolean) -> Unit,
    onUnderline: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = bold, onClick = { onBold(!bold) }, label = { Text(stringResource(R.string.edit_style_bold)) })
        FilterChip(selected = italic, onClick = { onItalic(!italic) }, label = { Text(stringResource(R.string.edit_style_italic)) })
        FilterChip(selected = underline, onClick = { onUnderline(!underline) }, label = { Text(stringResource(R.string.edit_style_underline)) })
    }
}

@Composable
private fun DecideDeleteRow(onDecide: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onDecide, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Check, null); Text("  " + stringResource(R.string.edit_decide))
        }
        OutlinedButton(onClick = onCancel) { Icon(Icons.Default.Close, null); Text("  " + stringResource(R.string.edit_cancel)) }
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
    SectionCard(title = stringResource(R.string.edit_preview_title)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrev, enabled = ui.page > 1) { Text(stringResource(R.string.edit_prev)) }
            Text(
                stringResource(R.string.edit_page_of, ui.page, ui.pageCount.coerceAtLeast(1)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onNext, enabled = ui.pageCount == 0 || ui.page < ui.pageCount) { Text(stringResource(R.string.edit_next)) }
        }
        val bitmap = ui.previewBitmap
        if (bitmap != null) {
            PageCanvas(bitmap = bitmap, ui = ui, onTap = onTap, onDragStart = onDragStart, onDrag = onDrag)
        } else {
            Text(stringResource(R.string.edit_preview_loading), style = MaterialTheme.typography.labelSmall)
        }
        Text(
            stringResource(R.string.edit_preview_hint),
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
    val badgeDelete = stringResource(R.string.edit_badge_delete)
    val badgeMove = stringResource(R.string.edit_badge_move)
    val badgeReplace = stringResource(R.string.edit_badge_replace)
    val badgeImage = stringResource(R.string.edit_badge_image)
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
                val effRect = if (obj is EditorObject.ImageObject) obj.rect.scaledAboutCenter(obj.scale) else obj.rect
                val left = effRect.left * size.width
                val top = effRect.top * size.height
                val w = (effRect.right - effRect.left) * size.width
                val h = (effRect.bottom - effRect.top) * size.height
                val selected = obj.id == ui.selectedId
                val deleteMode = (obj is EditorObject.EditObject && obj.delete) ||
                    (obj is EditorObject.ImageObject && obj.delete)
                val native = drawContext.canvas.nativeCanvas
                when (obj) {
                    is EditorObject.TextObject -> {
                        val rotated = obj.rotationDeg % 360 != 0
                        if (rotated) {
                            native.save()
                            native.rotate(obj.rotationDeg.toFloat(), left + w / 2f, top + h / 2f)
                        }
                        drawLines(native, obj.text, left, top, pxPerPoint * obj.fontSizePt, obj.colorRgb, obj.bold, obj.italic, obj.underline)
                        if (rotated) native.restore()
                    }
                    is EditorObject.EditObject ->
                        // Label the pending action instead of overlaying the replacement on the
                        // original (baked into the page image), which would look duplicated.
                        native.drawText(
                            when { obj.delete -> badgeDelete; obj.moved -> badgeMove; else -> badgeReplace }, left + 6f, top + 34f,
                            Paint().apply {
                                color = if (obj.delete) 0xFFD32F2F.toInt() else 0xFF3F51B5.toInt()
                                textSize = 30f
                                isAntiAlias = true
                            },
                        )
                    is EditorObject.ImageObject -> {
                        val bmp = obj.thumbnail
                        when {
                            obj.delete -> native.drawText(
                                badgeDelete, left + 6f, top + 34f,
                                Paint().apply { color = 0xFFD32F2F.toInt(); textSize = 30f; isAntiAlias = true },
                            )
                            bmp != null -> {
                                val scale = min(w / bmp.width, h / bmp.height)
                                val dw = bmp.width * scale
                                val dh = bmp.height * scale
                                val dl = left + (w - dw) / 2f
                                val dt = top + (h - dh) / 2f
                                val rot = obj.rotationDeg % 360 != 0
                                if (rot) {
                                    native.save()
                                    native.rotate(obj.rotationDeg.toFloat(), left + w / 2f, top + h / 2f)
                                }
                                native.drawBitmap(bmp, null, android.graphics.RectF(dl, dt, dl + dw, dt + dh), imagePaint)
                                if (rot) native.restore()
                            }
                            // Existing annotation layers are drawn by the page render itself.
                            obj.annotationId != null -> Unit
                            else -> native.drawText(
                                badgeImage, left + 6f, top + h / 2f,
                                Paint().apply { color = 0xFF555555.toInt(); textSize = 28f; isAntiAlias = true },
                            )
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

private fun drawLines(
    canvas: android.graphics.Canvas,
    text: String,
    x: Float,
    top: Float,
    sizePx: Float,
    rgb: Int,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
) {
    if (sizePx < 2f) return
    val paint = Paint().apply {
        color = (0xFF000000.toInt()) or rgb
        textSize = sizePx.coerceAtLeast(8f)
        isAntiAlias = true
        isFakeBoldText = bold
        isUnderlineText = underline
        textSkewX = if (italic) -0.25f else 0f
    }
    text.split(Regex("\\r?\\n")).forEachIndexed { i, line ->
        canvas.drawText(line, x, top + sizePx * (i + 1), paint)
    }
}

@Composable
private fun layerLabel(obj: EditorObject): String = when (obj) {
    is EditorObject.TextObject -> "T｜${obj.text.replace('\n', ' ').take(16)}"
    is EditorObject.ImageObject -> "IMG｜${obj.name.take(16)}"
    is EditorObject.EditObject -> when {
        obj.delete -> "${stringResource(R.string.edit_badge_delete)}｜${obj.target.take(14)}"
        obj.moved -> "${stringResource(R.string.edit_badge_move)}｜${obj.target.take(14)}"
        else -> "${stringResource(R.string.edit_badge_replace)}｜${obj.target.take(8)}→${obj.replacement.take(8)}"
    }
}

@Composable
private fun FontCard(ui: EditUiState, onDownload: () -> Unit) {
    SectionCard(title = stringResource(R.string.edit_font_title)) {
        when (ui.fontStage) {
            FontStage.AVAILABLE -> Text(stringResource(R.string.edit_font_ready), style = MaterialTheme.typography.bodyMedium)
            FontStage.DOWNLOADING -> {
                Text(stringResource(R.string.edit_font_downloading, (ui.fontProgress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { ui.fontProgress }, modifier = Modifier.fillMaxWidth())
            }
            FontStage.UNKNOWN, FontStage.ERROR -> {
                Text(
                    stringResource(R.string.edit_font_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ui.fontStage == FontStage.ERROR && ui.fontError.isNotBlank()) {
                    Text(ui.fontError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null); Text("  " + stringResource(R.string.edit_font_download))
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: ApplyEditsResult, onOpen: () -> Unit, onShare: () -> Unit) {
    SectionCard(title = stringResource(R.string.edit_result_title)) {
        Text(stringResource(R.string.edit_result_saved, result.output.displayName), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.edit_result_size, formatSize(result.output.sizeBytes)), style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.edit_result_applied, result.ops.count { it.applied }, result.ops.size), style = MaterialTheme.typography.bodyMedium)
        result.ops.forEach { r ->
            Text(
                "${if (r.applied) "✓" else "―"} ${r.detail}",
                style = MaterialTheme.typography.labelSmall,
                color = if (r.applied) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Text("  " + stringResource(R.string.action_open)) }
            TextButton(onClick = onShare) { Icon(Icons.Default.Share, null); Text("  " + stringResource(R.string.action_share)) }
        }
    }
}
