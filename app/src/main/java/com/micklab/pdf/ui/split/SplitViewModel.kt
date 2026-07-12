package com.micklab.pdf.ui.split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.model.SplitMode
import com.micklab.pdf.domain.pdf.PageRangeParser
import com.micklab.pdf.domain.usecase.GetPdfInfoUseCase
import com.micklab.pdf.domain.usecase.SplitPdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplitUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val pageSpec: String = "",
    val mode: SplitMode = SplitMode.SELECTED_INTO_ONE,
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
)

@HiltViewModel
class SplitViewModel @Inject constructor(
    private val splitPdf: SplitPdfUseCase,
    private val getPdfInfo: GetPdfInfoUseCase,
    private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplitUiState())
    val uiState: StateFlow<SplitUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<List<OutputFile>>>(OperationState.Idle)
    val operation: StateFlow<OperationState<List<OutputFile>>> = _operation.asStateFlow()

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update {
            it.copy(source = uri, sourceName = fileRepository.displayName(uri), pageCount = 0)
        }
        _operation.value = OperationState.Idle
        viewModelScope.launch {
            val count = runCatching { getPdfInfo(uri) }.getOrDefault(0)
            _uiState.update {
                it.copy(pageCount = count, pageSpec = if (count > 0) "1-$count" else "")
            }
        }
    }

    fun onPageSpecChanged(spec: String) = _uiState.update { it.copy(pageSpec = spec) }

    fun onModeChanged(mode: SplitMode) = _uiState.update { it.copy(mode = mode) }

    fun onOutputTreePicked(uri: Uri?) {
        if (uri != null) fileRepository.persistTreePermission(uri)
        _uiState.update {
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::displayName).orEmpty())
        }
    }

    fun run() {
        val state = _uiState.value
        val source = state.source ?: return
        val pages = PageRangeParser.parse(state.pageSpec, state.pageCount.coerceAtLeast(1))
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = "処理中…")
            runCatching {
                splitPdf(source, pages, state.mode, state.outputTree) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: "失敗しました", it) }
        }
    }
}
