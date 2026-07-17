package com.micklab.pdf.ui.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.micklab.pdf.R
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.DocumentTextResult
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.ocr.OcrEngineNotImplementedException
import com.micklab.pdf.domain.ocr.OcrEngineRegistry
import com.micklab.pdf.domain.ocr.OcrModelUnavailableException
import com.micklab.pdf.domain.usecase.ExtractDocumentTextUseCase
import com.micklab.pdf.domain.usecase.TextExtractionMode
import com.micklab.pdf.worker.PdfProcessingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class OcrResultView(val result: DocumentTextResult, val json: String)

data class OcrUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val engine: OcrEngineType = OcrEngineType.TESSERACT,
    val availableEngines: List<OcrEngineType> = emptyList(),
    val languages: List<String> = listOf("jpn", "eng"),
    val mode: TextExtractionMode = TextExtractionMode.AUTO,
    val dpi: Int = 200,
    val runInBackground: Boolean = false,
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val extractDocumentText: ExtractDocumentTextUseCase,
    private val ocrRegistry: OcrEngineRegistry,
    private val fileRepository: FileRepository,
    private val workManager: WorkManager,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<OcrResultView>>(OperationState.Idle)
    val operation: StateFlow<OperationState<OcrResultView>> = _operation.asStateFlow()

    private var observeJob: Job? = null

    init {
        _uiState.update { it.copy(availableEngines = ocrRegistry.engineTypes) }
    }

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update { it.copy(source = uri, sourceName = fileRepository.displayName(uri)) }
        _operation.value = OperationState.Idle
    }

    fun onEngineChanged(engine: OcrEngineType) = _uiState.update { it.copy(engine = engine) }
    fun onModeChanged(mode: TextExtractionMode) = _uiState.update { it.copy(mode = mode) }
    fun onDpiChanged(dpi: Int) = _uiState.update { it.copy(dpi = dpi.coerceIn(100, 400)) }
    fun onToggleBackground(enabled: Boolean) = _uiState.update { it.copy(runInBackground = enabled) }

    fun toggleLanguage(language: String) = _uiState.update { state ->
        val languages = if (language in state.languages) {
            state.languages.filterNot { it == language }
        } else {
            state.languages + language
        }
        state.copy(languages = languages)
    }

    fun run() {
        val state = _uiState.value
        val source = state.source ?: return
        if (state.engine == OcrEngineType.TESSERACT &&
            state.languages.isEmpty() && state.mode != TextExtractionMode.EMBEDDED_ONLY
        ) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_ocr_need_language))
            return
        }
        if (state.runInBackground) runInBackground(state, source) else runInline(state, source)
    }

    private fun runInline(state: OcrUiState, source: Uri) {
        observeJob?.cancel()
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.vm_ocr_analyzing))
            runCatching {
                val result = extractDocumentText(
                    source = source,
                    engineType = state.engine,
                    languages = state.languages,
                    mode = state.mode,
                    renderDpi = state.dpi,
                ) { fraction, label -> _operation.value = OperationState.Running(fraction, label) }
                OcrResultView(result, json.encodeToString(result))
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(friendlyError(it), it) }
        }
    }

    private fun runInBackground(state: OcrUiState, source: Uri) {
        val request = OneTimeWorkRequestBuilder<PdfProcessingWorker>()
            .setInputData(
                PdfProcessingWorker.buildInputData(
                    source = source,
                    engine = state.engine,
                    languages = state.languages,
                    mode = state.mode,
                    dpi = state.dpi,
                ),
            )
            .build()

        workManager.enqueue(request)
        _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.vm_ocr_analyzing_bg))

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(PdfProcessingWorker.KEY_PROGRESS, 0)
                        val label = info.progress.getString(PdfProcessingWorker.KEY_PROGRESS_LABEL)
                            ?.takeIf { it.isNotBlank() }
                            ?: LocaleManager.string(appContext, R.string.vm_ocr_analyzing_bg)
                        _operation.value = OperationState.Running(progress / 100f, label)
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val uriString = info.outputData.getString(PdfProcessingWorker.KEY_RESULT_URI)
                        loadBackgroundResult(uriString)
                    }

                    WorkInfo.State.FAILED -> {
                        val message = info.outputData.getString(PdfProcessingWorker.KEY_ERROR)
                            ?: LocaleManager.string(appContext, R.string.state_failed)
                        _operation.value = OperationState.Failure(message)
                    }

                    WorkInfo.State.CANCELLED -> _operation.value = OperationState.Idle
                    else -> Unit
                }
            }
        }
    }

    private suspend fun loadBackgroundResult(uriString: String?) {
        if (uriString == null) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_ocr_no_result))
            return
        }
        runCatching {
            val text = withContext(dispatchers.io) {
                fileRepository.openInput(Uri.parse(uriString)).bufferedReader().use { it.readText() }
            }
            OcrResultView(json.decodeFromString<DocumentTextResult>(text), text)
        }.onSuccess { _operation.value = OperationState.Success(it) }
            .onFailure { _operation.value = OperationState.Failure(friendlyError(it), it) }
    }

    private fun friendlyError(t: Throwable): String = when (t) {
        is OcrModelUnavailableException ->
            LocaleManager.string(appContext, R.string.vm_ocr_model_hint, t.message ?: "")
        is OcrEngineNotImplementedException -> t.message ?: LocaleManager.string(appContext, R.string.vm_ocr_engine_unimpl)
        else -> t.message ?: LocaleManager.string(appContext, R.string.state_failed)
    }
}
