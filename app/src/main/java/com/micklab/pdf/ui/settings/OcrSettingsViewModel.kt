package com.micklab.pdf.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.R
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.ocr.LlmApiType
import com.micklab.pdf.domain.ocr.LlmClient
import com.micklab.pdf.domain.ocr.LlmSettings
import com.micklab.pdf.domain.ocr.LlmSettingsStore
import com.micklab.pdf.domain.ocr.OcrModelManager
import com.micklab.pdf.domain.ocr.OcrModelVariant
import com.micklab.pdf.domain.ocr.PaddleModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Model management + LLM connection settings, independent of OCR execution. */
data class OcrSettingsUiState(
    val downloadLanguages: List<String> = listOf("jpn", "eng"),
    val installedLanguages: Set<String> = emptySet(),
    val llmSettings: LlmSettings = LlmSettings(),
    val llmModels: List<String> = emptyList(),
    val paddleDownloaded: Boolean = false,
)

@HiltViewModel
class OcrSettingsViewModel @Inject constructor(
    private val modelManager: OcrModelManager,
    private val paddleModelManager: PaddleModelManager,
    private val llmSettingsStore: LlmSettingsStore,
    private val llmClient: LlmClient,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrSettingsUiState())
    val uiState: StateFlow<OcrSettingsUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<String>>(OperationState.Idle)
    val operation: StateFlow<OperationState<String>> = _operation.asStateFlow()

    init {
        _uiState.update { it.copy(llmSettings = llmSettingsStore.get()) }
        refreshInstalledLanguages()
        refreshPaddleStatus()
    }

    // --- Tesseract ---

    fun toggleDownloadLanguage(language: String) = _uiState.update { state ->
        val languages = if (language in state.downloadLanguages) {
            state.downloadLanguages.filterNot { it == language }
        } else {
            state.downloadLanguages + language
        }
        state.copy(downloadLanguages = languages)
    }

    fun downloadTesseract(variant: OcrModelVariant = OcrModelVariant.FAST) {
        val missing = _uiState.value.downloadLanguages.filter { it !in _uiState.value.installedLanguages }
        if (missing.isEmpty()) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_set_all_installed))
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, LocaleManager.string(appContext, R.string.vm_set_preparing))
            runCatching {
                withContext(dispatchers.io) {
                    missing.forEachIndexed { index, language ->
                        modelManager.downloadLanguage(language, variant) { fraction ->
                            _operation.value = OperationState.Running(
                                fraction,
                                LocaleManager.string(appContext, R.string.vm_set_downloading_lang, language, index + 1, missing.size),
                            )
                        }
                    }
                }
            }.onSuccess {
                refreshInstalledLanguages()
                _operation.value = OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_import_done, missing.joinToString("+")))
            }.onFailure {
                _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.vm_set_download_failed), it)
            }
        }
    }

    fun importTesseract(treeUri: Uri) {
        viewModelScope.launch {
            val count = withContext(dispatchers.io) { modelManager.importFromTree(treeUri) }
            refreshInstalledLanguages()
            _operation.value = if (count > 0) {
                OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_traineddata_imported, count))
            } else {
                OperationState.Failure(LocaleManager.string(appContext, R.string.vm_set_no_traineddata))
            }
        }
    }

    private fun refreshInstalledLanguages() {
        viewModelScope.launch {
            val installed = withContext(dispatchers.io) { modelManager.availableLanguages() }
            _uiState.update { it.copy(installedLanguages = installed) }
        }
    }

    // --- LLM ---

    fun onLlmApiTypeChanged(apiType: LlmApiType) = updateLlm { it.copy(apiType = apiType) }
    fun onLlmBaseUrlChanged(url: String) = updateLlm { it.copy(baseUrl = url) }
    fun onLlmModelChanged(model: String) = updateLlm { it.copy(model = model) }
    fun onLlmApiKeyChanged(key: String) = updateLlm { it.copy(apiKey = key) }

    private fun updateLlm(transform: (LlmSettings) -> LlmSettings) {
        val updated = transform(_uiState.value.llmSettings)
        llmSettingsStore.save(updated)
        _uiState.update { it.copy(llmSettings = updated) }
    }

    fun fetchLlmModels() {
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, LocaleManager.string(appContext, R.string.vm_set_fetching_models))
            runCatching { llmClient.listModels() }
                .onSuccess { models ->
                    _uiState.update { it.copy(llmModels = models) }
                    _operation.value = if (models.isEmpty()) {
                        OperationState.Failure(LocaleManager.string(appContext, R.string.vm_set_no_models))
                    } else {
                        OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_models_fetched, models.size))
                    }
                }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.vm_set_model_fetch_failed)) }
        }
    }

    fun testLlmConnection() {
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, LocaleManager.string(appContext, R.string.vm_set_testing))
            val available = runCatching { llmClient.ping() }.getOrDefault(false)
            _operation.value = if (available) {
                OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_conn_ok))
            } else {
                OperationState.Failure(LocaleManager.string(appContext, R.string.vm_set_conn_failed))
            }
        }
    }

    // --- Paddle ---

    fun downloadPaddleModels() {
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, LocaleManager.string(appContext, R.string.vm_set_preparing))
            runCatching {
                withContext(dispatchers.io) {
                    paddleModelManager.downloadAll { fileName, fraction ->
                        _operation.value = OperationState.Running(fraction, LocaleManager.string(appContext, R.string.vm_set_downloading_file, fileName))
                    }
                }
            }.onSuccess {
                refreshPaddleStatus()
                _operation.value = OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_paddle_done_msg))
            }.onFailure {
                _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.vm_set_download_failed), it)
            }
        }
    }

    private fun refreshPaddleStatus() {
        viewModelScope.launch {
            val downloaded = withContext(dispatchers.io) { paddleModelManager.isDownloaded() }
            _uiState.update { it.copy(paddleDownloaded = downloaded) }
        }
    }
}
