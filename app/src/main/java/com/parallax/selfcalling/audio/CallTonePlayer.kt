package com.parallax.selfcalling.audio

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

/** Plays telecom-style tones: ringback while dialing, connect beep, end beep. Uses system
 *  ringtones/notifications so they are always audible. Kotlin. */
object CallTonePlayer {

    private val h = Handler(Looper.getMainLooper())
    private var ringbackOn = false
    private var ringbackRunnable: Runnable? = null
    private var ringbackRingtone: Ringtone? = null

    @Synchronized
    @JvmStatic
    fun startRingback(ctx: Context) {
        stopRingback()
        ringbackOn = true
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringbackRingtone = RingtoneManager.getRingtone(ctx, uri)
        val r = object : Runnable {
            override fun run() {
                if (!ringbackOn) return
                try {
                    ringbackRingtone?.play()
                    h.postDelayed({ try { ringbackRingtone?.stop() } catch (_: Exception) { } }, 1500)
                } catch (_: Exception) {
                }
                h.postDelayed(this, 2000)
            }
        }
        ringbackRunnable = r
        h.post(r)
    }

    @Synchronized
    @JvmStatic
    fun stopRingback() {
        ringbackOn = false
        ringbackRunnable?.let { h.removeCallbacks(it) }
        ringbackRunnable = null
        try { ringbackRingtone?.stop() } catch (_: Exception) { }
        ringbackRingtone = null
    }

    @Synchronized
    @JvmStatic
    fun playConnect(ctx: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(ctx, uri)
            r?.play()
            h.postDelayed({ try { r?.stop() } catch (_: Exception) { } }, 500)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    @JvmStatic
    fun playEnd(ctx: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(ctx, uri)
            r?.play()
            h.postDelayed({ try { r?.stop() } catch (_: Exception) { } }, 500)
        } catch (_: Exception) {
        }
    }
}