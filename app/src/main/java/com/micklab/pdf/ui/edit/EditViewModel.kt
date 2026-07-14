package com.micklab.pdf.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.edit.ApplyEditsResult
import com.micklab.pdf.domain.edit.ApplyEditsUseCase
import com.micklab.pdf.domain.edit.EditOp
import com.micklab.pdf.domain.edit.FractionRect
import com.micklab.pdf.domain.edit.NotoFontManager
import com.micklab.pdf.domain.pdf.PdfThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A 3x3 placement anchor; maps to a visual-fraction box of the given size. */
enum class Anchor(val label: String) {
    TOP_LEFT("左上"), TOP("中央上"), TOP_RIGHT("右上"),
    LEFT("左"), CENTER("中央"), RIGHT("右"),
    BOTTOM_LEFT("左下"), BOTTOM("中央下"), BOTTOM_RIGHT("右下");

    fun rect(boxW: Float, boxH: Float): FractionRect {
        val col = when (this) {
            TOP_LEFT, LEFT, BOTTOM_LEFT -> 0f
            TOP, CENTER, BOTTOM -> (1f - boxW) / 2f
            else -> 1f - boxW
        }
        val row = when (this) {
            TOP_LEFT, TOP, TOP_RIGHT -> 0f
            LEFT, CENTER, RIGHT -> (1f - boxH) / 2f
            else -> 1f - boxH
        }
        return FractionRect(col, row, col + boxW, row + boxH)
    }
}

enum class FontStage { UNKNOWN, AVAILABLE, DOWNLOADING, ERROR }

/** One queued edit plus a short human label for the list UI. */
data class PendingOp(val id: Long, val label: String, val op: EditOp)

data class EditUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val ops: List<PendingOp> = emptyList(),
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
    val fontStage: FontStage = FontStage.UNKNOWN,
    val fontProgress: Float = 0f,
    val fontError: String = "",
    // Add-text / placement form.
    val page: Int = 1,
    val text: String = "",
    val fontSizePt: Float = 14f,
    val colorRgb: Int = 0x000000,
    val anchor: Anchor = Anchor.TOP_LEFT,
)

@HiltViewModel
class EditViewModel @Inject constructor(
    private val applyEdits: ApplyEditsUseCase,
    private val fontManager: NotoFontManager,
    private val thumbnailLoader: PdfThumbnailLoader,
    private val fileRepository: FileRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<ApplyEditsResult>>(OperationState.Idle)
    val operation: StateFlow<OperationState<ApplyEditsResult>> = _operation.asStateFlow()

    private var nextId = 0L

    init {
        refreshFont()
    }

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update {
            it.copy(source = uri, sourceName = fileRepository.displayName(uri), ops = emptyList(), pageCount = 0, page = 1)
        }
        _operation.value = OperationState.Idle
        viewModelScope.launch {
            val count = thumbnailLoader.open(uri)
            _uiState.update { it.copy(pageCount = count) }
        }
    }

    fun onOutputTreePicked(uri: Uri?) {
        if (uri != null) fileRepository.persistTreePermission(uri)
        _uiState.update {
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::treeDisplayName).orEmpty())
        }
    }

    fun onPageChanged(page: Int) = _uiState.update { it.copy(page = page.coerceAtLeast(1)) }
    fun onTextChanged(text: String) = _uiState.update { it.copy(text = text) }
    fun onFontSizeChanged(size: Float) = _uiState.update { it.copy(fontSizePt = size) }
    fun onColorChanged(rgb: Int) = _uiState.update { it.copy(colorRgb = rgb) }
    fun onAnchorChanged(anchor: Anchor) = _uiState.update { it.copy(anchor = anchor) }

    fun addText() {
        val s = _uiState.value
        val text = s.text.trim()
        if (text.isEmpty()) return
        val pageIndex = pageIndexOf(s.page) ?: return
        val op = EditOp.AddText(pageIndex, s.anchor.rect(TEXT_BOX_W, TEXT_BOX_H), text, s.fontSizePt, s.colorRgb)
        addOp("T P${s.page}: ${text.take(16)}", op)
        _uiState.update { it.copy(text = "") }
    }

    fun addImage(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        val s = _uiState.value
        val pageIndex = pageIndexOf(s.page) ?: return
        val op = EditOp.AddImage(pageIndex, s.anchor.rect(IMAGE_BOX_W, IMAGE_BOX_H), uri)
        addOp("IMG P${s.page}: ${fileRepository.displayName(uri).take(16)}", op)
    }

    fun removeOp(id: Long) = _uiState.update { state -> state.copy(ops = state.ops.filterNot { it.id == id }) }

    private fun addOp(label: String, op: EditOp) =
        _uiState.update { it.copy(ops = it.ops + PendingOp(nextId++, label, op)) }

    /** 0-based index if valid (or if the page count isn't known yet), else null. */
    private fun pageIndexOf(page1Based: Int): Int? {
        val count = _uiState.value.pageCount
        val index = page1Based - 1
        return if (count <= 0 || index in 0 until count) index else null
    }

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

    fun run() {
        val s = _uiState.value
        val source = s.source ?: return
        if (s.ops.isEmpty()) {
            _operation.value = OperationState.Failure("編集項目を追加してください")
            return
        }
        if (s.ops.any { it.op is EditOp.AddText } && s.fontStage != FontStage.AVAILABLE) {
            _operation.value = OperationState.Failure("テキスト追加には日本語フォントの取得が必要です（初回のみ通信）")
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = "適用中…")
            runCatching {
                applyEdits(source, s.ops.map { it.op }, s.outputTree) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: "失敗しました", it) }
        }
    }

    override fun onCleared() {
        thumbnailLoader.close()
    }

    private companion object {
        const val TEXT_BOX_W = 0.6f
        const val TEXT_BOX_H = 0.08f
        const val IMAGE_BOX_W = 0.4f
        const val IMAGE_BOX_H = 0.4f
    }
}
