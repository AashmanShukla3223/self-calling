package com.parallax.selfcalling.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.parallax.selfcalling.R
import com.parallax.selfcalling.telecom.CallAlarmReceiver
import com.parallax.selfcalling.telecom.FgsIncomingService
import com.parallax.selfcalling.telecom.TelecomHelper

/**
 * Schedules "Simulate incoming call". Short waits use an exact Handler in a foreground
 * service so the ring is on-time; long waits use AlarmManager so they survive process death.
 */
object CallScheduler {

    /** Handler path stays exact; alarms cover waits longer than this. */
    private const val FAST_MAX_SEC = 180
    private const val KEY_AT = "incoming_at"

    private fun sp(c: Context) =
        c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)

    private fun alarmOp(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 1001,
        Intent(c, CallAlarmReceiver::class.java)
            .setAction("com.parallax.selfcalling.FIRE_INCOMING"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun startWaitService(c: Context, delayMs: Long) {
        val s = Intent(c, FgsIncomingService::class.java)
            .putExtra(FgsIncomingService.EXTRA_DELAY_MS, delayMs)
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s) else c.startService(s)
    }

    /** Opens system exact-alarm settings when a long delayed ring would miss the clock. */
    @JvmStatic
    fun ensureExactAlarms(c: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        if (am.canScheduleExactAlarms()) return
        Toast.makeText(c, R.string.exact_alarm_needed, Toast.LENGTH_LONG).show()
        try {
            c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    /** 0 rings at once; short waits fire on a Handler; longer waits arm an exact alarm. */
    @JvmStatic
    fun fireIncoming(c: Context, delaySec: Int) {
        cancel(c)
        if (delaySec <= 0) {
            TelecomHelper.simulateIncoming(c)
            return
        }
        val at = System.currentTimeMillis() + delaySec * 1000L
        sp(c).edit().putLong(KEY_AT, at).apply()
        if (delaySec <= FAST_MAX_SEC) {
            startWaitService(c, delaySec * 1000L)
            return
        }
        ensureExactAlarms(c)
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmOp(c))
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 15_000, alarmOp(c))
        }
    }

    /** Cancels the pending wait (Handler service and/or alarm) and clears the marker. */
    @JvmStatic
    fun cancel(c: Context) {
        try {
            c.getSystemService(AlarmManager::class.java)?.cancel(alarmOp(c))
        } catch (_: Exception) {
        }
        try {
            c.stopService(Intent(c, FgsIncomingService::class.java)
                .setAction(FgsIncomingService.ACTION_CANCEL))
        } catch (_: Exception) {
        }
        clearMarker(c)
    }

    /** Epoch ms the pending incoming call will fire at; 0 = none. */
    @JvmStatic
    fun pendingAt(c: Context): Long = sp(c).getLong(KEY_AT, 0)

    /** Clears the stored marker without touching the alarm (receiver calls this at fire time). */
    @JvmStatic
    fun clearMarker(c: Context) {
        sp(c).edit().remove(KEY_AT).apply()
    }

    /** "in 3s" / "in 12min" / "in 2h 05min" — time left until the pending call fires. */
    @JvmStatic
    fun fmtLeft(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return when {
            s < 60 -> "in ${s}s"
            s < 3600 -> "in ${s / 60}min"
            else -> {
                val h = s / 3600
                val m = (s % 3600) / 60
                if (m == 0L) "in ${h}h" else "in ${h}h ${m}min"
            }
        }
    }
}
