package com.micklab.pdf.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.ocr.LlmApiType
import com.micklab.pdf.domain.ocr.LlmClient
import com.micklab.pdf.domain.ocr.LlmSettings
import com.micklab.pdf.domain.ocr.LlmSettingsStore
import com.micklab.pdf.domain.ocr.OcrModelManager
import com.micklab.pdf.domain.ocr.OcrModelVariant
import com.micklab.pdf.domain.ocr.PaddleModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
            _operation.value = OperationState.Failure("選択中の言語はすべて取込済みです")
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, "ダウンロード準備中…")
            runCatching {
                withContext(dispatchers.io) {
                    missing.forEachIndexed { index, language ->
                        modelManager.downloadLanguage(language, variant) { fraction ->
                            _operation.value = OperationState.Running(
                                fraction, "$language をダウンロード中… (${index + 1}/${missing.size})",
                            )
                        }
                    }
                }
            }.onSuccess {
                refreshInstalledLanguages()
                _operation.value = OperationState.Success("取込完了: ${missing.joinToString("+")}")
            }.onFailure {
                _operation.value = OperationState.Failure(it.message ?: "ダウンロードに失敗しました", it)
            }
        }
    }

    fun importTesseract(treeUri: Uri) {
        viewModelScope.launch {
            val count = withContext(dispatchers.io) { modelManager.importFromTree(treeUri) }
            refreshInstalledLanguages()
            _operation.value = if (count > 0) {
                OperationState.Success("$count 件の traineddata を取り込みました")
            } else {
                OperationState.Failure("選択したフォルダに *.traineddata が見つかりませんでした")
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
            _operation.value = OperationState.Running(null, "モデル一覧を取得中…")
            runCatching { llmClient.listModels() }
                .onSuccess { models ->
                    _uiState.update { it.copy(llmModels = models) }
                    _operation.value = if (models.isEmpty()) {
                        OperationState.Failure("モデルが見つかりませんでした")
                    } else {
                        OperationState.Success("${models.size} 個のモデルを取得しました")
                    }
                }
                .onFailure { _operation.value = OperationState.Failure(it.message ?: "モデル取得に失敗しました") }
        }
    }

    fun testLlmConnection() {
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, "接続確認中…")
            val available = runCatching { llmClient.ping() }.getOrDefault(false)
            _operation.value = if (available) {
                OperationState.Success("接続 OK（サーバに到達しました）")
            } else {
                OperationState.Failure("接続できません。URL・サーバ起動を確認してください。")
            }
        }
    }

    // --- Paddle ---

    fun downloadPaddleModels() {
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, "ダウンロード準備中…")
            runCatching {
                withContext(dispatchers.io) {
                    paddleModelManager.downloadAll { fileName, fraction ->
                        _operation.value = OperationState.Running(fraction, "$fileName をダウンロード中…")
                    }
                }
            }.onSuccess {
                refreshPaddleStatus()
                _operation.value = OperationState.Success("PaddleOCR モデルを取得しました")
            }.onFailure {
                _operation.value = OperationState.Failure(it.message ?: "ダウンロードに失敗しました", it)
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
