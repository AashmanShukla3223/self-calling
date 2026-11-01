package com.parallax.selfcalling.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.parallax.selfcalling.R
import com.parallax.selfcalling.util.CallScheduler

/**
 * Foreground wait + fire: rings the simulated incoming call after an exact Handler delay
 * (short waits) or immediately when a long AlarmManager wait is due. Kotlin.
 */
class FgsIncomingService : Service() {

    private val h = Handler(Looper.getMainLooper())
    private val fire = Runnable {
        try {
            TelecomHelper.simulateIncoming(this)
        } catch (e: Exception) {
            android.util.Log.w("SelfCall", "delayed incoming: ${e.message}")
        } finally {
            CallScheduler.clearMarker(this)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            h.removeCallbacks(fire)
            CallScheduler.clearMarker(this)
            stopSelf()
            return START_NOT_STICKY
        }
        h.removeCallbacks(fire)
        val delayMs = intent?.getLongExtra(EXTRA_DELAY_MS, 0L) ?: 0L
        goForeground(delayMs)
        h.postDelayed(fire, delayMs.coerceAtLeast(0L))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(fire)
        super.onDestroy()
    }

    private fun goForeground(delayMs: Long) {
        val title = getString(R.string.app_name)
        val text = if (delayMs > 0) {
            getString(R.string.toast_scheduled, CallScheduler.fmtLeft(delayMs))
        } else {
            getString(R.string.pending_firing)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                    NotificationChannel(
                        "selfcall_pending", title, NotificationManager.IMPORTANCE_LOW))
            } catch (_: Exception) {
            }
        }
        val n: Notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "selfcall_pending")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        }
        if (Build.VERSION.SDK_INT >= 34 && delayMs > 0) {
            startForeground(2001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(2001, n)
        }
    }

    companion object {
        const val EXTRA_DELAY_MS = "delay_ms"
        const val ACTION_CANCEL = "com.parallax.selfcalling.CANCEL_WAIT"
    }
}
