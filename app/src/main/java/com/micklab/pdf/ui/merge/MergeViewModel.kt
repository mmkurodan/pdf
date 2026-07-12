package com.micklab.pdf.ui.merge

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.usecase.MergePdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MergeItem(val uri: Uri, val name: String)

data class MergeUiState(
    val items: List<MergeItem> = emptyList(),
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
)

@HiltViewModel
class MergeViewModel @Inject constructor(
    private val mergePdf: MergePdfUseCase,
    private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MergeUiState())
    val uiState: StateFlow<MergeUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<OutputFile>>(OperationState.Idle)
    val operation: StateFlow<OperationState<OutputFile>> = _operation.asStateFlow()

    fun onPdfsPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { state ->
            val existing = state.items.map { it.uri }.toSet()
            val added = uris.filter { it !in existing }.map { uri ->
                fileRepository.persistReadPermission(uri)
                MergeItem(uri, fileRepository.displayName(uri))
            }
            state.copy(items = state.items + added)
        }
    }

    fun move(index: Int, delta: Int) = _uiState.update { state ->
        val target = index + delta
        if (index in state.items.indices && target in state.items.indices) {
            val list = state.items.toMutableList()
            list.add(target, list.removeAt(index))
            state.copy(items = list)
        } else {
            state
        }
    }

    fun remove(index: Int) = _uiState.update { state ->
        state.copy(items = state.items.filterIndexed { i, _ -> i != index })
    }

    fun onOutputTreePicked(uri: Uri?) {
        if (uri != null) fileRepository.persistTreePermission(uri)
        _uiState.update {
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::displayName).orEmpty())
        }
    }

    fun run() {
        val state = _uiState.value
        if (state.items.size < 2) {
            _operation.value = OperationState.Failure("結合するには 2 つ以上の PDF を選択してください")
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = "処理中…")
            runCatching {
                mergePdf(state.items.map { it.uri }, state.outputTree) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: "失敗しました", it) }
        }
    }
}
