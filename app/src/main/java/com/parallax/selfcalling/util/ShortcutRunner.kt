package com.parallax.selfcalling.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper

object ShortcutRunner {

    /** Result of attempting to run a set. */
    enum class RunResult {
        OK,               // everything sent / scheduled
        FAILED            // set not found or empty
    }

    /** Whether the set contains at least one message step. */
    @JvmStatic
    fun hasMessageSteps(c: Context, setId: Int): Boolean {
        val set = ShortcutSets.get(c, setId) ?: return false
        return set.steps.any { it.type == ShortcutStep.TYPE_MESSAGE }
    }

    /** Schedules every step of a set: calls via CallScheduler, messages via native Messages app intent. */
    @JvmStatic
    fun run(c: Context, setId: Int): RunResult {
        val set = ShortcutSets.get(c, setId) ?: return RunResult.FAILED
        if (set.steps.isEmpty()) return RunResult.FAILED

        for (step in set.steps) {
            val delay = if (step.delaySec < 0) 0 else step.delaySec
            if (step.type == ShortcutStep.TYPE_MESSAGE) {
                openNativeMessages(c, delay, step.snapshot.number, step.messageText)
            } else {
                CallScheduler.fireIncoming(c, delay, step.snapshot, step.ringTimeoutSec)
            }
        }
        return RunResult.OK
    }

    /** Opens the native Messages app with recipient + body pre-filled (after optional delay). */
    private fun openNativeMessages(c: Context, delaySec: Int, number: String, text: String) {
        val send = {
            val i = Intent(Intent.ACTION_SENDTO)
            i.data = Uri.parse("smsto:" + number)
            i.putExtra("sms_body", text)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        }
        if (delaySec <= 0) {
            send()
        } else {
            Handler(Looper.getMainLooper()).postDelayed(send, delaySec * 1000L)
        }
    }
}
