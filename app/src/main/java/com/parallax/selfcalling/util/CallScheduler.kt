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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Schedules "Simulate incoming call". Each call is independent: scheduling on one preset does
 * not cancel pending calls on other presets. Waits survive process death via AlarmManager.
 */
object CallScheduler {

    private const val KEY_PENDING = "incoming_pending"
    private const val KEY_NEXT_ID = "incoming_next_id"

    data class Pending(val id: Int, val at: Long, val preset: Preset, val ringTimeout: Int = -1)

    private fun sp(c: Context) = c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)

    private fun load(c: Context): MutableList<Pending> {
        val out = mutableListOf<Pending>()
        try {
            val arr = JSONArray(sp(c).getString(KEY_PENDING, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val preset = if (o.has("preset") && o.get("preset") is JSONObject) {
                    Preset.fromJson(o.getJSONObject("preset"))
                } else {
                    val idx = o.optInt("presetIndex", Prefs.activeIndex(c))
                    val all = Prefs.presets(c)
                    all.getOrElse(idx) { Prefs.activePreset(c) }
                }
                out.add(Pending(o.getInt("id"), o.getLong("at"), preset, o.optInt("ringTimeout", -1)))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(c: Context, list: List<Pending>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("at", p.at)
            o.put("preset", p.preset.toJson())
            o.put("ringTimeout", p.ringTimeout)
            arr.put(o)
        }
        sp(c).edit().putString(KEY_PENDING, arr.toString()).apply()
    }

    private fun nextId(c: Context): Int {
        val n = sp(c).getInt(KEY_NEXT_ID, 1)
        sp(c).edit().putInt(KEY_NEXT_ID, n + 1).apply()
        return n
    }

    private fun alarmOp(c: Context, id: Int, preset: Preset, ringTimeout: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, id,
            Intent(c, CallAlarmReceiver::class.java)
                .setAction("com.parallax.selfcalling.FIRE_INCOMING")
                .putExtra("id", id)
                .putExtra("preset_snapshot", preset.toJson().toString())
                .putExtra("ring_timeout", ringTimeout),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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

    /** Schedules a new independent incoming call; does not cancel other pending ones. */
    @JvmStatic
    fun fireIncoming(c: Context, delaySec: Int) {
        fireIncoming(c, delaySec, Prefs.activePreset(c), -1)
    }

    /** Schedules a new independent incoming call for an explicit (possibly snapshotted) preset. */
    @JvmStatic
    @JvmOverloads
    fun fireIncoming(c: Context, delaySec: Int, preset: Preset, ringTimeout: Int = -1) {
        val at = System.currentTimeMillis() + delaySec * 1000L
        val id = nextId(c)
        val list = load(c)
        list.add(Pending(id, at, preset, ringTimeout))
        save(c, list)

        if (delaySec <= 0) {
            TelecomHelper.simulateIncoming(c, preset, ringTimeout)
            save(c, load(c).filter { it.id != id })
            return
        }

        if (delaySec <= 180) {
            // short wait: use foreground service for on-time ring
            val s = Intent(c, FgsIncomingService::class.java)
                .putExtra(FgsIncomingService.EXTRA_DELAY_MS, delaySec * 1000L)
                .putExtra(FgsIncomingService.EXTRA_PRESET_SNAPSHOT, preset.toJson().toString())
                .putExtra(FgsIncomingService.EXTRA_RING_TIMEOUT, ringTimeout)
                .putExtra("id", id)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s) else c.startService(s)
            return
        }

        ensureExactAlarms(c)
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val op = alarmOp(c, id, preset, ringTimeout)
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 15_000, op)
        }
    }

    /** Cancels ALL pending incoming calls. */
    @JvmStatic
    fun cancel(c: Context) {
        for (p in load(c)) {
            try {
                c.getSystemService(AlarmManager::class.java)?.cancel(alarmOp(c, p.id, p.preset, p.ringTimeout))
            } catch (_: Exception) {
            }
        }
        try {
            c.stopService(Intent(c, FgsIncomingService::class.java)
                .setAction(FgsIncomingService.ACTION_CANCEL))
        } catch (_: Exception) {
        }
        save(c, emptyList())
    }

    /** Cancels one pending call by id. */
    @JvmStatic
    fun cancelOne(c: Context, id: Int) {
        val list = load(c)
        val target = list.firstOrNull { it.id == id } ?: return
        try {
            c.getSystemService(AlarmManager::class.java)?.cancel(alarmOp(c, target.id, target.preset, target.ringTimeout))
        } catch (_: Exception) {
        }
        save(c, list.filter { it.id != id })
    }

    /** Epoch ms of the earliest pending call; 0 = none. */
    @JvmStatic
    fun pendingAt(c: Context): Long {
        val list = load(c)
        return list.minByOrNull { it.at }?.at ?: 0L
    }

    /** Number of pending calls. */
    @JvmStatic
    fun pendingCount(c: Context): Int = load(c).size

    /** Returns the earliest pending call's preset (or active). */
    @JvmStatic
    fun pendingPreset(c: Context): Preset =
        load(c).minByOrNull { it.at }?.preset ?: Prefs.activePreset(c)

    /** Removes a fired pending entry by id. */
    @JvmStatic
    fun clearFired(c: Context, id: Int) {
        if (id < 0) return
        save(c, load(c).filter { it.id != id })
    }

    /** "in 3s" / "in 12min" / "in 2h 05min" — time left until the earliest pending call. */
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