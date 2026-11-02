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
 * Foreground wait + fire: rings one or more simulated incoming calls after exact Handler delays.
 * Each pending call is tracked independently (numbered) and the notification shows a live
 * countdown for the earliest one. Kotlin.
 */
class FgsIncomingService : Service() {

    private val h = Handler(Looper.getMainLooper())
    private val entries = mutableListOf<FireEntry>()
    private var foregroundStarted = false

    data class FireEntry(val id: Int, val preset: Int, val delayMs: Long, val fireAt: Long, val runnable: Runnable)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            synchronized(entries) {
                for (e in entries) h.removeCallbacks(e.runnable)
                entries.clear()
            }
            CallScheduler.cancel(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getIntExtra("id", -1) ?: -1
        val preset = intent?.getIntExtra(EXTRA_PRESET_INDEX, Prefs.activeIndex(this)) ?: Prefs.activeIndex(this)
        val delayMs = intent?.getLongExtra(EXTRA_DELAY_MS, 0L) ?: 0L
        val fireAt = System.currentTimeMillis() + delayMs.coerceAtLeast(0L)

        val runnable = Runnable {
            try {
                TelecomHelper.simulateIncoming(this, preset)
            } catch (e: Exception) {
                android.util.Log.w("SelfCall", "delayed incoming: ${e.message}")
            } finally {
                if (id >= 0) CallScheduler.clearFired(this, id)
                synchronized(entries) { entries.removeAll { it.runnable === runnable } }
                updateNotification()
                if (synchronized(entries) { entries.isEmpty() }) stopSelf()
            }
        }
        val entry = FireEntry(id, preset, delayMs, fireAt, runnable)
        synchronized(entries) { entries.add(entry) }

        h.postDelayed(runnable, delayMs.coerceAtLeast(0L))
        updateNotification()
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val snapshot = synchronized(entries) { entries.toList() }
        if (snapshot.isEmpty()) return
        val earliest = snapshot.minByOrNull { it.fireAt }!!
        val count = snapshot.size
        val title = getString(R.string.app_name)
        val text = if (count == 1) {
            "Incoming call in ${CallScheduler.fmtLeft(earliest.fireAt - System.currentTimeMillis())}"
        } else {
            "$count incoming calls scheduled (next in ${CallScheduler.fmtLeft(earliest.fireAt - System.currentTimeMillis())})"
        }
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                    NotificationChannel("selfcall_pending", title, NotificationManager.IMPORTANCE_LOW))
            } catch (_: Exception) {
            }
        }
        val n: Notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "selfcall_pending")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setWhen(earliest.fireAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setWhen(earliest.fireAt)
                .setUsesChronometer(true)
                .build()
        }
        if (!foregroundStarted) {
            if (Build.VERSION.SDK_INT >= 34 && earliest.delayMs > 0) {
                startForeground(2001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(2001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else {
                startForeground(2001, n)
            }
            foregroundStarted = true
        } else {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(2001, n)
        }
    }

    private var foregroundStarted = false

    override fun onDestroy() {
        synchronized(entries) {
            for (e in entries) h.removeCallbacks(e.runnable)
            entries.clear()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DELAY_MS = "delay_ms"
        const val EXTRA_PRESET_INDEX = "preset_index"
        const val ACTION_CANCEL = "com.parallax.selfcalling.CANCEL_WAIT"
    }
}