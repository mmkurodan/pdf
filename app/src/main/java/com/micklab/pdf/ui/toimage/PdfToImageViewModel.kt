package com.micklab.pdf.ui.toimage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.ImageFormat
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.pdf.PageRangeParser
import com.micklab.pdf.domain.usecase.GetPdfInfoUseCase
import com.micklab.pdf.domain.usecase.PdfToImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PdfToImageUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val pageSpec: String = "",
    val dpi: Int = 200,
    val format: ImageFormat = ImageFormat.PNG,
    val jpegQuality: Int = 90,
    val outputTree: Uri? = null,
    val outputFolderName: String = "",
)

@HiltViewModel
class PdfToImageViewModel @Inject constructor(
    private val pdfToImages: PdfToImagesUseCase,
    private val getPdfInfo: GetPdfInfoUseCase,
    private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfToImageUiState())
    val uiState: StateFlow<PdfToImageUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<List<OutputFile>>>(OperationState.Idle)
    val operation: StateFlow<OperationState<List<OutputFile>>> = _operation.asStateFlow()

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update { it.copy(source = uri, sourceName = fileRepository.displayName(uri), pageCount = 0) }
        _operation.value = OperationState.Idle
        viewModelScope.launch {
            val count = runCatching { getPdfInfo(uri) }.getOrDefault(0)
            _uiState.update { it.copy(pageCount = count) }
        }
    }

    fun onPageSpecChanged(spec: String) = _uiState.update { it.copy(pageSpec = spec) }
    fun onDpiChanged(dpi: Int) = _uiState.update { it.copy(dpi = dpi.coerceIn(36, 600)) }
    fun onFormatChanged(format: ImageFormat) = _uiState.update { it.copy(format = format) }
    fun onQualityChanged(quality: Int) = _uiState.update { it.copy(jpegQuality = quality.coerceIn(10, 100)) }

    fun onOutputTreePicked(uri: Uri?) {
        if (uri != null) fileRepository.persistTreePermission(uri)
        _uiState.update {
            it.copy(outputTree = uri, outputFolderName = uri?.let(fileRepository::displayName).orEmpty())
        }
    }

    fun run() {
        val state = _uiState.value
        val source = state.source ?: return
        val pages = state.pageSpec.trim().takeIf { it.isNotEmpty() }
            ?.let { PageRangeParser.parse(it, state.pageCount.coerceAtLeast(1)) }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = "処理中…")
            runCatching {
                pdfToImages(
                    source = source,
                    pages = pages,
                    dpi = state.dpi,
                    format = state.format,
                    jpegQuality = state.jpegQuality,
                    outputTree = state.outputTree,
                ) { fraction, label -> _operation.value = OperationState.Running(fraction, label) }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: "失敗しました", it) }
        }
    }
}
