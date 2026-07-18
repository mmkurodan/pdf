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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.R
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.edit.ApplyEditsResult
import com.micklab.pdf.domain.edit.TextRun
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
import kotlin.math.roundToInt

private val TEXT_COLOR_RGBS = listOf(
    0x000000, 0xFFFFFF, 0x757575,
    0xD32F2F, 0xF57C00, 0xFBC02D,
    0x388E3C, 0x1976D2, 0x7B1FA2,
)

/** Which floating window (if any) is open over the fixed canvas. */
private enum class Panel { None, Text, Image, Layers, Font, Save }

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

    var panel by remember { mutableStateOf(Panel.None) }
    // Selecting an object (on the canvas or from the layer list) opens its floating editor;
    // clearing the selection closes an open editor. Other panels are opened from the toolbox.
    LaunchedEffect(ui.selectedId) {
        when (ui.selected) {
            is EditorObject.TextObject, is EditorObject.EditObject -> panel = Panel.Text
            is EditorObject.ImageObject -> panel = Panel.Image
            null -> if (panel == Panel.Text || panel == Panel.Image) panel = Panel.None
        }
    }
    // Every panel's 決定 commits identically: bake pending edits into the temp PDF and reload.
    val commit: () -> Unit = { viewModel.commitPreview(); panel = Panel.None }

    ToolScaffold(title = stringResource(PdfDestination.EDIT.titleRes), onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (ui.source == null) {
                EmptyState(
                    ui = ui,
                    onPick = { pickSource.launch(arrayOf("application/pdf")) },
                    onCreateBlank = viewModel::createBlank,
                    onDownloadFont = viewModel::downloadFont,
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    SourceBar(name = ui.sourceName, onChange = { pickSource.launch(arrayOf("application/pdf")) })
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val bitmap = ui.previewBitmap
                        if (bitmap != null) {
                            FittedCanvas(
                                bitmap = bitmap,
                                ui = ui,
                                onTap = viewModel::onCanvasTap,
                                onDragStart = viewModel::onDragStart,
                                onDrag = viewModel::onDrag,
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.edit_preview_loading), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    EditToolbar(
                        ui = ui,
                        onPrev = viewModel::prevPage,
                        onNext = viewModel::nextPage,
                        onAddText = { viewModel.deselect(); panel = Panel.Text },
                        onAddImage = { pickImage.launch(arrayOf("image/*")) },
                        onLayers = { panel = Panel.Layers },
                        onFont = { panel = Panel.Font },
                        onSave = { panel = Panel.Save },
                    )
                }

                // --- floating windows (drawn over the fixed canvas) ---
                when (panel) {
                    Panel.Text -> FloatingPanel(
                        title = stringResource(R.string.edit_text_section),
                        onClose = { viewModel.deselect(); panel = Panel.None },
                    ) { TextPanelContent(ui, viewModel, onCommit = commit) }

                    Panel.Image -> FloatingPanel(
                        title = stringResource(R.string.edit_image_section),
                        onClose = { viewModel.deselect(); panel = Panel.None },
                    ) { ImagePanelContent(ui, viewModel, onCommit = commit) }

                    Panel.Layers -> FloatingPanel(
                        title = stringResource(R.string.edit_tool_layers),
                        onClose = { panel = Panel.None },
                    ) { LayersPanelContent(ui, viewModel, onCommit = commit) }

                    Panel.Font -> FloatingPanel(
                        title = stringResource(R.string.edit_font_title),
                        onClose = { panel = Panel.None },
                    ) { FontPanelContent(ui, onDownload = viewModel::downloadFont) }

                    Panel.Save -> FloatingPanel(
                        title = stringResource(R.string.edit_run),
                        onClose = { panel = Panel.None },
                    ) {
                        SavePanelContent(
                            ui = ui,
                            loading = op is OperationState.Running,
                            onPickTree = { pickTree.launch(null) },
                            onRun = viewModel::run,
                        )
                    }

                    Panel.None -> Unit
                }

                // Progress / error, pinned above everything (transient).
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
                    OperationStatus(op)
                }

                // Result of a completed save, as its own floating window.
                (op as? OperationState.Success)?.data?.let { result ->
                    FloatingPanel(
                        title = stringResource(R.string.edit_result_title),
                        onClose = viewModel::dismissResult,
                    ) {
                        ResultPanelContent(
                            result = result,
                            onOpen = { context.openOutput(result.output) },
                            onShare = { context.shareOutputs(listOf(result.output)) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fixed layout pieces
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(
    ui: EditUiState,
    onPick: () -> Unit,
    onCreateBlank: () -> Unit,
    onDownloadFont: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.edit_input_title)) {
            Text(stringResource(R.string.label_no_selection), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPick) { Text(stringResource(R.string.action_pick_pdf)) }
                OutlinedButton(onClick = onCreateBlank) { Text(stringResource(R.string.edit_create_blank)) }
            }
        }
        SectionCard(title = stringResource(R.string.edit_font_title)) {
            FontPanelContent(ui, onDownload = onDownloadFont)
        }
    }
}

@Composable
private fun SourceBar(name: String, onChange: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onChange) { Text(stringResource(R.string.edit_change)) }
        }
    }
}

