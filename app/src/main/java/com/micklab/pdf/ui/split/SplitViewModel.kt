package com.micklab.pdf.ui.split

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.R
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.model.SplitMode
import com.micklab.pdf.domain.pdf.PdfThumbnailLoader
import com.micklab.pdf.domain.usecase.SplitPdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val loadingThumbnails: Boolean = false,
    val selectedPages: Set<Int> = emptySet(),
    val mode: SplitMode = SplitMode.SELECTED_INTO_ONE,
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
)

@HiltViewModel
class SplitViewModel @Inject constructor(
    private val splitPdf: SplitPdfUseCase,
    private val thumbnailLoader: PdfThumbnailLoader,
    private val fileRepository: FileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplitUiState())
    val uiState: StateFlow<SplitUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<List<OutputFile>>>(OperationState.Idle)
    val operation: StateFlow<OperationState<List<OutputFile>>> = _operation.asStateFlow()

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update {
            it.copy(
                source = uri,
                sourceName = fileRepository.displayName(uri),
                pageCount = 0,
                loadingThumbnails = true,
                selectedPages = emptySet(),
            )
        }
        _operation.value = OperationState.Idle
        viewModelScope.launch {
            val count = thumbnailLoader.open(uri)
            _uiState.update { it.copy(pageCount = count, loadingThumbnails = false) }
        }
    }

    /** Lazy thumbnail provider for the grid cells. */
    suspend fun thumbnail(index: Int): Bitmap? = thumbnailLoader.render(index)

    fun togglePage(index: Int) = _uiState.update { state ->
        val selected = if (index in state.selectedPages) state.selectedPages - index else state.selectedPages + index
        state.copy(selectedPages = selected)
    }

    fun selectAll() = _uiState.update { it.copy(selectedPages = (0 until it.pageCount).toSet()) }

    fun clearSelection() = _uiState.update { it.copy(selectedPages = emptySet()) }

    fun onModeChanged(mode: SplitMode) = _uiState.update { it.copy(mode = mode) }

    fun onOutputTreePicked(uri: Uri?) {
        if (uri != null) fileRepository.persistTreePermission(uri)
        _uiState.update {
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::treeDisplayName).orEmpty())
        }
    }

    fun run() {
        val state = _uiState.value
        val source = state.source ?: return
        val pages = state.selectedPages.sorted()
        if (pages.isEmpty()) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_split_no_pages))
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.state_processing))
            runCatching {
                splitPdf(source, pages, state.mode, state.outputTree) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.state_failed), it) }
        }
    }

    override fun onCleared() {
        thumbnailLoader.close()
        super.onCleared()
    }
}
