package com.micklab.pdf.ui.reorder

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.usecase.GetPdfInfoUseCase
import com.micklab.pdf.domain.usecase.ReorderPdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
)

@HiltViewModel
class ReorderViewModel @Inject constructor(
    private val reorderPdf: ReorderPdfUseCase,
    private val getPdfInfo: GetPdfInfoUseCase,
    private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReorderUiState())
    val uiState: StateFlow<ReorderUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<OutputFile>>(OperationState.Idle)
    val operation: StateFlow<OperationState<OutputFile>> = _operation.asStateFlow()

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update {
            it.copy(source = uri, sourceName = fileRepository.displayName(uri), pageCount = 0, order = emptyList())
        }
        _operation.value = OperationState.Idle
        viewModelScope.launch {
            val count = runCatching { getPdfInfo(uri) }.getOrDefault(0)
            _uiState.update { it.copy(pageCount = count, order = (0 until count).toList()) }
        }
    }

    fun move(index: Int, delta: Int) = _uiState.update { state ->
        val target = index + delta
        if (index in state.order.indices && target in state.order.indices) {
            val list = state.order.toMutableList()
            list.add(target, list.removeAt(index))
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
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::displayName).orEmpty())
        }
    }

    fun run() {
        val state = _uiState.value
        val source = state.source ?: return
        if (state.order.isEmpty()) {
            _operation.value = OperationState.Failure("ページがありません")
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = "処理中…")
            runCatching {
                reorderPdf(source, state.order, state.outputTree) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: "失敗しました", it) }
        }
    }
}