/** The page image, scaled to fit the fixed area entirely (never scrolled). */
@Composable
private fun FittedCanvas(
    bitmap: Bitmap,
    ui: EditUiState,
    onTap: (Float, Float) -> Unit,
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        val pageAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val boxAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else pageAspect
        // Constrain by the tighter dimension so the whole page is always visible.
        val sizeMod = if (pageAspect >= boxAspect) Modifier.fillMaxWidth() else Modifier.fillMaxHeight()
        PageCanvas(
            bitmap = bitmap,
            ui = ui,
            onTap = onTap,
            onDragStart = onDragStart,
            onDrag = onDrag,
            modifier = sizeMod.aspectRatio(pageAspect),
        )
    }
}

@Composable
private fun EditToolbar(
    ui: EditUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
    onLayers: () -> Unit,
    onFont: () -> Unit,
    onSave: () -> Unit,
) {
    val layerCount = ui.objects.size
    Surface(tonalElevation = 3.dp) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev, enabled = ui.page > 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.edit_prev))
                }
                Text(
                    stringResource(R.string.edit_page_of, ui.page, ui.pageCount.coerceAtLeast(1)),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onNext, enabled = ui.pageCount == 0 || ui.page < ui.pageCount) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.edit_next))
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.Default.TextFields, stringResource(R.string.edit_text_section), onAddText)
                ToolButton(Icons.Default.AddPhotoAlternate, stringResource(R.string.edit_image_section), onAddImage)
                ToolButton(
                    Icons.Default.Layers,
                    if (layerCount > 0) stringResource(R.string.edit_layers_title, layerCount) else stringResource(R.string.edit_tool_layers),
                    onLayers,
                )
                ToolButton(Icons.Default.FontDownload, stringResource(R.string.edit_tool_font), onFont, warn = ui.fontStage != FontStage.AVAILABLE)
                ToolButton(Icons.Default.Save, stringResource(R.string.edit_tool_save), onSave)
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    warn: Boolean = false,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .widthIn(min = 64.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(icon, contentDescription = label, tint = tint)
            if (warn) {
                Box(Modifier.size(8.dp).offset(x = 3.dp, y = (-2).dp)) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFD32F2F)) }
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Floating window frame
// ---------------------------------------------------------------------------

@Composable
private fun FloatingPanel(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize()) {
        ElevatedCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .padding(12.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = 460.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            dragOffset = Offset(dragOffset.x + delta.x, dragOffset.y + delta.y)
                        }
                    }
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_close))
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Panel contents
// ---------------------------------------------------------------------------

