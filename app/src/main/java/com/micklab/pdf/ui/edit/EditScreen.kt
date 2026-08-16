package com.micklab.pdf.ui.edit

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PanoramaFishEye
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.R
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.edit.AppFont
import com.micklab.pdf.domain.edit.ApplyEditsResult
import com.micklab.pdf.domain.edit.FractionPoint
import com.micklab.pdf.domain.edit.ShapeType
import com.micklab.pdf.domain.edit.scaledAboutCenter
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.fontLabel
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
private enum class Panel { None, Text, Image, Layers, Save, Shape, Draw, Canvas, NewDoc }

// Common page size presets in points (72pt = 1 inch)
private val PAGE_SIZE_PRESETS = listOf(
    "A4"     to Pair(595f, 842f),
    "A3"     to Pair(842f, 1190f),
    "A5"     to Pair(420f, 595f),
    "Letter" to Pair(612f, 792f),
)

@Composable
fun EditScreen(onBack: () -> Unit, viewModel: EditViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onSourcePicked(it, showEditWarning = true) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::addImage)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onOutputTreePicked(uri)
    }

    var panel by remember { mutableStateOf(Panel.None) }

    // Warn before leaving with unsaved edits (top-bar arrow and system back).
    var showBackWarning by remember { mutableStateOf(false) }
    val requestBack: () -> Unit = { if (ui.dirty) showBackWarning = true else onBack() }
    BackHandler(enabled = ui.dirty) { showBackWarning = true }

    // Sync draw mode with the active panel.
    // Panel.Shape opened by selecting an EXISTING shape must NOT re-enter draw mode;
    // only enter SHAPE mode when there is no shape currently selected (new drawing).
    LaunchedEffect(panel) {
        when (panel) {
            Panel.Shape -> if (ui.selected !is EditorObject.ShapeObject) viewModel.setDrawMode(DrawMode.SHAPE)
            Panel.Draw  -> viewModel.setDrawMode(DrawMode.BRUSH)
            else        -> viewModel.setDrawMode(DrawMode.NONE)
        }
    }

    // Open the panel when the user taps or selects from the layer list (not on drag).
    LaunchedEffect(ui.openPanelRevision) {
        when (ui.selected) {
            is EditorObject.TextObject, is EditorObject.EditObject -> panel = Panel.Text
            is EditorObject.ImageObject -> panel = Panel.Image
            is EditorObject.ShapeObject -> panel = Panel.Shape
            else -> Unit
        }
    }
    // After drawing a NEW shape, switch to the Layers panel so it's immediately visible.
    LaunchedEffect(ui.openLayersRevision) {
        if (ui.openLayersRevision > 0L) panel = Panel.Layers
    }
    // Close the panel only when selection is fully cleared.
    // isDragging intentionally does NOT close the panel; the panel stays open throughout
    // a drag so the rapid start→end cycle that detectDragGestures emits even on a pure
    // tap never causes a visible flash.
    LaunchedEffect(ui.selectedId) {
        if (ui.selectedId == null && (panel == Panel.Text || panel == Panel.Image || panel == Panel.Shape)) {
            panel = Panel.None
        }
    }

    if (ui.showEditWarning) {
        AlertDialog(
            onDismissRequest = viewModel::onEditWarningShown,
            confirmButton = {
                TextButton(onClick = viewModel::onEditWarningShown) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = { Text(stringResource(R.string.edit_text_change_warning)) },
        )
    }

    val commit: () -> Unit = { viewModel.commitPreview(); panel = Panel.None }
    // Cancel: discard an unmodified EditObject, or just deselect a modified/non-edit object.
    val cancelSelection: () -> Unit = { viewModel.cancelObject(); panel = Panel.None }
    // Delete: mark PDF content for deletion, or discard a new overlay object.
    val deleteSelection: () -> Unit = { viewModel.deleteSelected(); panel = Panel.None }

    // Panel offset: only recomputed when the panel was fully closed (Panel.None).
    // While the panel is already visible, the position is kept so users can drag it freely.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var panelOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(ui.openPanelRevision) {
        val sel = ui.selected
        // Compute position only when the panel is not yet visible and an object is selected.
        if (sel != null && panel == Panel.None) {
            val cx = (sel.rect.left + sel.rect.right) / 2f
            val cy = (sel.rect.top + sel.rect.bottom) / 2f
            val tx = if (cx < 0.5f) configuration.screenWidthDp * 0.28f else -configuration.screenWidthDp * 0.28f
            val ty = if (cy < 0.5f) configuration.screenHeightDp * 0.22f else -configuration.screenHeightDp * 0.22f
            panelOffset = with(density) { Offset(tx.dp.toPx(), ty.dp.toPx()) }
        }
    }

    if (showBackWarning) {
        AlertDialog(
            onDismissRequest = { showBackWarning = false },
            title = { Text(stringResource(R.string.edit_back_discard_title)) },
            text = { Text(stringResource(R.string.edit_back_discard_message)) },
            confirmButton = {
                TextButton(onClick = { showBackWarning = false; onBack() }) {
                    Text(stringResource(R.string.edit_back_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackWarning = false }) {
                    Text(stringResource(R.string.edit_cancel))
                }
            },
        )
    }

    ToolScaffold(title = stringResource(PdfDestination.EDIT.titleRes), onBack = requestBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (ui.source == null) {
                EmptyState(
                    onPick = { pickSource.launch(arrayOf("application/pdf")) },
                    onCreateBlank = viewModel::createBlank,
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
                                onMoveEnd = viewModel::onMoveEnd,
                                onDrawPoint = viewModel::onDrawPoint,
                                onDrawEnd = viewModel::onDrawEnd,
                                onCommit = commit,
                                onCancel = cancelSelection,
                                onDelete = deleteSelection,
                                showFloatingBar = panel == Panel.None || panel == Panel.Layers || panel == Panel.Draw,
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
                        onShape = { viewModel.deselect(); panel = Panel.Shape },
                        onDraw = { panel = Panel.Draw },
                        onCanvas = { panel = Panel.Canvas },
                        onSave = { panel = Panel.Save },
                        onUndo = viewModel::undo,
                    )
                }

                // --- floating windows ---
                // Shared action bar composable for panels that have a selected object.
                val objectActions: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = commit, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Text("  " + stringResource(R.string.edit_decide))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = deleteSelection, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                Text("  " + stringResource(R.string.action_delete))
                            }
                            TextButton(onClick = cancelSelection, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.edit_cancel))
                            }
                        }
                    }
                }

                when (panel) {
                    Panel.Text -> FloatingPanel(
                        title = stringResource(R.string.edit_text_section),
                        onClose = { viewModel.deselect(); panel = Panel.None },
                        initialOffset = panelOffset,
                        actions = if (ui.selected != null) objectActions else null,
                    ) { TextPanelContent(ui, viewModel, onCommit = commit) }

                    Panel.Image -> FloatingPanel(
                        title = stringResource(R.string.edit_image_section),
                        onClose = { viewModel.deselect(); panel = Panel.None },
                        initialOffset = panelOffset,
                        actions = if (ui.selected != null) objectActions else null,
                    ) { ImagePanelContent(ui, viewModel, onCommit = commit) }

                    Panel.Layers -> FloatingPanel(
                        title = stringResource(R.string.edit_tool_layers),
                        onClose = { viewModel.deselect(); panel = Panel.None },
                        actions = {
                            Button(onClick = commit, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                Text("  " + stringResource(R.string.edit_commit_button))
                            }
                        },
                    ) { LayersPanelContent(ui, viewModel) }

                    Panel.Shape -> FloatingPanel(
                        title = stringResource(R.string.edit_tool_shape),
                        onClose = { viewModel.deselect(); panel = Panel.None },
                        initialOffset = panelOffset,
                        actions = if (ui.selected is EditorObject.ShapeObject) objectActions else null,
                    ) { ShapePanelContent(ui, viewModel, onCommit = commit) }

                    Panel.Draw -> FloatingPanel(
                        title = stringResource(R.string.edit_tool_draw),
                        onClose = { panel = Panel.None },
                    ) { DrawPanelContent(ui, viewModel, onCommit = commit) }

                    Panel.Canvas -> FloatingPanel(
                        title = stringResource(R.string.edit_tool_canvas),
                        onClose = { panel = Panel.None },
                    ) { CanvasPanelContent(ui, viewModel) }

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

                    Panel.NewDoc -> Unit // handled by dialog below

                    Panel.None -> Unit
                }

                // Progress / error
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
                    OperationStatus(op)
                }

                // Result overlay
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

            // New document dialog (appears even before source is loaded)
            if (ui.newDocSettings != null) {
                NewDocDialog(
                    ui = ui,
                    onWidthChanged = viewModel::onNewDocWidthChanged,
                    onHeightChanged = viewModel::onNewDocHeightChanged,
                    onBgChanged = viewModel::onNewDocBgChanged,
                    onDismiss = viewModel::dismissNewDocDialog,
                    onCreate = viewModel::createBlankWithSettings,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fixed layout pieces
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(
    onPick: () -> Unit,
    onCreateBlank: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.edit_section_edit)) {
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_pick_pdf))
            }
        }
        SectionCard(title = stringResource(R.string.edit_section_new)) {
            OutlinedButton(onClick = onCreateBlank, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.edit_create_blank))
            }
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
    onMoveEnd: () -> Unit,
    onDrawPoint: (Float, Float) -> Unit,
    onDrawEnd: () -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    showFloatingBar: Boolean = true,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        val pageAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val boxAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else pageAspect
        val fillWidth = pageAspect >= boxAspect
        val sizeMod = if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxHeight()

        val pageW: androidx.compose.ui.unit.Dp = if (fillWidth) maxWidth else maxHeight * pageAspect
        val pageH: androidx.compose.ui.unit.Dp = if (fillWidth) maxWidth / pageAspect else maxHeight
        val pageTop: androidx.compose.ui.unit.Dp = ((maxHeight - pageH) / 2).coerceAtLeast(0.dp)

        PageCanvas(
            bitmap = bitmap,
            ui = ui,
            onTap = onTap,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onMoveEnd = onMoveEnd,
            onDrawPoint = onDrawPoint,
            onDrawEnd = onDrawEnd,
            modifier = sizeMod.aspectRatio(pageAspect),
        )

        // Floating action bar anchored near any selected object (including DrawingObject).
        // Only shown when no editing panel is open (the panel's objectActions handles those cases).
        val sel = ui.selected
        if (showFloatingBar && !ui.isDragging && sel != null) {
            val objTop: androidx.compose.ui.unit.Dp = pageTop + pageH * sel.rect.top
            val objBottom: androidx.compose.ui.unit.Dp = pageTop + pageH * sel.rect.bottom
            val objCenterY: androidx.compose.ui.unit.Dp = (objTop + objBottom) / 2
            val barHeight = 56.dp
            val barOffsetY: androidx.compose.ui.unit.Dp = if (objCenterY < maxHeight / 2)
                (objBottom + 6.dp).coerceIn(0.dp, maxHeight - barHeight)
            else
                (objTop - barHeight - 6.dp).coerceIn(0.dp, maxHeight - barHeight)

            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = barOffsetY),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cancel: close the panel without discarding the layer.
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.edit_cancel))
                    }
                    // Delete: remove the layer entirely.
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Text("  " + stringResource(R.string.action_delete))
                    }
                    if (sel !is EditorObject.DrawingObject) {
                        Button(onClick = onCommit) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Text("  " + stringResource(R.string.edit_decide))
                        }
                    }
                }
            }
        }
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
    onShape: () -> Unit,
    onDraw: () -> Unit,
    onCanvas: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
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
                ToolButton(Icons.Default.Square, stringResource(R.string.edit_tool_shape), onShape, active = ui.drawMode == DrawMode.SHAPE)
                ToolButton(Icons.Default.Brush, stringResource(R.string.edit_tool_draw), onDraw, active = ui.drawMode == DrawMode.BRUSH)
                ToolButton(Icons.Default.FormatColorFill, stringResource(R.string.edit_tool_canvas), onCanvas)
                ToolButton(
                    Icons.Default.Layers,
                    if (layerCount > 0) stringResource(R.string.edit_layers_title, layerCount) else stringResource(R.string.edit_tool_layers),
                    onLayers,
                )
                ToolButton(Icons.Default.Undo, stringResource(R.string.edit_undo), onUndo, enabled = ui.canUndo)
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
    active: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        active   -> MaterialTheme.colorScheme.primary
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
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
    initialOffset: Offset = Offset.Zero,
    // Optional action row placed at the top of the content (above controls).
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var dragOffset by remember { mutableStateOf(initialOffset) }
    LaunchedEffect(initialOffset) { dragOffset = initialOffset }
    // Start collapsed so a newly-appearing toolbar doesn't cover the canvas immediately.
    var collapsed by remember { mutableStateOf(true) }
    var cardHeightPx by remember { mutableStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Clamp upward drag so the panel never goes above the content area top edge.
        val density = LocalDensity.current
        val minDragY = with(density) { -maxHeight.toPx() / 2f + cardHeightPx / 2f + 8.dp.toPx() }
        ElevatedCard(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .padding(12.dp)
                .widthIn(min = 200.dp, max = 240.dp)
                .heightIn(max = 480.dp)
                .onSizeChanged { cardHeightPx = it.height },
        ) {
            // Title row — drag anywhere on it to move; tap title text to collapse/expand.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            dragOffset = Offset(
                                dragOffset.x + delta.x,
                                (dragOffset.y + delta.y).coerceAtLeast(minDragY),
                            )
                        }
                    }
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f).clickable { collapsed = !collapsed },
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { collapsed = !collapsed }) {
                    Icon(
                        if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_close))
                }
            }
            // Action buttons (決定 / 削除 / 取消) — always visible, even when collapsed.
            if (actions != null) {
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    actions()
                }
            }
            // Panel content — hidden when collapsed.
            AnimatedVisibility(visible = !collapsed) {
                Column {
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
            FontRow(ui, sel.fontId, onSelect = vm::onSelectedFontChanged)
            RotationSlider(sel.rotationDeg, vm::onSelectedRotationChanged)
            UrlField(sel.url, vm::onSelectedUrlChanged)
        }

        is EditorObject.EditObject -> {
            if (sel.delete) {
                Text(
                    stringResource(R.string.edit_text_delete_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = { vm.onSelectedDeleteChanged(false) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.edit_text_delete_cancel))
                }
            } else {
                Text(
                    stringResource(R.string.edit_original_text, sel.target),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                FontRow(ui, sel.fontId, onSelect = vm::onSelectedFontChanged)
                RotationSlider(sel.rotationDeg, vm::onSelectedRotationChanged)
                UrlField(sel.url, vm::onSelectedUrlChanged)
                Text(
                    stringResource(R.string.edit_replace_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            Button(
                onClick = vm::addText,
                enabled = ui.source != null && ui.textInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null); Text("  " + stringResource(R.string.edit_add_button))
            }
            SizeSlider(ui.fontSizePt, vm::onFontSizeChanged)
            ColorChips(ui.colorRgb, vm::onColorChanged)
            StyleToggles(
                ui.bold, ui.italic, ui.underline,
                vm::onBoldChanged, vm::onItalicChanged, vm::onUnderlineChanged,
            )
            FontRow(ui, ui.selectedFontId, onSelect = vm::onFontSelected)
            RotationSlider(ui.rotationDeg, vm::onRotationChanged)
            UrlField(ui.url, vm::onUrlChanged)
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
    // Mark annotation layer for deletion (existing PDF image, requires commit to apply).
    if (sel.annotationId != null && !sel.delete) {
        OutlinedButton(onClick = { vm.onSelectedDeleteChanged(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_delete) + " (PDF から削除)")
        }
    }
}

@Composable
private fun ShapePanelContent(ui: EditUiState, vm: EditViewModel, onCommit: () -> Unit) {
    val sel = ui.selected as? EditorObject.ShapeObject

    // When a shape is selected, edit it; otherwise show new-shape config.
    val shapeType = sel?.shapeType ?: ui.shapeType
    val strokeColor = sel?.strokeColorRgb ?: ui.shapeStrokeColorRgb
    val fillColor = sel?.fillColorRgb ?: ui.shapeFillColorRgb
    val strokeWidth = sel?.strokeWidthPt ?: ui.shapeStrokeWidthPt

    val onTypeChange: (ShapeType) -> Unit = { t ->
        if (sel != null) vm.onSelectedShapeTypeChanged(t) else vm.onShapeTypeChanged(t)
    }
    val onStrokeColorChange: (Int) -> Unit = { c ->
        if (sel != null) vm.onSelectedShapeStrokeColorChanged(c) else vm.onShapeStrokeColorChanged(c)
    }
    val onFillColorChange: (Int?) -> Unit = { c ->
        if (sel != null) vm.onSelectedShapeFillColorChanged(c) else vm.onShapeFillColorChanged(c)
    }
    val onStrokeWidthChange: (Float) -> Unit = { w ->
        if (sel != null) vm.onSelectedShapeStrokeWidthChanged(w) else vm.onShapeStrokeWidthChanged(w)
    }

    Text(
        if (sel != null) stringResource(R.string.edit_shape_selected)
        else stringResource(R.string.edit_shape_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val shapeRectLabel = stringResource(R.string.edit_shape_rect)
    val shapeOvalLabel = stringResource(R.string.edit_shape_oval)
    ChoiceChipsRow(
        label = stringResource(R.string.edit_shape_type),
        options = ShapeType.entries,
        selected = shapeType,
        optionLabel = { if (it == ShapeType.RECT) shapeRectLabel else shapeOvalLabel },
        onSelect = onTypeChange,
    )

    // Stroke color
    Text(stringResource(R.string.edit_shape_stroke_color), style = MaterialTheme.typography.labelLarge)
    ColorChips(strokeColor, onStrokeColorChange)

    // Fill color (with "none" toggle)
    Text(stringResource(R.string.edit_shape_fill_color), style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = fillColor != null, onCheckedChange = { on ->
            onFillColorChange(if (on) 0xFFFFFF else null)
        })
        Text("  " + if (fillColor != null) stringResource(R.string.edit_shape_fill_on) else stringResource(R.string.edit_shape_no_fill),
            style = MaterialTheme.typography.bodyMedium)
    }
    if (fillColor != null) {
        ColorChips(fillColor, { onFillColorChange(it) })
    }

    // Stroke width
    Text(stringResource(R.string.edit_shape_stroke_width, strokeWidth), style = MaterialTheme.typography.bodyMedium)
    Slider(value = strokeWidth, onValueChange = onStrokeWidthChange, valueRange = 0.5f..20f)

}

@Composable
private fun DrawPanelContent(ui: EditUiState, vm: EditViewModel, onCommit: () -> Unit) {
    Text(
        stringResource(R.string.edit_draw_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.edit_draw_color), style = MaterialTheme.typography.labelLarge)
    ColorChips(ui.brushColorRgb, vm::onBrushColorChanged)
    Text(stringResource(R.string.edit_draw_width, ui.brushWidthPt), style = MaterialTheme.typography.bodyMedium)
    Slider(value = ui.brushWidthPt, onValueChange = vm::onBrushWidthChanged, valueRange = 1f..30f)
}

@Composable
private fun CanvasPanelContent(ui: EditUiState, vm: EditViewModel) {
    Text(
        stringResource(R.string.edit_canvas_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.edit_canvas_bg_color), style = MaterialTheme.typography.labelLarge)
    ColorChips(ui.canvasBgRgb, vm::onCanvasBgChanged)
    Button(onClick = vm::applyBackground, enabled = ui.source != null, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.FormatColorFill, null)
        Text("  " + stringResource(R.string.edit_canvas_apply))
    }
}

@Composable
private fun LayersPanelContent(ui: EditUiState, vm: EditViewModel) {
    val currentPage = ui.page - 1
    // Overlay layers on this page, front-most first (later in the list = drawn on top).
    val pageObjects = ui.objects.filter { it.pageIndex == currentPage }.reversed()
    val pendingRuns = ui.runs.filterNot { run ->
        ui.objects.any {
            it is EditorObject.EditObject && it.pageIndex == currentPage &&
                it.target == run.text && it.occurrence == run.occurrence
        }
    }

    if (pageObjects.isEmpty() && pendingRuns.isEmpty()) {
        Text(
            stringResource(R.string.edit_toolbox_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    if (pageObjects.isNotEmpty()) {
        Text(
            stringResource(R.string.edit_layers_order_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Reorderable overlay layers (tap label to edit; arrows change stacking order).
    pageObjects.forEach { obj ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                layerLabel(obj),
                modifier = Modifier
                    .weight(1f)
                    .clickable { vm.select(obj.id) }
                    .padding(vertical = 8.dp),
                color = if (obj.id == ui.selectedId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = { vm.moveLayer(obj.id, toFront = true) }, enabled = vm.canMoveToFront(obj.id)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.edit_layer_to_front))
            }
            IconButton(onClick = { vm.moveLayer(obj.id, toFront = false) }, enabled = vm.canMoveToBack(obj.id)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.edit_layer_to_back))
            }
            if (obj.isRemovable()) {
                IconButton(onClick = { vm.removeObject(obj.id) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_cancel))
                }
            }
        }
    }

    // Existing text runs on this page (baked content, below all overlays; tap to edit).
    if (pendingRuns.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        pendingRuns.forEach { run ->
            Text(
                run.text.replace('\n', ' ').take(20),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.selectRun(run) }
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Inline font chooser for the text-compose controls. Selecting a font downloads it on demand. */
@Composable
private fun FontRow(
    ui: EditUiState,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Text(stringResource(R.string.edit_font_label), style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppFont.entries.forEach { font ->
            val available = font.id in ui.availableFontIds
            FilterChip(
                selected = font.id == selectedId,
                onClick = { onSelect(font.id) },
                label = { Text(fontLabel(font)) },
                trailingIcon = if (!available) {
                    { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
    if (ui.downloadingFontId != null) {
        LinearProgressIndicator(progress = { ui.fontProgress }, modifier = Modifier.fillMaxWidth())
    }
    if (ui.fontError.isNotBlank()) {
        Text(ui.fontError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
// New document dialog
// ---------------------------------------------------------------------------

@Composable
private fun NewDocDialog(
    ui: EditUiState,
    onWidthChanged: (Float) -> Unit,
    onHeightChanged: (Float) -> Unit,
    onBgChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val settings = ui.newDocSettings ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_new_doc_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Size presets
                Text(stringResource(R.string.edit_new_doc_size), style = MaterialTheme.typography.labelLarge)
                // Screen size preset
                FilterChip(
                    selected = settings.widthPt == ui.screenWidthPt && settings.heightPt == ui.screenHeightPt,
                    onClick = { onWidthChanged(ui.screenWidthPt); onHeightChanged(ui.screenHeightPt) },
                    label = { Text(stringResource(R.string.edit_new_doc_screen)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PAGE_SIZE_PRESETS.forEach { (name, size) ->
                        FilterChip(
                            selected = settings.widthPt == size.first && settings.heightPt == size.second,
                            onClick = { onWidthChanged(size.first); onHeightChanged(size.second) },
                            label = { Text(name) },
                        )
                    }
                }
                // Width / height display
                Text(
                    "${settings.widthPt.toInt()} × ${settings.heightPt.toInt()} pt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Background color
                Text(stringResource(R.string.edit_new_doc_bg), style = MaterialTheme.typography.labelLarge)
                ColorChips(settings.backgroundRgb, onBgChanged)
            }
        },
        confirmButton = {
            Button(onClick = onCreate) { Text(stringResource(R.string.edit_new_doc_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) }
        },
    )
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
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TEXT_COLOR_RGBS.forEach { rgb ->
            val isSelected = rgb == selected
            val color = Color(0xFF000000.toLong() or rgb.toLong())
            Box(
                modifier = Modifier
                    .size(if (isSelected) 36.dp else 30.dp)
                    .background(color, CircleShape)
                    .then(
                        if (isSelected) Modifier.padding(2.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(rgb) },
            )
        }
    }
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
    onMoveEnd: () -> Unit,
    onDrawPoint: (Float, Float) -> Unit,
    onDrawEnd: () -> Unit,
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
            .pointerInput(ui.page, ui.source, ui.drawMode) {
                var absX = 0f
                var absY = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        absX = offset.x
                        absY = offset.y
                        val fx = offset.x / size.width
                        val fy = offset.y / size.height
                        onDragStart(fx, fy)
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        absX += delta.x
                        absY += delta.y
                        if (ui.drawMode != DrawMode.NONE) {
                            onDrawPoint(absX / size.width, absY / size.height)
                        } else {
                            onDrag(delta.x / size.width, delta.y / size.height)
                        }
                    },
                    onDragEnd = { if (ui.drawMode != DrawMode.NONE) onDrawEnd() else onMoveEnd() },
                    onDragCancel = { if (ui.drawMode != DrawMode.NONE) onDrawEnd() else onMoveEnd() },
                )
            },
    ) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxPerPoint = if (ui.pageWidthPt > 0f) size.width / ui.pageWidthPt else 0f
            val imagePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
            val native = drawContext.canvas.nativeCanvas
            ui.objects.filter { it.pageIndex == pageIndex }.forEach { obj ->
                when (obj) {
                    is EditorObject.TextObject -> {
                        val effRect = obj.rect
                        val left = effRect.left * size.width
                        val top = effRect.top * size.height
                        val w = (effRect.right - effRect.left) * size.width
                        val h = (effRect.bottom - effRect.top) * size.height
                        val rotated = obj.rotationDeg % 360 != 0
                        if (rotated) {
                            native.save()
                            native.rotate(obj.rotationDeg.toFloat(), left + w / 2f, top + h / 2f)
                        }
                        drawLines(native, obj.text, left, top, pxPerPoint * obj.fontSizePt, obj.colorRgb, obj.bold, obj.italic, obj.underline)
                        if (rotated) native.restore()
                        drawObjectBorder(this, effRect, obj.id == ui.selectedId, false)
                    }
                    is EditorObject.EditObject -> {
                        val effRect = obj.rect
                        val left = effRect.left * size.width
                        val top = effRect.top * size.height
                        val deleteMode = obj.delete
                        native.drawText(
                            when { obj.delete -> badgeDelete; obj.moved -> badgeMove; else -> badgeReplace }, left + 6f, top + 34f,
                            Paint().apply {
                                color = if (obj.delete) 0xFFD32F2F.toInt() else 0xFF3F51B5.toInt()
                                textSize = 30f
                                isAntiAlias = true
                            },
                        )
                        drawObjectBorder(this, effRect, obj.id == ui.selectedId, deleteMode)
                    }
                    is EditorObject.ImageObject -> {
                        val effRect = obj.rect.scaledAboutCenter(obj.scale)
                        val left = effRect.left * size.width
                        val top = effRect.top * size.height
                        val w = (effRect.right - effRect.left) * size.width
                        val h = (effRect.bottom - effRect.top) * size.height
                        val deleteMode = obj.delete
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
                            obj.annotationId != null -> Unit
                            else -> native.drawText(
                                badgeImage, left + 6f, top + h / 2f,
                                Paint().apply { color = 0xFF555555.toInt(); textSize = 28f; isAntiAlias = true },
                            )
                        }
                        drawObjectBorder(this, effRect, obj.id == ui.selectedId, deleteMode)
                    }
                    is EditorObject.ShapeObject -> {
                        val r = obj.rect
                        val left = r.left * size.width
                        val top = r.top * size.height
                        val w = (r.right - r.left) * size.width
                        val h = (r.bottom - r.top) * size.height
                        val strokeW = (obj.strokeWidthPt * pxPerPoint).coerceAtLeast(1f)
                        val strokeColor = Color(0xFF000000.toLong() or obj.strokeColorRgb.toLong())
                        val fillColor = obj.fillColorRgb?.let { Color(0xFF000000.toLong() or it.toLong()) }
                        when (obj.shapeType) {
                            ShapeType.RECT -> {
                                if (fillColor != null) drawRect(fillColor, Offset(left, top), Size(w, h))
                                drawRect(strokeColor, Offset(left, top), Size(w, h), style = Stroke(strokeW))
                            }
                            ShapeType.OVAL -> {
                                if (fillColor != null) drawOval(fillColor, Offset(left, top), Size(w, h))
                                drawOval(strokeColor, Offset(left, top), Size(w, h), style = Stroke(strokeW))
                            }
                        }
                        drawObjectBorder(this, r, obj.id == ui.selectedId, false)
                    }
                    is EditorObject.DrawingObject -> {
                        val color = Color(0xFF000000.toLong() or obj.colorRgb.toLong())
                        val strokeW = (obj.strokeWidthPt * pxPerPoint).coerceAtLeast(1f)
                        if (obj.points.size >= 2) {
                            val path = Path()
                            obj.points.forEachIndexed { i, fp ->
                                val x = fp.x * size.width
                                val y = fp.y * size.height
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color, style = Stroke(strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                        // Drawings aren't tap-selectable; show a border only when picked from the layers list.
                        if (obj.id == ui.selectedId) drawObjectBorder(this, obj.rect, true, false)
                    }
                }
            }
            // Preview the in-progress stroke/shape while dragging.
            if (ui.drawMode != DrawMode.NONE && ui.currentStroke.size >= 2) {
                drawCurrentStroke(ui)
            }
        }
    }
}

private fun DrawScope.drawCurrentStroke(ui: EditUiState) {
    val stroke = ui.currentStroke
    when (ui.drawMode) {
        DrawMode.SHAPE -> {
            val p0 = stroke.first(); val p1 = stroke.last()
            val left = minOf(p0.x, p1.x) * size.width
            val top = minOf(p0.y, p1.y) * size.height
            val w = kotlin.math.abs(p1.x - p0.x) * size.width
            val h = kotlin.math.abs(p1.y - p0.y) * size.height
            val color = Color(0xFF000000.toLong() or ui.shapeStrokeColorRgb.toLong())
            when (ui.shapeType) {
                ShapeType.RECT -> drawRect(color, Offset(left, top), Size(w, h), style = Stroke(2f))
                ShapeType.OVAL -> drawOval(color, Offset(left, top), Size(w, h), style = Stroke(2f))
            }
        }
        DrawMode.BRUSH -> {
            val color = Color(0xFF000000.toLong() or ui.brushColorRgb.toLong())
            val pxPerPoint = if (ui.pageWidthPt > 0f) size.width / ui.pageWidthPt else 0f
            val strokeW = (ui.brushWidthPt * pxPerPoint).coerceAtLeast(1f)
            val path = Path()
            stroke.forEachIndexed { i, fp ->
                val x = fp.x * size.width; val y = fp.y * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        DrawMode.NONE -> Unit
    }
}

private fun drawObjectBorder(scope: DrawScope, rect: com.micklab.pdf.domain.edit.FractionRect, selected: Boolean, deleteMode: Boolean) {
    val left = rect.left * scope.size.width
    val top = rect.top * scope.size.height
    val w = (rect.right - rect.left) * scope.size.width
    val h = (rect.bottom - rect.top) * scope.size.height
    val base = if (deleteMode) Color(0xFFD32F2F) else Color(0xFF3F51B5)
    scope.drawRect(
        color = base.copy(alpha = if (selected) 1f else 0.5f),
        topLeft = Offset(left, top),
        size = Size(w, h),
        style = Stroke(width = if (selected) 5f else 2f),
    )
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

/** Drawings are removable from the list but not by the ×-on-canvas flow. */
private fun EditorObject.isRemovable(): Boolean = when (this) {
    is EditorObject.TextObject -> true
    is EditorObject.EditObject -> true
    is EditorObject.ImageObject -> annotationId == null
    is EditorObject.ShapeObject -> true
    is EditorObject.DrawingObject -> true
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
    is EditorObject.ShapeObject -> "${stringResource(R.string.edit_badge_shape)}｜${if (obj.shapeType == ShapeType.RECT) stringResource(R.string.edit_shape_rect) else stringResource(R.string.edit_shape_oval)}"
    is EditorObject.DrawingObject -> stringResource(R.string.edit_badge_draw)
}
