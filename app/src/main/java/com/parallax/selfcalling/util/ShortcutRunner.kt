package com.parallax.selfcalling.util

import android.content.Context
import android.telephony.SmsManager

object ShortcutRunner {
    /** Schedules every step of a set: calls via CallScheduler, messages via SmsManager. */
    @JvmStatic
    fun run(c: Context, setId: Int): Boolean {
        val set = ShortcutSets.get(c, setId) ?: return false
        if (set.steps.isEmpty()) return false
        for (step in set.steps) {
            val delay = if (step.delaySec < 0) 0 else step.delaySec
            if (step.type == ShortcutStep.TYPE_MESSAGE) {
                sendMessage(c, delay, step.snapshot.number, step.messageText)
            } else {
                CallScheduler.fireIncoming(c, delay, step.snapshot, step.ringTimeoutSec)
            }
        }
        return true
    }

    private fun sendMessage(c: Context, delaySec: Int, number: String, text: String) {
        if (delaySec <= 0) {
            SmsManager.getDefault().sendTextMessage(number, null, text, null, null)
        } else {
            // schedule after delay using a handler on the main looper
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                SmsManager.getDefault().sendTextMessage(number, null, text, null, null)
            }, delaySec * 1000L)
        }
    }
}
