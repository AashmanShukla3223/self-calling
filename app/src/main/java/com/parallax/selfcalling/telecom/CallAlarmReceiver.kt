package com.parallax.selfcalling.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.parallax.selfcalling.util.CallScheduler
import com.parallax.selfcalling.util.Prefs

/** Fired by AlarmManager when a delayed simulated incoming call is due. Kotlin. */
class CallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val preset = i.getIntExtra("preset", Prefs.activeIndex(c))
        if (id >= 0) CallScheduler.clearFired(c, id)
        // Hand the actual Telecom request to a foreground service: Telecom is friendlier to
        // incoming calls placed by a foregrounded process, and this survives long waits.
        val s = Intent(c, FgsIncomingService::class.java)
            .putExtra(FgsIncomingService.EXTRA_PRESET_INDEX, preset)
            .putExtra("id", id)
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s) else c.startService(s)
        } catch (e: Exception) {
            android.util.Log.w("SelfCall", "receiver: ${e.message}")
        }
    }
}