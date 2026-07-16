package com.micklab.pdf.ui.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.edit.ApplyEditsResult
import com.micklab.pdf.domain.edit.ApplyEditsUseCase
import com.micklab.pdf.domain.edit.CreateBlankPdfUseCase
import com.micklab.pdf.domain.edit.EditOp
import com.micklab.pdf.domain.edit.FractionRect
import com.micklab.pdf.domain.edit.NotoFontManager
import com.micklab.pdf.domain.edit.PdfTextLayer
import com.micklab.pdf.domain.edit.TextRun
import com.micklab.pdf.domain.pdf.PdfThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class FontStage { UNKNOWN, AVAILABLE, DOWNLOADING, ERROR }

/** A draggable editor object placed on the page and edited before it is applied. */
sealed interface EditorObject {
    val id: Long
    val pageIndex: Int
    val rect: FractionRect

    data class TextObject(
        override val id: Long, override val pageIndex: Int, override val rect: FractionRect,
        val text: String, val fontSizePt: Float, val colorRgb: Int,
    ) : EditorObject

    data class ImageObject(
        override val id: Long, override val pageIndex: Int, override val rect: FractionRect,
        val uri: Uri, val name: String, val thumbnail: Bitmap? = null,
    ) : EditorObject

    /** Editing an existing text-layer run: [target] is what's on the page, [replacement] the new text. */
    data class EditObject(
        override val id: Long, override val pageIndex: Int, override val rect: FractionRect,
        val target: String, val replacement: String, val fontSizePt: Float, val colorRgb: Int = 0x000000,
        val delete: Boolean = false, val occurrence: Int = 0, val moved: Boolean = false,
        val restyled: Boolean = false,
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
    val selectedId: Long? = null,
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
    val fontStage: FontStage = FontStage.UNKNOWN,
    val fontProgress: Float = 0f,
    val fontError: String = "",
    // Controls for creating new text.
    val textInput: String = "",
    val fontSizePt: Float = 14f,
    val colorRgb: Int = 0x000000,
    // At least one 決定 has baked edits into the working PDF (so "save" is meaningful even with no pending layers).
    val committed: Boolean = false,
) {
    val selected: EditorObject? get() = objects.firstOrNull { it.id == selectedId }
}

@HiltViewModel
class EditViewModel @Inject constructor(
    private val applyEdits: ApplyEditsUseCase,
    private val createBlankPdf: CreateBlankPdfUseCase,
    private val fontManager: NotoFontManager,
    private val thumbnailLoader: PdfThumbnailLoader,
    private val textLayer: PdfTextLayer,
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
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
        refreshFont()
    }

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update {
            EditUiState(
                source = uri, sourceName = fileRepository.displayName(uri),
                fontStage = it.fontStage, outputTree = it.outputTree, outputFolderName = it.outputFolderName,
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
            _operation.value = OperationState.Running(label = "白紙 PDF を作成中…")
            runCatching { createBlankPdf() }
                .onSuccess {
                    _operation.value = OperationState.Idle
                    onSourcePicked(it.uri)
                }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: "白紙 PDF の作成に失敗しました") }
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
        viewModelScope.launch {
            val bitmap = thumbnailLoader.render(pageIndex, PREVIEW_WIDTH_PX)
            val size = thumbnailLoader.pageSizePoints(pageIndex)
            _uiState.update {
                it.copy(previewBitmap = bitmap, pageWidthPt = size?.first ?: 0f, pageHeightPt = size?.second ?: 0f)
            }
            currentRuns = textLayer.runs(pageIndex)
        }
    }

    // --- creating new text / images ---

    fun onTextInputChanged(text: String) = _uiState.update { it.copy(textInput = text) }
    fun onFontSizeChanged(size: Float) = _uiState.update { it.copy(fontSizePt = size) }
    fun onColorChanged(rgb: Int) = _uiState.update { it.copy(colorRgb = rgb) }

    fun addText() {
        val s = _uiState.value
        val text = s.textInput.trim()
        if (text.isEmpty() || s.source == null) return
        val h = if (s.pageHeightPt > 0f) (s.fontSizePt * 1.6f / s.pageHeightPt).coerceIn(0.03f, 0.4f) else 0.08f
        val obj = EditorObject.TextObject(nextId++, s.page - 1, centeredRect(0.5f, h), text, s.fontSizePt, s.colorRgb)
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
                        is EditorObject.ImageObject -> it.copy(rect = moved)
                        // Dragging an existing run means "move it": regenerate at the new spot.
                        is EditorObject.EditObject -> it.copy(rect = moved, moved = true)
                    }
                },
            )
        }
    }

    private fun objectAt(fx: Float, fy: Float): EditorObject? {
        val pageIndex = _uiState.value.page - 1
        return _uiState.value.objects.lastOrNull { it.pageIndex == pageIndex && it.rect.contains(fx, fy) }
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
    fun onReplacementChanged(text: String) =
        updateSelected { if (it is EditorObject.EditObject) it.copy(replacement = text) else it }
    fun onSelectedDeleteChanged(delete: Boolean) =
        updateSelected { if (it is EditorObject.EditObject) it.copy(delete = delete) else it }

    fun deleteSelected() = _uiState.update { it.copy(objects = it.objects.filterNot { o -> o.id == it.selectedId }, selectedId = null) }
    fun deselect() = _uiState.update { it.copy(selectedId = null) }

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
        if (needsFont(s.objects) && s.fontStage != FontStage.AVAILABLE) {
            _operation.value = OperationState.Failure("テキストの追加・編集には日本語フォントの取得が必要です（初回のみ通信）")
            return
        }
        val edits = s.objects.map { it.toEditOp() }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = "プレビューに反映中…")
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
                _operation.value = OperationState.Failure(it.message ?: "反映に失敗しました", it)
            }
        }
    }

    private fun needsFont(objects: List<EditorObject>) = objects.any {
        it is EditorObject.TextObject || (it is EditorObject.EditObject && !it.delete)
    }

    // --- font ---

    fun refreshFont() {
        viewModelScope.launch {
            val available = withContext(dispatchers.io) { fontManager.isAvailable() }
            _uiState.update { it.copy(fontStage = if (available) FontStage.AVAILABLE else FontStage.UNKNOWN) }
        }
    }

    fun downloadFont() {
        if (_uiState.value.fontStage == FontStage.DOWNLOADING) return
        viewModelScope.launch {
            _uiState.update { it.copy(fontStage = FontStage.DOWNLOADING, fontProgress = 0f, fontError = "") }
            runCatching {
                withContext(dispatchers.io) {
                    fontManager.download { fraction -> _uiState.update { it.copy(fontProgress = fraction) } }
                }
            }.onSuccess {
                _uiState.update { it.copy(fontStage = FontStage.AVAILABLE) }
            }.onFailure { e ->
                _uiState.update { it.copy(fontStage = FontStage.ERROR, fontError = e.message ?: "取得に失敗しました") }
            }
        }
    }

    // --- apply ---

    fun run() {
        val s = _uiState.value
        val ws = workingSource ?: return
        if (s.objects.isEmpty() && !s.committed) {
            _operation.value = OperationState.Failure("編集項目を追加してください")
            return
        }
        if (needsFont(s.objects) && s.fontStage != FontStage.AVAILABLE) {
            _operation.value = OperationState.Failure("テキストの追加・編集には日本語フォントの取得が必要です（初回のみ通信）")
            return
        }
        val edits = s.objects.map { it.toEditOp() }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = "適用中…")
            runCatching {
                applyEdits(ws, edits, s.outputTree, outputBaseName = s.sourceName) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: "失敗しました", it) }
        }
    }

    override fun onCleared() {
        thumbnailLoader.close()
        textLayer.close()
    }

    private fun EditorObject.toEditOp(): EditOp = when (this) {
        is EditorObject.TextObject -> EditOp.AddText(pageIndex, rect, text, fontSizePt, colorRgb)
        is EditorObject.ImageObject -> EditOp.AddImage(pageIndex, rect, uri)
        is EditorObject.EditObject ->
            if (delete) EditOp.DeleteExistingText(pageIndex, rect, target, occurrence)
            else EditOp.EditExistingText(pageIndex, rect, target, replacement, fontSizePt, colorRgb, occurrence, moved, restyled)
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