@Composable
private fun TextPanelContent(ui: EditUiState, vm: EditViewModel, onCommit: () -> Unit) {
    when (val sel = ui.selected) {
        is EditorObject.TextObject -> {
            OutlinedTextField(
                value = sel.text, onValueChange = vm::onSelectedTextChanged,
                label = { Text(stringResource(R.string.edit_text_field_label)) }, modifier = Modifier.fillMaxWidth(),
            )
            SizeSlider(sel.fontSizePt, vm::onSelectedSizeChanged)
            ColorChips(sel.colorRgb, vm::onSelectedColorChanged)
            StyleToggles(
                sel.bold, sel.italic, sel.underline,
                vm::onSelectedBoldChanged, vm::onSelectedItalicChanged, vm::onSelectedUnderlineChanged,
            )
            RotationSlider(sel.rotationDeg, vm::onSelectedRotationChanged)
            UrlField(sel.url, vm::onSelectedUrlChanged)
            DecideDeleteRow(onCommit, vm::deleteSelected)
        }

        is EditorObject.EditObject -> {
            Text(
                stringResource(R.string.edit_original_text, sel.target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = sel.delete, onCheckedChange = vm::onSelectedDeleteChanged)
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
                    value = sel.replacement, onValueChange = vm::onReplacementChanged,
                    label = { Text(stringResource(R.string.edit_replacement_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                SizeSlider(sel.fontSizePt, vm::onSelectedSizeChanged)
                ColorChips(sel.colorRgb, vm::onSelectedColorChanged)
                StyleToggles(
                    sel.bold, sel.italic, sel.underline,
                    vm::onSelectedBoldChanged, vm::onSelectedItalicChanged, vm::onSelectedUnderlineChanged,
                )
                RotationSlider(sel.rotationDeg, vm::onSelectedRotationChanged)
                UrlField(sel.url, vm::onSelectedUrlChanged)
                Text(
                    stringResource(R.string.edit_replace_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DecideDeleteRow(onCommit, vm::deleteSelected)
        }

        else -> {
            Text(
                stringResource(R.string.edit_add_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = ui.textInput, onValueChange = vm::onTextInputChanged,
                label = { Text(stringResource(R.string.edit_add_field_label)) }, modifier = Modifier.fillMaxWidth(),
            )
            SizeSlider(ui.fontSizePt, vm::onFontSizeChanged)
            ColorChips(ui.colorRgb, vm::onColorChanged)
            StyleToggles(
                ui.bold, ui.italic, ui.underline,
                vm::onBoldChanged, vm::onItalicChanged, vm::onUnderlineChanged,
            )
            RotationSlider(ui.rotationDeg, vm::onRotationChanged)
            UrlField(ui.url, vm::onUrlChanged)
            Button(
                onClick = vm::addText,
                enabled = ui.source != null && ui.textInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null); Text("  " + stringResource(R.string.edit_add_button))
            }
        }
    }
}

@Composable
private fun ImagePanelContent(ui: EditUiState, vm: EditViewModel, onCommit: () -> Unit) {
    val sel = ui.selected as? EditorObject.ImageObject ?: return
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
        ScaleSlider(sel.scale, vm::onSelectedScaleChanged)
        RotationSlider(sel.rotationDeg, vm::onSelectedImageRotationChanged)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCommit, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Check, null); Text("  " + stringResource(R.string.edit_decide))
        }
        if (sel.annotationId != null && !sel.delete) {
            OutlinedButton(onClick = { vm.onSelectedDeleteChanged(true) }) { Text(stringResource(R.string.action_delete)) }
        }
        OutlinedButton(onClick = vm::deleteSelected) { Icon(Icons.Default.Close, null); Text(stringResource(R.string.edit_cancel)) }
    }
}

@Composable
private fun LayersPanelContent(ui: EditUiState, vm: EditViewModel, onCommit: () -> Unit) {
    // Every recognizable object on the page — added overlays, detected image layers and
    // not-yet-edited existing text — in one list, ordered top-to-bottom, selectable without a tap.
    val currentPage = ui.page - 1
    val pendingRuns = ui.runs.filterNot { run ->
        ui.objects.any {
            it is EditorObject.EditObject && it.pageIndex == currentPage &&
                it.target == run.text && it.occurrence == run.occurrence
        }
    }
    val entries = (
        ui.objects.map { LayerEntry.Obj(it) } +
            pendingRuns.map { LayerEntry.Run(it, currentPage) }
        ).sortedWith(compareBy({ it.pageIndex }, { it.top }))

    if (entries.isEmpty()) {
        Text(
            stringResource(R.string.edit_toolbox_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    entries.forEach { entry ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (entry) {
                is LayerEntry.Obj -> {
                    val obj = entry.obj
                    Text(
                        layerLabel(obj),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { vm.select(obj.id) }
                            .padding(vertical = 8.dp),
                        color = if (obj.id == ui.selectedId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (obj.isRemovable()) {
                        IconButton(onClick = { vm.removeObject(obj.id) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_cancel))
                        }
                    }
                }

                is LayerEntry.Run -> Text(
                    entry.run.text.replace('\n', ' ').take(20),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { vm.selectRun(entry.run) }
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    Button(onClick = onCommit, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Check, null); Text("  " + stringResource(R.string.edit_commit_button))
    }
}

@Composable
private fun FontPanelContent(ui: EditUiState, onDownload: () -> Unit) {
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

@Composable
private fun SavePanelContent(
    ui: EditUiState,
    loading: Boolean,
    onPickTree: () -> Unit,
    onRun: () -> Unit,
) {
    OutputFolderSection(folderName = ui.outputFolderName, onPick = onPickTree)
    PrimaryActionButton(
        text = stringResource(R.string.edit_run),
        enabled = ui.source != null && (ui.objects.isNotEmpty() || ui.committed),
        loading = loading,
        onClick = onRun,
    )
}

@Composable
private fun ResultPanelContent(result: ApplyEditsResult, onOpen: () -> Unit, onShare: () -> Unit) {
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

// ---------------------------------------------------------------------------
// Shared controls
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Canvas rendering
// ---------------------------------------------------------------------------

@Composable
private fun PageCanvas(
    bitmap: Bitmap,
    ui: EditUiState,
    onTap: (Float, Float) -> Unit,
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val pageIndex = ui.page - 1
    val badgeDelete = stringResource(R.string.edit_badge_delete)
    val badgeMove = stringResource(R.string.edit_badge_move)
    val badgeReplace = stringResource(R.string.edit_badge_replace)
    val badgeImage = stringResource(R.string.edit_badge_image)
    Box(
        modifier = modifier
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

/** Object-list entries: editor objects plus not-yet-edited existing text runs. */
private sealed interface LayerEntry {
    val pageIndex: Int
    val top: Float

    data class Obj(val obj: EditorObject) : LayerEntry {
        override val pageIndex get() = obj.pageIndex
        override val top get() = obj.rect.top
    }

    data class Run(val run: TextRun, override val pageIndex: Int) : LayerEntry {
        override val top get() = run.rect.top
    }
}

/** The list's × may drop new overlays / pending text edits, but not a detected image layer. */
private fun EditorObject.isRemovable(): Boolean = when (this) {
    is EditorObject.TextObject -> true
    is EditorObject.EditObject -> true
    is EditorObject.ImageObject -> annotationId == null
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
