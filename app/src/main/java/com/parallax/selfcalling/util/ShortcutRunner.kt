package com.parallax.selfcalling.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import com.parallax.selfcalling.telecom.TelecomHelper

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

    /** Schedules every step of a set: calls via CallScheduler, messages via native Messages app,
     *  outgoing calls via Telecom. */
    @JvmStatic
    fun run(c: Context, setId: Int): RunResult {
        val set = ShortcutSets.get(c, setId) ?: return RunResult.FAILED
        if (set.steps.isEmpty()) return RunResult.FAILED

        for (step in set.steps) {
            val delay = if (step.delaySec < 0) 0 else step.delaySec
            when (step.type) {
                ShortcutStep.TYPE_MESSAGE -> {
                    openNativeMessages(c, delay, step.snapshot.number, step.messageText)
                }
                ShortcutStep.TYPE_OUTGOING -> {
                    placeOutgoingCall(c, delay, step.snapshot)
                }
                else -> {
                    // TYPE_CALL: simulated incoming call
                    CallScheduler.fireIncoming(c, delay, step.snapshot, step.ringTimeoutSec)
                }
            }
        }
        return RunResult.OK
    }

    private fun placeOutgoingCall(c: Context, delaySec: Int, preset: Preset) {
        val send = {
            TelecomHelper.connectSelfCall(c, preset)
        }
        if (delaySec <= 0) {
            send()
        } else {
            Handler(Looper.getMainLooper()).postDelayed(send, delaySec * 1000L)
        }
    }

    /** Cleans a number to digits/+ only for the SMS URI. */
    private fun cleanNumber(raw: String?): String {
        var n = raw?.replace(Regex("[^0-9+]"), "") ?: ""
        return if (n.isEmpty()) "" else n
    }

    /** Opens the native Messages app with recipient + body pre-filled (after optional delay).
     *  The user reviews and taps Send in the native Messages app. */
    private fun openNativeMessages(c: Context, delaySec: Int, number: String, text: String) {
        val clean = cleanNumber(number)
        val send = {
            var opened = false
            // Try 1: ACTION_VIEW sms: (works on most Samsung)
            try {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse("sms:" + clean)
                i.putExtra("sms_body", text)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                c.startActivity(i)
                opened = true
            } catch (e: Exception) {
                // fall through to next attempt
            }
            // Try 2: ACTION_SENDTO smsto:
            if (!opened) {
                try {
                    val i = Intent(Intent.ACTION_SENDTO)
                    i.data = Uri.parse("smsto:" + clean)
                    i.putExtra("sms_body", text)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    c.startActivity(i)
                    opened = true
                } catch (e: Exception) {
                    // fall through
                }
            }
            if (!opened) {
                Toast.makeText(c, "Could not open Messages app", Toast.LENGTH_LONG).show()
            }
        }
        if (delaySec <= 0) {
            send()
        } else {
            Handler(Looper.getMainLooper()).postDelayed(send, delaySec * 1000L)
        }
    }
}
