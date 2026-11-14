package com.micklab.pdf.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.api.OcrApiService
import com.micklab.pdf.api.OcrApiState
import com.micklab.pdf.core.AppPreferences
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.ExpertSettings
import com.micklab.pdf.domain.ocr.OcrModelManager
import com.micklab.pdf.domain.ocr.PaddleModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import javax.inject.Inject

/** Port field + reachable addresses for the home screen's API card. */
data class ApiCardUiState(
    val portInput: String = ExpertSettings.DEFAULT_PORT.toString(),
    val portError: Boolean = false,
    val localIps: List<String> = emptyList(),
    val batteryExempt: Boolean = true,
)

/**
 * Backs the home screen: the OCR API server card (start/stop, port, endpoints,
 * battery exemption) and the one-time "no OCR models downloaded" prompt.
 *
 * Whether the server is enabled/running comes from [OcrApiState], not from here,
 * because other apps, the notification and the boot/watchdog receivers can change
 * it behind the UI's back.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tesseractModels: OcrModelManager,
    private val paddleModels: PaddleModelManager,
    private val appPreferences: AppPreferences,
    private val expertSettings: ExpertSettings,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _showModelPrompt = MutableStateFlow(false)
    val showModelPrompt: StateFlow<Boolean> = _showModelPrompt.asStateFlow()

    private val _apiCard = MutableStateFlow(ApiCardUiState(portInput = expertSettings.port.toString()))
    val apiCard: StateFlow<ApiCardUiState> = _apiCard.asStateFlow()

    val apiStatus: StateFlow<OcrApiState.Status> = OcrApiState.status
    val apiLog: StateFlow<List<String>> = OcrApiState.log

    init {
        // Seed from the persisted intent: a fresh process has not heard from the service yet.
        OcrApiState.update { it.copy(enabled = expertSettings.apiEnabled, port = expertSettings.port) }
        refreshBatteryExemption()
        viewModelScope.launch {
            // Follow the server (incl. external start with another port): keep the
            // port field and the endpoint list in step with what is really serving.
            OcrApiState.status.map { it.enabled to it.port }.distinctUntilChanged().collect { (enabled, port) ->
                if (enabled) {
                    _apiCard.update { it.copy(portInput = port.toString(), portError = false) }
                    refreshLocalIps()
                } else {
                    _apiCard.update { it.copy(localIps = emptyList()) }
                }
            }
        }
        if (!appPreferences.modelPromptDismissed) {
            viewModelScope.launch {
                val noModels = withContext(dispatchers.io) {
                    tesseractModels.availableLanguages().isEmpty() && !paddleModels.isAnyDownloaded()
                }
                if (noModels) _showModelPrompt.value = true
            }
        }
    }

    // --- OCR API server ---

    fun onPortInputChanged(input: String) {
        val port = input.toIntOrNull()
        val valid = port != null && port in ExpertSettings.PORT_RANGE
        _apiCard.update { it.copy(portInput = input, portError = input.isNotEmpty() && !valid) }
        if (valid && port != null) expertSettings.port = port
    }

    /** Starts/stops the foreground service; the service persists the enabled intent. */
    fun setApiEnabled(enabled: Boolean) {
        if (enabled) {
            ContextCompat.startForegroundService(appContext, OcrApiService.startIntent(appContext, expertSettings.port))
        } else {
            // Persist first so the choice holds even if the service is not alive to hear it.
            expertSettings.apiEnabled = false
            OcrApiState.update { it.copy(enabled = false) }
            if (OcrApiService.isServiceRunning) appContext.startService(OcrApiService.stopIntent(appContext))
        }
    }

    fun clearLog() = OcrApiState.clearLog()

    fun refreshBatteryExemption() {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val exempt = pm?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true
        _apiCard.update { it.copy(batteryExempt = exempt) }
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
            _apiCard.update { it.copy(localIps = ips) }
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

    // --- "No OCR models" prompt ---

    /** "Later": close the prompt for this session only. */
    fun dismissPrompt() {
        _showModelPrompt.value = false
    }

    /** "Don't show again": persist the opt-out and close. */
    fun dontShowAgain() {
        appPreferences.modelPromptDismissed = true
        _showModelPrompt.value = false
    }
}
