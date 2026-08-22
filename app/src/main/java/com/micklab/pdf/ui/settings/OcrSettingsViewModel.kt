package com.micklab.pdf.ui.settings

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.R
import com.micklab.pdf.api.OcrApiService
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.ExpertSettings
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.edit.AppFont
import com.micklab.pdf.domain.edit.FontManager
import com.micklab.pdf.domain.ocr.LlmApiType
import com.micklab.pdf.domain.ocr.LlmClient
import com.micklab.pdf.domain.ocr.LlmSettings
import com.micklab.pdf.domain.ocr.LlmSettingsStore
import com.micklab.pdf.domain.ocr.LlmTesterLauncher
import com.micklab.pdf.domain.ocr.OcrModelManager
import com.micklab.pdf.domain.ocr.OcrModelVariant
import com.micklab.pdf.domain.ocr.PaddleModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import javax.inject.Inject

/** Model management + LLM connection settings, independent of OCR execution. */
data class OcrSettingsUiState(
    val downloadLanguages: List<String> = listOf("jpn", "eng"),
    val installedLanguages: Set<String> = emptySet(),
    val llmSettings: LlmSettings = LlmSettings(),
    val llmModels: List<String> = emptyList(),
    val llmApiAvailable: Boolean = false,
    val paddleDownloadLanguages: List<String> = listOf("jpn", "eng"),
    val paddleInstalledLanguages: Set<String> = emptySet(),
    val availableFontIds: Set<String> = emptySet(),
    // Expert mode
    val expertEnabled: Boolean = false,
    val expertPort: Int = ExpertSettings.DEFAULT_PORT,
    val expertPortInput: String = ExpertSettings.DEFAULT_PORT.toString(),
    val expertPortError: Boolean = false,
    val expertLocalIps: List<String> = emptyList(),
)

