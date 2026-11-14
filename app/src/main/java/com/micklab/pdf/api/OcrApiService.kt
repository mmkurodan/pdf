package com.micklab.pdf.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.micklab.pdf.MainActivity
import com.micklab.pdf.R
import com.micklab.pdf.core.ExpertSettings
import com.micklab.pdf.domain.ocr.OcrEngineRegistry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service hosting [OcrApiServer]. Designed to stay up permanently once
 * enabled, the same way OllamaForegroundService does in the companion llama app:
 *
 *  - the "enabled" intent is persisted, so it is re-asserted after process death
 *    (START_STICKY), task removal, reboot ([BootReceiver]) and by the watchdog alarm
 *    ([WatchdogReceiver]) until someone explicitly stops it;
 *  - the service is **exported**: other apps switch the API on/off with an explicit
 *    Intent — [ACTION_START] (optional int extra [EXTRA_PORT]) or [ACTION_STOP] —
 *    sent through `startForegroundService()`.
 */
@AndroidEntryPoint
class OcrApiService : Service() {

    @Inject lateinit var ocrRegistry: OcrEngineRegistry
    @Inject lateinit var expertSettings: ExpertSettings

    private var server: OcrApiServer? = null
    private var serverPort = -1
    private var powerLocks: PowerLocks? = null

    companion object {
        private const val TAG = "OcrApiService"
        const val CHANNEL_ID = "ocr_api_channel"
        const val NOTIF_ID = 2001

        /** Public contract for other apps (mirrors com.micklab.llama.START_SERVICE). */
        const val ACTION_START = "com.micklab.pdf.START_SERVICE"
        const val ACTION_STOP = "com.micklab.pdf.STOP_SERVICE"
        const val EXTRA_PORT = "port"

        /** Liveness in *this* process; false again in a freshly restarted one. */
        @Volatile
        var isServiceRunning = false
            private set

        fun startIntent(ctx: Context, port: Int? = null): Intent =
            Intent(ctx, OcrApiService::class.java).setAction(ACTION_START)
                .apply { if (port != null) putExtra(EXTRA_PORT, port) }

        fun stopIntent(ctx: Context): Intent =
            Intent(ctx, OcrApiService::class.java).setAction(ACTION_STOP)

        fun isApiEnabled(ctx: Context) = ExpertSettings(ctx).apiEnabled

        /**
         * Starts the service iff the API was left enabled. Safe from app launch, boot
         * and the watchdog: a refused background start is swallowed so a later
         * foreground launch can re-assert.
         */
        fun startIfEnabled(ctx: Context) {
            if (!isApiEnabled(ctx)) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(startIntent(ctx))
                } else {
                    ctx.startService(startIntent(ctx))
                }
            } catch (e: Exception) {
                Log.w(TAG, "startIfEnabled: start not allowed right now", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        powerLocks = PowerLocks(this, "OcrApi")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Explicit disable: clear the persisted intent so nothing resurrects us.
            expertSettings.apiEnabled = false
            RecoveryScheduler.cancel(this)
            OcrApiState.update { it.copy(enabled = false) }
            // A caller using startForegroundService() obliges us to enter the foreground
            // once, even when the request is "stop" and the service was not running.
            runCatching { startForeground(NOTIF_ID, buildNotif(getString(R.string.expert_notif_stopped))) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // A sticky restart delivers a null intent (extras lost): use the persisted port.
        val requested = intent?.getIntExtra(EXTRA_PORT, -1) ?: -1
        if (requested in ExpertSettings.PORT_RANGE) expertSettings.port = requested
        val port = expertSettings.port

        expertSettings.apiEnabled = true
        OcrApiState.update { it.copy(enabled = true, port = port) }

        try {
            startForeground(NOTIF_ID, buildNotif(getString(R.string.expert_notif_starting)))
        } catch (e: Exception) {
            // Background-start restriction (e.g. watchdog without battery exemption):
            // stay "enabled" and let the next launch / tick try again.
            Log.w(TAG, "startForeground not allowed in this context", e)
            stopSelf()
            return START_STICKY
        }

        isServiceRunning = true
        startServer(port)
        RecoveryScheduler.schedule(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped from recents: some OEMs kill without a sticky restart, so re-request.
        startIfEnabled(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false
        server?.stop(); server = null
        powerLocks?.releaseAll()
        OcrApiState.update { it.copy(running = false) }
        super.onDestroy()
    }

    private fun startServer(port: Int) {
        if (server?.isRunning() == true && serverPort == port) return
        server?.stop()
        serverPort = port
        server = OcrApiServer(this, ocrRegistry).apply {
            setPort(port)
            setListener(object : OcrApiServer.ServerListener {
                override fun onServerStarted(p: Int) {
                    OcrApiState.update { it.copy(running = true, port = p, error = null) }
                    OcrApiState.log("OCR API started on port $p")
                    updateNotif(getString(R.string.expert_notif_running, p))
                }
                override fun onServerStopped() {
                    OcrApiState.update { it.copy(running = false) }
                    OcrApiState.log("OCR API stopped")
                }
                override fun onServerError(msg: String) {
                    Log.e(TAG, "Server error: $msg")
                    OcrApiState.update { it.copy(running = false, error = msg) }
                    OcrApiState.log("Error: $msg")
                    updateNotif(getString(R.string.expert_notif_error, msg))
                }
                override fun onRequest(method: String, path: String) =
                    OcrApiState.log("$method $path")
                override fun onBusy(busy: Boolean) {
                    if (busy) powerLocks?.acquire() else powerLocks?.release()
                }
            })
            start()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.expert_channel_name),
                NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.expert_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(content: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.expert_notif_title))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.expert_notif_action_stop), stop)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(content: String) {
        if (!isServiceRunning) return
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotif(content))
    }
}
