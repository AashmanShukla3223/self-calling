package com.parallax.selfcalling.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.parallax.selfcalling.util.CallScheduler

/** Fired by AlarmManager when a delayed simulated incoming call is due. Kotlin. */
class CallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, i: Intent) {
        // marker cleared per-fired id below
        // Hand the actual Telecom request to a foreground service: Telecom is friendlier to
        // incoming calls placed by a foregrounded process, and this survives long waits.
        val s = Intent(c, FgsIncomingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s) else c.startService(s)
        } catch (e: Exception) {
            android.util.Log.w("SelfCall", "receiver: ${e.message}")
        }
    }
}
