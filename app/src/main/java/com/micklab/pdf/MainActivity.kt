package com.micklab.pdf

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.MobileAds
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.ui.ads.ConsentManager
import com.micklab.pdf.ui.navigation.PdfApp
import com.micklab.pdf.ui.theme.PdfToolsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Registered ahead of time (recommended pattern). Result is ignored: if the
    // user declines, saving falls back to a SAF folder or app storage.
    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // まず UMP 同意を取得し、広告リクエストが許可されてから AdMob を初期化する。
        ConsentManager.gatherConsent(this) {
            Thread { MobileAds.initialize(applicationContext) }.start()
        }
        enableEdgeToEdge()
        maybeRequestLegacyStoragePermission()
        setContent {
            PdfToolsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PdfApp()
                }
            }
        }
    }

    /** Android 9 and below need WRITE_EXTERNAL_STORAGE to save into Download/. */
    private fun maybeRequestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
