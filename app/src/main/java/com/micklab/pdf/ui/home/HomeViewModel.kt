package com.micklab.pdf.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micklab.pdf.core.AppPreferences
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.domain.ocr.OcrModelManager
import com.micklab.pdf.domain.ocr.PaddleModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the home screen's one-time "no OCR models downloaded" prompt: on first
 * composition it checks — off the main thread — whether *neither* Tesseract nor
 * PaddleOCR has any model, and if so (and the user hasn't opted out) asks to open
 * settings.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tesseractModels: OcrModelManager,
    private val paddleModels: PaddleModelManager,
    private val appPreferences: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _showModelPrompt = MutableStateFlow(false)
    val showModelPrompt: StateFlow<Boolean> = _showModelPrompt.asStateFlow()

    init {
        if (!appPreferences.modelPromptDismissed) {
            viewModelScope.launch {
                val noModels = withContext(dispatchers.io) {
                    tesseractModels.availableLanguages().isEmpty() && !paddleModels.isAnyDownloaded()
                }
                if (noModels) _showModelPrompt.value = true
            }
        }
    }

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
