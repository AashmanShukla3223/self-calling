package com.parallax.selfcalling.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Plays telecom-style tones: ringback while dialing, connect beep, end beep. Kotlin. */
object CallTonePlayer {

    private val h = Handler(Looper.getMainLooper())
    private var ringback: ToneGenerator? = null
    private var ringbackRunnable: Runnable? = null
    private var ringbackOn = false

    @Synchronized
    @JvmStatic
    fun startRingback(ctx: Context) {
        stopRingback()
        ringbackOn = true
        ringback = ToneGenerator(AudioManager.STREAM_RING, 80)
        val r = object : Runnable {
            override fun run() {
                if (!ringbackOn) return
                try { ringback?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500) } catch (_: Exception) { }
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
        try { ringback?.stopTone() } catch (_: Exception) { }
        try { ringback?.release() } catch (_: Exception) { }
        ringback = null
    }

    @Synchronized
    @JvmStatic
    fun playConnect(ctx: Context) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            h.postDelayed({ try { tg.release() } catch (_: Exception) { } }, 300)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    @JvmStatic
    fun playEnd(ctx: Context) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90)
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
            h.postDelayed({ try { tg.release() } catch (_: Exception) { } }, 400)
        } catch (_: Exception) {
        }
    }
}