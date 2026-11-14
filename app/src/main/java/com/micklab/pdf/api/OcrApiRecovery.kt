package com.micklab.pdf.api

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Re-asserts the OCR API after a reboot when it was left enabled. If the OS refuses
 * the background start, [OcrApiService.startIfEnabled] fails quietly and the next
 * app launch brings the server back.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" ->
                OcrApiService.startIfEnabled(context.applicationContext)
        }
    }
}

/**
 * Target of the self-re-arming watchdog alarm. Because it is manifest-registered,
 * the OS cold-starts a fresh process to deliver the tick even after the previous
 * one was killed (OOM, crash, task removal) — in which case the service is not
 * running here and gets restarted.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        if (!OcrApiService.isApiEnabled(app)) {
            RecoveryScheduler.cancel(app)
            return
        }
        if (!OcrApiService.isServiceRunning) {
            Log.i("OcrApiWatchdog", "API enabled but service not running; restarting")
            OcrApiService.startIfEnabled(app)
        }
        RecoveryScheduler.schedule(app)
    }
}

/**
 * Alarm chain behind [WatchdogReceiver] (same idea as RecoveryScheduler.java in the
 * llama project, minus the proactive process recycle).
 *
 * Limits: a force-stop cancels alarms, so nothing recovers from that; and on
 * Android 12+ a restart from the background is only permitted when the app is
 * exempt from battery optimization — the home screen offers that exemption.
 */
object RecoveryScheduler {
    private const val ACTION_TICK = "com.micklab.pdf.RECOVERY_TICK"
    private const val TICK_INTERVAL_MS = 60_000L
    private const val REQUEST_CODE = 0xA11

    private fun tickIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, REQUEST_CODE,
        Intent(c, WatchdogReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // Inexact + allow-while-idle: fires through Doze without SCHEDULE_EXACT_ALARM.
        runCatching {
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + TICK_INTERVAL_MS,
                tickIntent(c),
            )
        }.onFailure { Log.w("RecoveryScheduler", "schedule failed", it) }
    }

    fun cancel(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(tickIntent(c)) }
    }
}
