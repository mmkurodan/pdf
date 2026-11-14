package com.micklab.pdf.api

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

/**
 * A partial wake lock + Wi-Fi lock held while an OCR request is being processed, so
 * a screen-off does not stall recognition half way. Ported from PowerLocks.java in
 * the companion llama project: the underlying locks are not reference counted, this
 * class counts holders itself so concurrent requests and stray releases are safe.
 */
class PowerLocks(context: Context, tag: String) {
    private val wakeLock: PowerManager.WakeLock? =
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$tag:wake")
            ?.apply { setReferenceCounted(false) }

    @Suppress("DEPRECATION") // FULL_HIGH_PERF is the mode that still works with the screen off.
    private val wifiLock: WifiManager.WifiLock? =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$tag:wifi")
            ?.apply { setReferenceCounted(false) }

    private var count = 0

    @Synchronized
    fun acquire() {
        if (count++ == 0) {
            runCatching { wakeLock?.takeIf { !it.isHeld }?.acquire() }
                .onFailure { Log.w(TAG, "wake lock acquire failed", it) }
            runCatching { wifiLock?.takeIf { !it.isHeld }?.acquire() }
                .onFailure { Log.w(TAG, "wifi lock acquire failed", it) }
        }
    }

    @Synchronized
    fun release() {
        if (count > 0 && --count == 0) rawRelease()
    }

    @Synchronized
    fun releaseAll() {
        count = 0
        rawRelease()
    }

    private fun rawRelease() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
    }

    private companion object { const val TAG = "PowerLocks" }
}
