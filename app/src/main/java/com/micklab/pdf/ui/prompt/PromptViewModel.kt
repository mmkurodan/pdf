package com.micklab.pdf.ui.prompt

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.R
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.ocr.LlmSettings
import com.micklab.pdf.domain.ocr.LlmSettingsStore
import com.micklab.pdf.domain.ocr.OcrEngineRegistry
import com.micklab.pdf.domain.usecase.PromptDocumentUseCase
import com.micklab.pdf.domain.usecase.PromptResult
import com.micklab.pdf.domain.usecase.PromptScope
import com.micklab.pdf.domain.usecase.SummaryMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PromptUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val prompt: String = "",
    val scope: PromptScope = PromptScope.PER_PAGE,
    val method: SummaryMethod = SummaryMethod.OCR_THEN_LLM,
    val engine: OcrEngineType = OcrEngineType.TESSERACT,
    val availableEngines: List<OcrEngineType> = emptyList(),
    val languages: List<String> = listOf("jpn", "eng"),
    val dpi: Int = 200,
    val llmSettings: LlmSettings = LlmSettings(),
)

@HiltViewModel
class PromptViewModel @Inject constructor(
    private val promptDocument: PromptDocumentUseCase,
    private val ocrRegistry: OcrEngineRegistry,
    private val llmSettingsStore: LlmSettingsStore,
    private val fileRepository: FileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptUiState())
    val uiState: StateFlow<PromptUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<PromptResult>>(OperationState.Idle)
    val operation: StateFlow<OperationState<PromptResult>> = _operation.asStateFlow()

    init {
        _uiState.update {
            it.copy(availableEngines = ocrRegistry.engineTypes, llmSettings = llmSettingsStore.get())
        }
    }

    fun onSourcePicked(uri: Uri) {
        fileRepository.persistReadPermission(uri)
        _uiState.update {
            it.copy(source = uri, sourceName = fileRepository.displayName(uri), llmSettings = llmSettingsStore.get())
        }
        _operation.value = OperationState.Idle
    }

    fun onPromptChanged(prompt: String) = _uiState.update { it.copy(prompt = prompt) }
    fun onScopeChanged(scope: PromptScope) = _uiState.update { it.copy(scope = scope) }
    fun onMethodChanged(method: SummaryMethod) = _uiState.update { it.copy(method = method) }
    fun onEngineChanged(engine: OcrEngineType) = _uiState.update { it.copy(engine = engine) }
    fun onDpiChanged(dpi: Int) = _uiState.update { it.copy(dpi = dpi.coerceIn(100, 400)) }

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
        if (state.prompt.isBlank()) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_prm_need_prompt))
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = LocaleManager.string(appContext, R.string.vm_prm_running))
            runCatching {
                promptDocument(
                    source = source,
                    prompt = state.prompt,
                    scope = state.scope,
                    method = state.method,
                    engineType = state.engine,
                    languages = state.languages,
                    renderDpi = state.dpi,
                ) { fraction, label -> _operation.value = OperationState.Running(fraction, label) }
            }.onSuccess { _operation.value = OperationState.Success(it) }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.state_failed), it) }
        }
    }
}