@HiltViewModel
class OcrSettingsViewModel @Inject constructor(
    private val modelManager: OcrModelManager,
    private val paddleModelManager: PaddleModelManager,
    private val fontManager: FontManager,
    private val llmSettingsStore: LlmSettingsStore,
    private val llmClient: LlmClient,
    private val llmTesterLauncher: LlmTesterLauncher,
    private val expertSettings: ExpertSettings,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrSettingsUiState())
    val uiState: StateFlow<OcrSettingsUiState> = _uiState.asStateFlow()

    private val _operation = MutableStateFlow<OperationState<String>>(OperationState.Idle)
    val operation: StateFlow<OperationState<String>> = _operation.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                llmSettings = llmSettingsStore.get(),
                expertEnabled = expertSettings.apiEnabled,
                expertPort = expertSettings.port,
                expertPortInput = expertSettings.port.toString(),
            )
        }
        refreshInstalledLanguages()
        refreshPaddleStatus()
        refreshLlmStatus()
        refreshFonts()
        if (expertSettings.apiEnabled) refreshLocalIps()
    }

    // --- Expert mode ---

    fun onExpertPortInputChanged(input: String) {
        val port = input.toIntOrNull()
        val valid = port != null && port in 1024..65535
        _uiState.update { it.copy(expertPortInput = input, expertPortError = input.isNotEmpty() && !valid) }
        if (valid && port != null) {
            expertSettings.port = port
            _uiState.update { it.copy(expertPort = port) }
        }
    }

    /**
     * Called from the Screen after notification permission is granted (or not needed).
     * Starts/stops the foreground service and persists the preference.
     */
    fun setExpertApiEnabled(enabled: Boolean) {
        expertSettings.apiEnabled = enabled
        _uiState.update { it.copy(expertEnabled = enabled) }
        if (enabled) {
            val port = expertSettings.port
            val intent = OcrApiService.startIntent(appContext, port)
            ContextCompat.startForegroundService(appContext, intent)
            refreshLocalIps()
        } else {
            appContext.startService(OcrApiService.stopIntent(appContext))
            _uiState.update { it.copy(expertLocalIps = emptyList()) }
        }
    }

    private fun refreshLocalIps() {
        viewModelScope.launch {
            val ips = withContext(dispatchers.io) {
                // Exclude only the cellular/carrier line's address (unreachable for a LAN
                // server); every other interface (Wi-Fi, tethering, VPN, Ethernet) is offered.
                val cellular = cellularIps()
                val result = mutableListOf<String>()
                runCatching {
                    val ifaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
                    for (iface in ifaces.toList()) {
                        if (!iface.isUp || iface.isLoopback) continue
                        for (addr in iface.inetAddresses.toList()) {
                            if (addr.isLoopbackAddress) continue
                            val ip = addr.hostAddress ?: continue
                            if (':' in ip) continue          // IPv4 only
                            if (ip in cellular) continue      // drop the carrier (mobile data) IP
                            result.add(ip)
                        }
                    }
                }
                result
            }
            _uiState.update { it.copy(expertLocalIps = ips) }
        }
    }

    /** IPv4 addresses bound to a cellular (mobile carrier) network, to be excluded from the list. */
    private fun cellularIps(): Set<String> {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptySet()
        val result = mutableSetOf<String>()
        runCatching {
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) continue
                val props = cm.getLinkProperties(network) ?: continue
                for (linkAddr in props.linkAddresses) {
                    val ip = linkAddr.address.hostAddress ?: continue
                    if (':' !in ip) result.add(ip)
                }
            }
        }
        return result
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
    fun onLlmTextModelChanged(model: String) = updateLlm { it.copy(textModel = model) }
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
            _uiState.update { it.copy(llmApiAvailable = available) }
            _operation.value = if (available) {
                OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_conn_ok))
            } else {
                OperationState.Failure(LocaleManager.string(appContext, R.string.vm_set_conn_failed))
            }
        }
    }

    /** Quietly refresh the API status tile (no progress/toast). */
    fun refreshLlmStatus() {
        viewModelScope.launch {
            val available = runCatching { llmClient.ping() }.getOrDefault(false)
            _uiState.update { it.copy(llmApiAvailable = available) }
        }
    }

    /** Launch the companion LLM Tester app (or its store page), then poll for the API. */
    fun launchLlmApi() {
        viewModelScope.launch {
            val port = llmTesterLauncher.resolvePort(_uiState.value.llmSettings.baseUrl)
            // startActivity/startForegroundService run on the (main) viewModelScope dispatcher.
            when (llmTesterLauncher.launch(port)) {
                LlmTesterLauncher.Result.LAUNCHED -> {
                    _operation.value = OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_llm_launched))
                    // Give the server a moment to come up, polling the status tile.
                    repeat(LLM_STATUS_POLLS) {
                        delay(LLM_STATUS_POLL_MS)
                        val available = runCatching { llmClient.ping() }.getOrDefault(false)
                        _uiState.update { it.copy(llmApiAvailable = available) }
                        if (available) return@launch
                    }
                }
                LlmTesterLauncher.Result.STORE_OPENED ->
                    _operation.value = OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_llm_store))
                LlmTesterLauncher.Result.FAILED ->
                    _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_set_llm_launch_failed))
            }
        }
    }

    // --- Paddle ---

    fun togglePaddleLanguage(language: String) = _uiState.update { state ->
        val languages = if (language in state.paddleDownloadLanguages) {
            state.paddleDownloadLanguages.filterNot { it == language }
        } else {
            state.paddleDownloadLanguages + language
        }
        state.copy(paddleDownloadLanguages = languages)
    }

    fun downloadPaddleModels() {
        val languages = _uiState.value.paddleDownloadLanguages
        if (languages.isEmpty()) {
            _operation.value = OperationState.Failure(LocaleManager.string(appContext, R.string.vm_set_pick_language))
            return
        }
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, LocaleManager.string(appContext, R.string.vm_set_preparing))
            runCatching {
                withContext(dispatchers.io) {
                    paddleModelManager.downloadLanguages(languages) { fileName, fraction ->
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
            val installed = withContext(dispatchers.io) { paddleModelManager.downloadedLanguages() }
            _uiState.update { it.copy(paddleInstalledLanguages = installed) }
        }
    }

    // --- Fonts (for PDF editing) ---

    fun downloadFont(fontId: String) {
        val font = AppFont.byId(fontId)
        viewModelScope.launch {
            _operation.value = OperationState.Running(null, LocaleManager.string(appContext, R.string.vm_set_preparing))
            runCatching {
                withContext(dispatchers.io) {
                    fontManager.download(font) { fraction ->
                        _operation.value = OperationState.Running(fraction, LocaleManager.string(appContext, R.string.vm_set_downloading_file, font.displayName))
                    }
                }
            }.onSuccess {
                refreshFonts()
                _operation.value = OperationState.Success(LocaleManager.string(appContext, R.string.vm_set_import_done, font.displayName))
            }.onFailure {
                _operation.value = OperationState.Failure(it.message ?: LocaleManager.string(appContext, R.string.vm_set_download_failed), it)
            }
        }
    }

    private fun refreshFonts() {
        viewModelScope.launch {
            val ids = withContext(dispatchers.io) { fontManager.availableIds() }
            _uiState.update { it.copy(availableFontIds = ids) }
        }
    }

    private companion object {
        const val LLM_STATUS_POLLS = 6
        const val LLM_STATUS_POLL_MS = 1_500L
    }
}
