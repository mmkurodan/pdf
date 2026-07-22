package com.micklab.pdf.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.R
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.edit.ApplyEditsResult
import com.micklab.pdf.domain.edit.ApplyEditsUseCase
import com.micklab.pdf.domain.edit.CreateBlankPdfUseCase
import com.micklab.pdf.domain.edit.EditOp
import com.micklab.pdf.domain.edit.FractionRect
import com.micklab.pdf.domain.edit.AppFont
import com.micklab.pdf.domain.edit.FontManager
import com.micklab.pdf.domain.edit.scaledAboutCenter
import com.micklab.pdf.domain.edit.PdfTextLayer
import com.micklab.pdf.domain.edit.TextRun
import com.micklab.pdf.domain.pdf.PdfThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

/** A draggable editor object placed on the page and edited before it is applied. */
sealed interface EditorObject {
    val id: Long
    val pageIndex: Int
    val rect: FractionRect

    data class TextObject(
        override val id: Long, override val pageIndex: Int, override val rect: FractionRect,
        val text: String, val fontSizePt: Float, val colorRgb: Int,
        val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false,
        val rotationDeg: Int = 0,
        val url: String = "",
        val fontId: String = AppFont.DEFAULT.id,
    ) : EditorObject

    data class ImageObject(
        override val id: Long, override val pageIndex: Int, override val rect: FractionRect,
        val uri: Uri, val name: String, val thumbnail: Bitmap? = null,
        // Non-null once it's an existing PDF annotation layer (movable/deletable after save).
        val annotationId: String? = null, val delete: Boolean = false, val moved: Boolean = false,
        val scale: Float = 1f,
        val rotationDeg: Int = 0,
        // The detected position of an existing layer, so 取消 can revert edits without dropping it.
        val baseRect: FractionRect? = null,
    ) : EditorObject

    /** Editing an existing text-layer run: [target] is what's on the page, [replacement] the new text. */
    data class EditObject(
        override val id: Long, override val pageIndex: Int, override val rect: FractionRect,
        val target: String, val replacement: String, val fontSizePt: Float, val colorRgb: Int = 0x000000,
        val delete: Boolean = false, val occurrence: Int = 0, val moved: Boolean = false,
        val restyled: Boolean = false,
        val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false,
        val rotationDeg: Int = 0,
        val url: String = "",
        val fontId: String = AppFont.DEFAULT.id,
    ) : EditorObject
}

data class EditUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val page: Int = 1,                       // 1-based, the page being previewed/edited
    val previewBitmap: Bitmap? = null,
    val pageWidthPt: Float = 0f,             // for WYSIWYG overlay scaling
    val pageHeightPt: Float = 0f,
    val objects: List<EditorObject> = emptyList(),
    // Existing text runs on the current page, so the object list can offer them without a tap.
    val runs: List<TextRun> = emptyList(),
    val selectedId: Long? = null,
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
    val availableFontIds: Set<String> = emptySet(),
    val selectedFontId: String = AppFont.DEFAULT.id,
    val downloadingFontId: String? = null,
    val fontProgress: Float = 0f,
    val fontError: String = "",
    // Controls for creating new text.
    val textInput: String = "",
    val fontSizePt: Float = 14f,
    val colorRgb: Int = 0x000000,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val rotationDeg: Int = 0,
    val url: String = "",
    // At least one 決定 has baked edits into the working PDF (so "save" is meaningful even with no pending layers).
    val committed: Boolean = false,
) {
    val selected: EditorObject? get() = objects.firstOrNull { it.id == selectedId }
}

