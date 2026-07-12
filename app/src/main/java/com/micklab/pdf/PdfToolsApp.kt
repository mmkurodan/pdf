package com.micklab.pdf

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Responsibilities:
 *  - Bootstraps Hilt (`@HiltAndroidApp`).
 *  - Initializes PDFBox-Android's resource loader once, on the main process,
 *    before any [com.tom_roush.pdfbox.pdmodel.PDDocument] is touched.
 *  - Wires WorkManager to Hilt so `@HiltWorker` workers can be constructed with
 *    injected dependencies (see [com.micklab.pdf.worker.PdfProcessingWorker]).
 */
@HiltAndroidApp
class PdfToolsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // PDFBox needs a Context to load its embedded font/resource maps.
        PDFBoxResourceLoader.init(applicationContext)
        Log.i(TAG, "PdfToolsApp initialized; PDFBox resource loader ready.")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    companion object {
        const val TAG = "PdfTools"
    }
}
