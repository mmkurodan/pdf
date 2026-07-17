package com.micklab.pdf.ui.topdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.R
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.usecase.ImagesToPdfUseCase
import com.micklab.pdf.domain.usecase.PagePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageItem(val uri: Uri, val name: String)

data class ImageToPdfUiState(
    val items: List<ImageItem> = emptyList(),
    val preset: PagePreset = PagePreset.FIT_A4,
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
)

@HiltViewModel
class ImageToPdfViewModel @Inject constructor(
    private val imagesToPdf: ImagesToPdfUseCase,
    private val fileRepository: FileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToPdfUiState())
    val uiState: StateFlow<ImageToPdfUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<OutputFile>>(OperationState.Idle)
    val operation: StateFlow<OperationState<OutputFile>> = _operation.asStateFlow()

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { state ->
            val existing = state.items.map { it.uri }.toSet()
            val added = uris.filter { it !in existing }.map { uri ->
                fileRepository.persistReadPermission(uri)
                ImageItem(uri, fileRepository.displayName(uri))
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

    fun onPresetChanged(preset: PagePreset) = _uiState.update { it.copy(preset = preset) }

    fun onOutputTreePicked(uri: Uri?) {
        if (uri != null) fileRepository.persistTreePermission(uri)
        _uiState.update {
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::treeDisplayName).orEmpty())
        }
    }

    fun run() {
        val state = _uiState.value
        if (state.items.isEmpty()) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_i2p_no_images))
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.state_processing))
            runCatching {
                imagesToPdf(state.items.map { it.uri }, state.preset, state.outputTree) { fraction, label ->
                    _operation.value = OperationState.Running(fraction, label)
                }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.state_failed), it) }
        }
    }
}
