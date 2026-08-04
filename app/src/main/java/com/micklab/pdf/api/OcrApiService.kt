package com.micklab.pdf.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
 * Foreground service that keeps [OcrApiServer] running while the app is in the
 * background. Modelled after OllamaForegroundService.java in the companion llama
 * project: START_STICKY so the OS re-starts it after process kill, with the
 * sticky notification acting as the user-visible "server running" indicator.
 */
@AndroidEntryPoint
class OcrApiService : Service() {

    @Inject lateinit var ocrRegistry: OcrEngineRegistry
    @Inject lateinit var expertSettings: ExpertSettings

    private var server: OcrApiServer? = null

    companion object {
        private const val TAG = "OcrApiService"
        const val CHANNEL_ID  = "ocr_api_channel"
        const val NOTIF_ID    = 2001
        const val ACTION_STOP = "com.micklab.pdf.OCR_API_STOP"
        const val EXTRA_PORT  = "port"

        fun startIntent(ctx: Context, port: Int) =
            Intent(ctx, OcrApiService::class.java).putExtra(EXTRA_PORT, port)

        fun stopIntent(ctx: Context) =
            Intent(ctx, OcrApiService::class.java).setAction(ACTION_STOP)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        // When restarted by the OS after kill, intent is null; read port from prefs.
        val port = intent?.getIntExtra(EXTRA_PORT, expertSettings.port) ?: expertSettings.port

        val notif = buildNotif(getString(R.string.expert_notif_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        startServer(port)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop(); server = null
        super.onDestroy()
    }

    private fun startServer(port: Int) {
        server?.stop()
        server = OcrApiServer(this, ocrRegistry).apply {
            setPort(port)
            setListener(object : OcrApiServer.ServerListener {
                override fun onServerStarted(p: Int) = updateNotif(getString(R.string.expert_notif_running, p))
                override fun onServerStopped()        = updateNotif(getString(R.string.expert_notif_stopped))
                override fun onServerError(msg: String) {
                    Log.e(TAG, "Server error: $msg")
                    updateNotif(getString(R.string.expert_notif_error, msg))
                }
                override fun onRequest(method: String, path: String) = Unit
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
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIF_ID, buildNotif(content))
    }
}
