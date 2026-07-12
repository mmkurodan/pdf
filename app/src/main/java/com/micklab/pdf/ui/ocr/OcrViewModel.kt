package com.micklab.pdf.ui.ocr

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.domain.model.DocumentTextResult
import com.micklab.pdf.domain.model.OcrEngineType
import com.micklab.pdf.domain.ocr.LlmApiType
import com.micklab.pdf.domain.ocr.LlmSettings
import com.micklab.pdf.domain.ocr.LlmSettingsStore
import com.micklab.pdf.domain.ocr.OcrEngineNotImplementedException
import com.micklab.pdf.domain.ocr.OcrEngineRegistry
import com.micklab.pdf.domain.ocr.OcrModelManager
import com.micklab.pdf.domain.ocr.OcrModelUnavailableException
import com.micklab.pdf.domain.ocr.OcrModelVariant
import com.micklab.pdf.domain.ocr.PaddleModelManager
import com.micklab.pdf.domain.usecase.ExtractDocumentTextUseCase
import com.micklab.pdf.domain.usecase.TextExtractionMode
import com.micklab.pdf.worker.PdfProcessingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val installedLanguages: Set<String> = emptySet(),
    val mode: TextExtractionMode = TextExtractionMode.AUTO,
    val dpi: Int = 200,
    val runInBackground: Boolean = false,
    val llmSettings: LlmSettings = LlmSettings(),
    val paddleDownloaded: Boolean = false,
) {
    /** Tesseract-specific readiness (used only for the Tesseract hint). */
    val tesseractReady: Boolean
        get() = mode == TextExtractionMode.EMBEDDED_ONLY ||
                (languages.isNotEmpty() && languages.all { it in installedLanguages })
}

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val extractDocumentText: ExtractDocumentTextUseCase,
    private val ocrRegistry: OcrEngineRegistry,
    private val modelManager: OcrModelManager,
    private val paddleModelManager: PaddleModelManager,
    private val llmSettingsStore: LlmSettingsStore,
    private val fileRepository: FileRepository,
    private val workManager: WorkManager,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<OcrResultView>>(OperationState.Idle)
    val operation: StateFlow<OperationState<OcrResultView>> = _operation.asStateFlow()

    /** Shared status for model actions: Tesseract download, Paddle download, LLM test. */
    private val _modelOperation = MutableStateFlow<OperationState<String>>(OperationState.Idle)
    val modelOperation: StateFlow<OperationState<String>> = _modelOperation.asStateFlow()

    private var observeJob: Job? = null

    init {
        _uiState.update {
            it.copy(
                availableEngines = ocrRegistry.engineTypes,
                llmSettings = llmSettingsStore.get(),
            )
        }
        refreshInstalledLanguages()
        refreshPaddleStatus()
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

    // --- Tesseract models ---

    fun downloadModels(variant: OcrModelVariant = OcrModelVariant.FAST) {
        val state = _uiState.value
        val missing = state.languages.filter { it !in state.installedLanguages }
        if (missing.isEmpty()) {
            _modelOperation.value = OperationState.Failure("選択中の言語はすべて取込済みです")
            return
        }
        viewModelScope.launch {
            _modelOperation.value = OperationState.Running(null, "ダウンロード準備中…")
            runCatching {
                withContext(dispatchers.io) {
                    missing.forEachIndexed { index, language ->
                        modelManager.downloadLanguage(language, variant) { fraction ->
                            _modelOperation.value = OperationState.Running(
                                fraction, "$language をダウンロード中… (${index + 1}/${missing.size})",
                            )
                        }
                    }
                }
            }.onSuccess {
                refreshInstalledLanguages()
                _modelOperation.value = OperationState.Success("取込完了: ${missing.joinToString("+")}")
            }.onFailure {
                _modelOperation.value = OperationState.Failure(it.message ?: "ダウンロードに失敗しました", it)
            }
        }
    }

    fun importModels(treeUri: Uri) {
        viewModelScope.launch {
            val count = withContext(dispatchers.io) { modelManager.importFromTree(treeUri) }
            refreshInstalledLanguages()
            _modelOperation.value = if (count > 0) {
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

    // --- LLM settings ---

    fun onLlmApiTypeChanged(apiType: LlmApiType) = updateLlm { it.copy(apiType = apiType) }
    fun onLlmBaseUrlChanged(url: String) = updateLlm { it.copy(baseUrl = url) }
    fun onLlmModelChanged(model: String) = updateLlm { it.copy(model = model) }
    fun onLlmApiKeyChanged(key: String) = updateLlm { it.copy(apiKey = key) }

    private fun updateLlm(transform: (LlmSettings) -> LlmSettings) {
        val updated = transform(_uiState.value.llmSettings)
        llmSettingsStore.save(updated)
        _uiState.update { it.copy(llmSettings = updated) }
    }

    fun testLlmConnection() {
        viewModelScope.launch {
            _modelOperation.value = OperationState.Running(null, "接続確認中…")
            val available = runCatching {
                ocrRegistry.engine(OcrEngineType.LLM_VISION).isAvailable(_uiState.value.languages)
            }.getOrDefault(false)
            _modelOperation.value = if (available) {
                OperationState.Success("接続 OK（サーバに到達しました）")
            } else {
                OperationState.Failure("接続できません。URL・モデル名・サーバ起動を確認してください。")
            }
        }
    }

    // --- Paddle models ---

    fun downloadPaddleModels() {
        viewModelScope.launch {
            _modelOperation.value = OperationState.Running(null, "ダウンロード準備中…")
            runCatching {
                withContext(dispatchers.io) {
                    paddleModelManager.downloadAll { fileName, fraction ->
                        _modelOperation.value = OperationState.Running(fraction, "$fileName をダウンロード中…")
                    }
                }
            }.onSuccess {
                refreshPaddleStatus()
                _modelOperation.value = OperationState.Success("PaddleOCR モデルを取得しました")
            }.onFailure {
                _modelOperation.value = OperationState.Failure(it.message ?: "ダウンロードに失敗しました", it)
            }
        }
    }

    private fun refreshPaddleStatus() {
        viewModelScope.launch {
            val downloaded = withContext(dispatchers.io) { paddleModelManager.isDownloaded() }
            _uiState.update { it.copy(paddleDownloaded = downloaded) }
        }
    }

    // --- Run ---

    fun run() {
        val state = _uiState.value
        val source = state.source ?: return
        if (state.engine == OcrEngineType.TESSERACT &&
            state.languages.isEmpty() && state.mode != TextExtractionMode.EMBEDDED_ONLY
        ) {
            _operation.value = OperationState.Failure("言語を 1 つ以上選択してください")
            return
        }
        if (state.runInBackground) runInBackground(state, source) else runInline(state, source)
    }

    private fun runInline(state: OcrUiState, source: Uri) {
        observeJob?.cancel()
        viewModelScope.launch {
            _operation.value = OperationState.Running(label = "解析中…")
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
        _operation.value = OperationState.Running(label = "バックグラウンドで解析中…")

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(PdfProcessingWorker.KEY_PROGRESS, 0)
                        _operation.value = OperationState.Running(progress / 100f, "バックグラウンドで解析中…")
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val uriString = info.outputData.getString(PdfProcessingWorker.KEY_RESULT_URI)
                        loadBackgroundResult(uriString)
                    }

                    WorkInfo.State.FAILED -> {
                        val message = info.outputData.getString(PdfProcessingWorker.KEY_ERROR) ?: "失敗しました"
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
            _operation.value = OperationState.Failure("結果を読み込めませんでした")
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
            "${t.message}\n『ダウンロード』または『取り込み』からモデルを追加してください。"
        is OcrEngineNotImplementedException -> t.message ?: "未実装のエンジンです"
        else -> t.message ?: "失敗しました"
    }
}
