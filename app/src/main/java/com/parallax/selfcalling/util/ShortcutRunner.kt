package com.parallax.selfcalling.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager

object ShortcutRunner {

    const val SMS_PERMISSION_REQUEST = 1003

    /** Result of attempting to run a set. */
    enum class RunResult {
        OK,               // everything sent / scheduled
        NEEDS_SMS_PERM,   // set has message steps but SEND_SMS not granted
        FAILED            // set not found or empty
    }

    /** Whether the set contains at least one message step. */
    @JvmStatic
    fun hasMessageSteps(c: Context, setId: Int): Boolean {
        val set = ShortcutSets.get(c, setId) ?: return false
        return set.steps.any { it.type == ShortcutStep.TYPE_MESSAGE }
    }

    /** Whether SEND_SMS is already granted. */
    @JvmStatic
    fun hasSmsPermission(c: Context): Boolean =
        c.checkSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Request SEND_SMS from the given activity. The activity must handle onRequestPermissionsResult
     * and call run() again once granted.
     */
    @JvmStatic
    fun requestSmsPermission(a: android.app.Activity) {
        a.requestPermissions(
            arrayOf(android.Manifest.permission.SEND_SMS), SMS_PERMISSION_REQUEST)
    }

    /** Schedules every step of a set: calls via CallScheduler, messages via SmsManager. */
    @JvmStatic
    fun run(c: Context, setId: Int): RunResult {
        val set = ShortcutSets.get(c, setId) ?: return RunResult.FAILED
        if (set.steps.isEmpty()) return RunResult.FAILED

        // if any message step and no SMS permission, bail out
        if (set.steps.any { it.type == ShortcutStep.TYPE_MESSAGE } && !hasSmsPermission(c)) {
            return RunResult.NEEDS_SMS_PERM
        }

        for (step in set.steps) {
            val delay = if (step.delaySec < 0) 0 else step.delaySec
            if (step.type == ShortcutStep.TYPE_MESSAGE) {
                sendMessage(c, delay, step.snapshot.number, step.messageText)
            } else {
                CallScheduler.fireIncoming(c, delay, step.snapshot, step.ringTimeoutSec)
            }
        }
        return RunResult.OK
    }

    private fun sendMessage(c: Context, delaySec: Int, number: String, text: String) {
        val send = { SmsManager.getDefault().sendTextMessage(number, null, text, null, null) }
        if (delaySec <= 0) {
            send()
        } else {
            Handler(Looper.getMainLooper()).postDelayed(send, delaySec * 1000L)
        }
    }
}