@HiltViewModel
class EditViewModel @Inject constructor(
    private val applyEdits: ApplyEditsUseCase,
    private val createBlankPdf: CreateBlankPdfUseCase,
    private val fontManager: FontManager,
    private val thumbnailLoader: PdfThumbnailLoader,
    private val textLayer: PdfTextLayer,
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<ApplyEditsResult>>(OperationState.Idle)
    val operation: StateFlow<OperationState<ApplyEditsResult>> = _operation.asStateFlow()

    private var nextId = 0L
    @Volatile private var currentRuns: List<TextRun> = emptyList()

    // The PDF currently shown/edited. Starts as the picked file; each 決定 bakes the
    // pending edits into a temp PDF that becomes the new working source (real render).
    private var workingSource: Uri? = null

    init {
        refreshFonts()
    }

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update {
            EditUiState(
                source = uri, sourceName = fileRepository.displayName(uri),
                availableFontIds = it.availableFontIds, selectedFontId = it.selectedFontId,
                outputTree = it.outputTree, outputFolderName = it.outputFolderName,
            )
        }
        _operation.value = OperationState.Idle
        currentRuns = emptyList()
        workingSource = uri
        viewModelScope.launch {
            val count = thumbnailLoader.open(uri)
            textLayer.open(uri)
            _uiState.update { it.copy(pageCount = count) }
            loadPage(0)
        }
    }

    /** Start a new document from a blank white A4 PDF, then open it in the editor. */
    fun createBlank() {
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.vm_edit_creating_blank))
            runCatching { createBlankPdf() }
                .onSuccess {
                    _operation.value = OperationState.Idle
                    onSourcePicked(it.uri)
                }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.vm_edit_blank_failed)) }
        }
    }

    fun onOutputTreePicked(uri: Uri?) {
        if (uri != null) fileRepository.persistTreePermission(uri)
        _uiState.update {
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::treeDisplayName).orEmpty())
        }
    }

    fun onPageChanged(page: Int) {
        val count = _uiState.value.pageCount
        val clamped = if (count > 0) page.coerceIn(1, count) else page.coerceAtLeast(1)
        if (clamped == _uiState.value.page && _uiState.value.previewBitmap != null) return
        _uiState.update { it.copy(page = clamped, selectedId = null) }
        if (_uiState.value.source != null) loadPage(clamped - 1)
    }

    fun prevPage() = onPageChanged(_uiState.value.page - 1)
    fun nextPage() = onPageChanged(_uiState.value.page + 1)

    private fun loadPage(pageIndex: Int) {
        currentRuns = emptyList()
        _uiState.update { it.copy(runs = emptyList()) }
        viewModelScope.launch {
            val bitmap = thumbnailLoader.render(pageIndex, PREVIEW_WIDTH_PX)
            val size = thumbnailLoader.pageSizePoints(pageIndex)
            val layers = textLayer.imageLayers(pageIndex)
            _uiState.update { state ->
                // Add only newly-seen annotation layers, so pending moves/deletes aren't reset.
                val knownIds = state.objects.mapNotNull { (it as? EditorObject.ImageObject)?.annotationId }.toSet()
                val detected = layers.filterNot { it.id in knownIds }.map {
                    EditorObject.ImageObject(nextId++, pageIndex, it.rect, Uri.EMPTY, LocaleManager.string(appContext, R.string.vm_edit_image_layer_name), annotationId = it.id, baseRect = it.rect)
                }
                state.copy(
                    previewBitmap = bitmap,
                    pageWidthPt = size?.first ?: 0f,
                    pageHeightPt = size?.second ?: 0f,
                    objects = state.objects + detected,
                )
            }
            val loaded = textLayer.runs(pageIndex)
            currentRuns = loaded
            _uiState.update { it.copy(runs = loaded) }
        }
    }

    // --- creating new text / images ---

    fun onTextInputChanged(text: String) = _uiState.update { it.copy(textInput = text) }
    fun onFontSizeChanged(size: Float) = _uiState.update { it.copy(fontSizePt = size) }
    fun onColorChanged(rgb: Int) = _uiState.update { it.copy(colorRgb = rgb) }
    fun onBoldChanged(on: Boolean) = _uiState.update { it.copy(bold = on) }
    fun onItalicChanged(on: Boolean) = _uiState.update { it.copy(italic = on) }
    fun onUnderlineChanged(on: Boolean) = _uiState.update { it.copy(underline = on) }
    fun onRotationChanged(deg: Int) = _uiState.update { it.copy(rotationDeg = ((deg % 360) + 360) % 360) }
    fun onUrlChanged(url: String) = _uiState.update { it.copy(url = url) }

    fun addText() {
        val s = _uiState.value
        val text = s.textInput.trim()
        if (text.isEmpty() || s.source == null) return
        val h = if (s.pageHeightPt > 0f) (s.fontSizePt * 1.6f / s.pageHeightPt).coerceIn(0.03f, 0.4f) else 0.08f
        val obj = EditorObject.TextObject(
            nextId++, s.page - 1, centeredRect(0.5f, h), text, s.fontSizePt, s.colorRgb,
            bold = s.bold, italic = s.italic, underline = s.underline, rotationDeg = s.rotationDeg, url = s.url,
            fontId = s.selectedFontId,
        )
        _uiState.update { it.copy(objects = it.objects + obj, selectedId = obj.id, textInput = "") }
    }

    fun addImage(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        val s = _uiState.value
        if (s.source == null) return
        val id = nextId++
        val obj = EditorObject.ImageObject(id, s.page - 1, centeredRect(0.4f, 0.4f), uri, fileRepository.displayName(uri))
        _uiState.update { it.copy(objects = it.objects + obj, selectedId = id) }
        // Decode a small preview so the image (not just a box) shows on the page.
        viewModelScope.launch {
            val thumb = withContext(dispatchers.io) { decodeThumbnail(uri) } ?: return@launch
            _uiState.update { state ->
                state.copy(
                    objects = state.objects.map {
                        if (it is EditorObject.ImageObject && it.id == id) it.copy(thumbnail = thumb) else it
                    },
                )
            }
        }
    }

    private fun decodeThumbnail(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        fileRepository.openInput(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > THUMB_MAX_PX || bounds.outHeight / sample > THUMB_MAX_PX) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return fileRepository.openInput(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }

    // --- canvas gestures ---

    /** Tap: select an object under the point, else pick up the text-layer run there for editing. */
    fun onCanvasTap(fx: Float, fy: Float) {
        val hit = objectAt(fx, fy)
        if (hit != null) {
            _uiState.update { it.copy(selectedId = hit.id) }
            return
        }
        val run = currentRuns.firstOrNull { it.rect.contains(fx, fy) }
        if (run != null) {
            val s = _uiState.value
            val obj = EditorObject.EditObject(
                nextId++, s.page - 1, run.rect, run.text, run.text, run.fontSizePt,
                colorRgb = run.colorRgb, occurrence = run.occurrence,
            )
            _uiState.update { it.copy(objects = it.objects + obj, selectedId = obj.id) }
        } else {
            _uiState.update { it.copy(selectedId = null) }
        }
    }

    fun onDragStart(fx: Float, fy: Float) {
        objectAt(fx, fy)?.let { hit -> _uiState.update { it.copy(selectedId = hit.id) } }
    }

    fun onDrag(dxFrac: Float, dyFrac: Float) {
        val id = _uiState.value.selectedId ?: return
        _uiState.update { state ->
            state.copy(
                objects = state.objects.map {
                    if (it.id != id) return@map it
                    val moved = it.rect.shifted(dxFrac, dyFrac)
                    when (it) {
                        is EditorObject.TextObject -> it.copy(rect = moved)
                        is EditorObject.ImageObject -> it.copy(rect = moved, moved = true)
                        // Dragging an existing run means "move it": regenerate at the new spot.
                        is EditorObject.EditObject -> it.copy(rect = moved, moved = true)
                    }
                },
            )
        }
    }

    private fun objectAt(fx: Float, fy: Float): EditorObject? {
        val pageIndex = _uiState.value.page - 1
        return _uiState.value.objects.lastOrNull { it.pageIndex == pageIndex && hitTest(it, fx, fy) }
    }

    /** Rotation-aware hit test: undo a text object's visual rotation about its centre before testing its box. */
    private fun hitTest(obj: EditorObject, fx: Float, fy: Float): Boolean {
        val rot = when (obj) {
            is EditorObject.TextObject -> obj.rotationDeg
            is EditorObject.EditObject -> obj.rotationDeg
            else -> 0
        }
        if (rot % 360 == 0) return obj.rect.contains(fx, fy)
        val w = _uiState.value.pageWidthPt.takeIf { it > 0f } ?: 1f
        val h = _uiState.value.pageHeightPt.takeIf { it > 0f } ?: 1f
        val cx = (obj.rect.left + obj.rect.right) / 2f
        val cy = (obj.rect.top + obj.rect.bottom) / 2f
        val rad = Math.toRadians(rot.toDouble())
        val cosr = cos(rad).toFloat()
        val sinr = sin(rad).toFloat()
        // Undo the clockwise (screen y-down) draw rotation: rotate the tap point by -rot about the centre.
        val dx = (fx - cx) * w
        val dy = (fy - cy) * h
        val ux = dx * cosr + dy * sinr
        val uy = -dx * sinr + dy * cosr
        return obj.rect.contains(cx + ux / w, cy + uy / h)
    }

    // --- selected-object editing ---

    fun onSelectedTextChanged(text: String) = updateSelected { if (it is EditorObject.TextObject) it.copy(text = text) else it }
    fun onSelectedSizeChanged(size: Float) = updateSelected {
        when (it) {
            is EditorObject.TextObject -> it.copy(fontSizePt = size)
            is EditorObject.EditObject -> it.copy(fontSizePt = size, restyled = true)
            else -> it
        }
    }
    fun onSelectedColorChanged(rgb: Int) = updateSelected {
        when (it) {
            is EditorObject.TextObject -> it.copy(colorRgb = rgb)
            is EditorObject.EditObject -> it.copy(colorRgb = rgb, restyled = true)
            else -> it
        }
    }
    fun onSelectedBoldChanged(on: Boolean) = updateSelected {
        when (it) {
            is EditorObject.TextObject -> it.copy(bold = on)
            is EditorObject.EditObject -> it.copy(bold = on, restyled = true)
            else -> it
        }
    }
    fun onSelectedItalicChanged(on: Boolean) = updateSelected {
        when (it) {
            is EditorObject.TextObject -> it.copy(italic = on)
            is EditorObject.EditObject -> it.copy(italic = on, restyled = true)
            else -> it
        }
    }
    fun onSelectedUnderlineChanged(on: Boolean) = updateSelected {
        when (it) {
            is EditorObject.TextObject -> it.copy(underline = on)
            is EditorObject.EditObject -> it.copy(underline = on, restyled = true)
            else -> it
        }
    }
    fun onSelectedRotationChanged(deg: Int) = updateSelected {
        val norm = ((deg % 360) + 360) % 360
        when (it) {
            is EditorObject.TextObject -> it.copy(rotationDeg = norm)
            is EditorObject.EditObject -> it.copy(rotationDeg = norm, restyled = true)
            else -> it
        }
    }
    fun onSelectedUrlChanged(url: String) = updateSelected {
        when (it) {
            is EditorObject.TextObject -> it.copy(url = url)
            is EditorObject.EditObject -> it.copy(url = url, restyled = true)
            else -> it
        }
    }
    fun onSelectedScaleChanged(scale: Float) = updateSelected {
        // Existing layers must become a MoveImage so the resize is written back on apply.
        if (it is EditorObject.ImageObject) it.copy(scale = scale.coerceIn(0.2f, 3f), moved = it.moved || it.annotationId != null) else it
    }
    fun onSelectedImageRotationChanged(deg: Int) = updateSelected {
        // Existing layers must become a MoveImage so the rotation is re-embedded on apply.
        if (it is EditorObject.ImageObject) it.copy(rotationDeg = ((deg % 360) + 360) % 360, moved = it.moved || it.annotationId != null) else it
    }
    fun onReplacementChanged(text: String) =
        updateSelected { if (it is EditorObject.EditObject) it.copy(replacement = text) else it }
    fun onSelectedDeleteChanged(delete: Boolean) =
        updateSelected {
            when (it) {
                is EditorObject.EditObject -> it.copy(delete = delete)
                is EditorObject.ImageObject -> it.copy(delete = delete)
                else -> it
            }
        }

    /** 取消: discard a newly-added object, or revert a detected image layer to its pristine state (still selectable). */
    fun deleteSelected() = _uiState.update { state ->
        val id = state.selectedId ?: return@update state
        val target = state.objects.firstOrNull { it.id == id }
        if (target is EditorObject.ImageObject && target.annotationId != null) {
            state.copy(
                objects = state.objects.map {
                    if (it.id == id && it is EditorObject.ImageObject) {
                        it.copy(rect = it.baseRect ?: it.rect, delete = false, moved = false, scale = 1f, rotationDeg = 0)
                    } else {
                        it
                    }
                },
                selectedId = null,
            )
        } else {
            state.copy(objects = state.objects.filterNot { it.id == id }, selectedId = null)
        }
    }
    fun deselect() = _uiState.update { it.copy(selectedId = null) }

    /** Dismiss the success result overlay (back to an idle, editable state). */
    fun dismissResult() { _operation.value = OperationState.Idle }

    private fun updateSelected(transform: (EditorObject) -> EditorObject) = _uiState.update { state ->
        val id = state.selectedId ?: return@update state
        state.copy(objects = state.objects.map { if (it.id == id) transform(it) else it })
    }

    // --- layer list / commit ---

    /** Select a layer from the list (navigating to its page). */
    fun select(id: Long) {
        val obj = _uiState.value.objects.firstOrNull { it.id == id } ?: return
        if (obj.pageIndex != _uiState.value.page - 1) onPageChanged(obj.pageIndex + 1)
        _uiState.update { it.copy(selectedId = id) }
    }

    /** Pick up an existing text run from the object list — the list-based twin of tapping it. */
    fun selectRun(run: TextRun) {
        val s = _uiState.value
        val pageIndex = s.page - 1
        val existing = s.objects.firstOrNull {
            it is EditorObject.EditObject && it.pageIndex == pageIndex && it.target == run.text && it.occurrence == run.occurrence
        }
        if (existing != null) {
            _uiState.update { it.copy(selectedId = existing.id) }
            return
        }
        val obj = EditorObject.EditObject(
            nextId++, pageIndex, run.rect, run.text, run.text, run.fontSizePt,
            colorRgb = run.colorRgb, occurrence = run.occurrence,
        )
        _uiState.update { it.copy(objects = it.objects + obj, selectedId = obj.id) }
    }

    fun removeObject(id: Long) = _uiState.update {
        it.copy(objects = it.objects.filterNot { o -> o.id == id }, selectedId = if (it.selectedId == id) null else it.selectedId)
    }

    /** 決定: bake the pending edits into a temp PDF, show its real render, and clear the layers. */
    fun commitPreview() {
        val s = _uiState.value
        val ws = workingSource ?: return
        if (s.objects.isEmpty()) {
            _uiState.update { it.copy(selectedId = null) }
            return
        }
        val missing = missingFontIds(s.objects, s.availableFontIds)
        if (missing.isNotEmpty()) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_edit_needs_font))
            return
        }
        val edits = s.objects.mapNotNull { it.toEditOp() }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.vm_edit_committing))
            runCatching {
                val out = applyEdits.preview(ws, edits)
                val count = thumbnailLoader.open(out.uri)
                textLayer.open(out.uri)
                out to count
            }.onSuccess { (out, count) ->
                workingSource = out.uri
                currentRuns = emptyList()
                _uiState.update { it.copy(objects = emptyList(), selectedId = null, pageCount = count, committed = true) }
                _operation.value = OperationState.Idle
                loadPage(_uiState.value.page - 1)
            }.onFailure {
                _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.vm_edit_commit_failed), it)
            }
        }
    }

    /** Font ids required by [objects] that are not yet available (added text uses its own
     *  font; regenerating edited text falls back to the default font). */
    private fun missingFontIds(objects: List<EditorObject>, available: Set<String>): Set<String> =
        objects.flatMap {
            when {
                it is EditorObject.TextObject -> listOf(it.fontId)
                it is EditorObject.EditObject && !it.delete -> listOf(it.fontId)
                else -> emptyList()
            }
        }.toSet() - available

    // --- font ---

    /** Pick the font for *new* text (the composing controls). */
    fun onFontSelected(fontId: String) = _uiState.update { it.copy(selectedFontId = fontId) }

    /** Change the font of the currently selected added-text object. */
    fun onSelectedFontChanged(fontId: String) = _uiState.update { state ->
        state.copy(
            objects = state.objects.map {
                when {
                    it.id != state.selectedId -> it
                    it is EditorObject.TextObject -> it.copy(fontId = fontId)
                    // Changing an existing run's font can't be done in place → regenerate it.
                    it is EditorObject.EditObject -> it.copy(fontId = fontId, restyled = true)
                    else -> it
                }
            },
        )
    }

    fun refreshFonts() {
        viewModelScope.launch {
            val ids = withContext(dispatchers.io) { fontManager.availableIds() }
            _uiState.update { it.copy(availableFontIds = ids) }
        }
    }

    fun downloadFont(fontId: String) {
        if (_uiState.value.downloadingFontId != null) return
        val font = AppFont.byId(fontId)
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingFontId = font.id, fontProgress = 0f, fontError = "") }
            runCatching {
                withContext(dispatchers.io) {
                    fontManager.download(font) { fraction -> _uiState.update { it.copy(fontProgress = fraction) } }
                }
            }.onSuccess {
                _uiState.update { it.copy(downloadingFontId = null, availableFontIds = it.availableFontIds + font.id) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(downloadingFontId = null, fontError = e.message ?: LocaleManager.string(appContext, R.string.vm_edit_font_failed))
                }
            }
        }
    }

    // --- apply ---

    fun run() {
        val s = _uiState.value
        val ws = workingSource ?: return
        if (s.objects.isEmpty() && !s.committed) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_edit_no_items))
            return
        }
        val missing = missingFontIds(s.objects, s.availableFontIds)
        if (missing.isNotEmpty()) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_edit_needs_font))
            return
        }
        val edits = s.objects.mapNotNull { it.toEditOp() }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.vm_edit_applying))
            runCatching {
                applyEdits(ws, edits, s.outputTree, outputBaseName = s.sourceName) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.state_failed), it) }
        }
    }

    override fun onCleared() {
        thumbnailLoader.close()
        textLayer.close()
    }

    private fun EditorObject.toEditOp(): EditOp? = when (this) {
        is EditorObject.TextObject -> EditOp.AddText(pageIndex, rect, text, fontSizePt, colorRgb, bold, italic, underline, rotationDeg, url, fontId)
        is EditorObject.ImageObject -> when {
            annotationId == null -> EditOp.AddImage(pageIndex, rect.scaledAboutCenter(scale), uri, rotationDeg)
            delete -> EditOp.DeleteImage(pageIndex, rect, annotationId)
            moved -> EditOp.MoveImage(pageIndex, rect.scaledAboutCenter(scale), annotationId, rotationDeg)
            else -> null // existing, unchanged
        }
        is EditorObject.EditObject ->
            if (delete) EditOp.DeleteExistingText(pageIndex, rect, target, occurrence)
            else EditOp.EditExistingText(pageIndex, rect, target, replacement, fontSizePt, colorRgb, occurrence, moved, restyled, bold, italic, underline, rotationDeg, url, fontId)
    }

    private fun centeredRect(w: Float, h: Float): FractionRect {
        val l = ((1f - w) / 2f).coerceIn(0f, 1f - w)
        val t = ((1f - h) / 2f).coerceIn(0f, 1f - h)
        return FractionRect(l, t, l + w, t + h)
    }

    private companion object {
        const val PREVIEW_WIDTH_PX = 1080
        const val THUMB_MAX_PX = 512
    }
}

// --- FractionRect helpers (UI-side) ---

val FractionRect.width get() = right - left
val FractionRect.height get() = bottom - top
fun FractionRect.contains(x: Float, y: Float) = x in left..right && y in top..bottom
fun FractionRect.shifted(dx: Float, dy: Float): FractionRect {
    val w = width
    val h = height
    val nl = (left + dx).coerceIn(0f, 1f - w)
    val nt = (top + dy).coerceIn(0f, 1f - h)
    return FractionRect(nl, nt, nl + w, nt + h)
}
