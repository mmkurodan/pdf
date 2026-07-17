package com.micklab.pdf.ui.reorder

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.R
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.usecase.GetPdfInfoUseCase
import com.micklab.pdf.domain.usecase.PageBitmap
import com.micklab.pdf.domain.usecase.RenderPdfThumbnailsUseCase
import com.micklab.pdf.domain.usecase.ReorderPdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReorderUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val order: List<Int> = emptyList(),
    val thumbnails: Map<Int, PageBitmap> = emptyMap(),
    val loadingThumbnails: Boolean = false,
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
)

@HiltViewModel
class ReorderViewModel @Inject constructor(
    private val reorderPdf: ReorderPdfUseCase,
    private val getPdfInfo: GetPdfInfoUseCase,
    private val renderThumbnails: RenderPdfThumbnailsUseCase,
    private val fileRepository: FileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReorderUiState())
    val uiState: StateFlow<ReorderUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<OutputFile>>(OperationState.Idle)
    val operation: StateFlow<OperationState<OutputFile>> = _operation.asStateFlow()

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update {
            it.copy(
                source = uri,
                sourceName = fileRepository.displayName(uri),
                pageCount = 0,
                order = emptyList(),
                thumbnails = emptyMap(),
                loadingThumbnails = true,
            )
        }
        _operation.value = OperationState.Idle
        viewModelScope.launch {
            val count = runCatching { getPdfInfo(uri) }.getOrDefault(0)
            _uiState.update { it.copy(pageCount = count, order = (0 until count).toList()) }
            val thumbs = runCatching { renderThumbnails(uri) }.getOrDefault(emptyList())
            _uiState.update { it.copy(thumbnails = thumbs.associateBy { t -> t.index }, loadingThumbnails = false) }
        }
    }

    fun move(index: Int, delta: Int) = moveTo(index, index + delta)

    /** Moves the page at [from] to position [to] (used by drag-and-drop). */
    fun moveTo(from: Int, to: Int) = _uiState.update { state ->
        if (from in state.order.indices && to in state.order.indices && from != to) {
            val list = state.order.toMutableList()
            list.add(to, list.removeAt(from))
            state.copy(order = list)
        } else {
            state
        }
    }

    fun remove(index: Int) = _uiState.update { state ->
        state.copy(order = state.order.filterIndexed { i, _ -> i != index })
    }

    fun reset() = _uiState.update { it.copy(order = (0 until it.pageCount).toList()) }

    fun onOutputTreePicked(uri: Uri?) {
        if (uri != null) fileRepository.persistTreePermission(uri)
        _uiState.update {
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::treeDisplayName).orEmpty())
        }
    }

    fun run() {
        val state = _uiState.value
        val source = state.source ?: return
        if (state.order.isEmpty()) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_reorder_no_pages))
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.state_processing))
            runCatching {
                reorderPdf(source, state.order, state.outputTree) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.state_failed), it) }
        }
    }
}
