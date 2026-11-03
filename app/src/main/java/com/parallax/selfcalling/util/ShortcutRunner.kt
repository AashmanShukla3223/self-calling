package com.parallax.selfcalling.util

import android.content.Context

object ShortcutRunner {
    /** Schedules every step of a set as an independent simulated incoming call. */
    @JvmStatic
    fun run(c: Context, setId: Int): Boolean {
        val set = ShortcutSets.get(c, setId) ?: return false
        if (set.steps.isEmpty()) return false
        for (step in set.steps) {
            val delay = if (step.delaySec < 0) 0 else step.delaySec
            CallScheduler.fireIncoming(c, delay, step.snapshot, step.ringTimeoutSec)
        }
        return true
    }
}
