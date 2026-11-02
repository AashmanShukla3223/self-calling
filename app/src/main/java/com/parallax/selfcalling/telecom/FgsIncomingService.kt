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
import com.parallax.selfcalling.util.Prefs

/**
 * Foreground wait + fire: rings one or more simulated incoming calls after exact Handler delays
 * (short waits) or immediately when a long AlarmManager wait is due. Supports multiple pending
 * calls (one per preset) without cancelling earlier ones. Kotlin.
 */
class FgsIncomingService : Service() {

    private val h = Handler(Looper.getMainLooper())
    private val entries = mutableListOf<FireEntry>()
    private var foregroundStarted = false

    data class FireEntry(val id: Int, val preset: Int, val runnable: Runnable)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelAll()
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getIntExtra("id", -1) ?: -1
        val preset = intent?.getIntExtra(EXTRA_PRESET_INDEX, Prefs.activeIndex(this)) ?: Prefs.activeIndex(this)
        val delayMs = intent?.getLongExtra(EXTRA_DELAY_MS, 0L) ?: 0L

        val runnable = Runnable {
            try {
                TelecomHelper.simulateIncoming(this, preset)
            } catch (e: Exception) {
                android.util.Log.w("SelfCall", "delayed incoming: ${e.message}")
            } finally {
                if (id >= 0) CallScheduler.clearFired(this, id)
                synchronized(entries) { entries.removeAll { it.id == id && it.preset == preset } }
                if (synchronized(entries) { entries.isEmpty() }) stopSelf()
            }
        }
        val entry = FireEntry(id, preset, runnable)
        synchronized(entries) { entries.add(entry) }

        if (!foregroundStarted) {
            goForeground(delayMs)
            foregroundStarted = true
        }
        h.postDelayed(runnable, delayMs.coerceAtLeast(0L))
        return START_NOT_STICKY
    }

    private fun cancelAll() {
        synchronized(entries) {
            for (e in entries) h.removeCallbacks(e.runnable)
            entries.clear()
        }
    }

    override fun onDestroy() {
        synchronized(entries) {
            for (e in entries) h.removeCallbacks(e.runnable)
            entries.clear()
        }
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
        const val EXTRA_PRESET_INDEX = "preset_index"
        const val ACTION_CANCEL = "com.parallax.selfcalling.CANCEL_WAIT"
    }
}