package com.parallax.selfcalling.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Plays telecom-style tones using the system ToneGenerator on STREAM_MUSIC (always audible),
 *  switching audio mode so they aren't suppressed. Kotlin. */
object CallTonePlayer {

    private val h = Handler(Looper.getMainLooper())
    private var ringbackOn = false
    private var ringbackRunnable: Runnable? = null
    private var ringbackTg: ToneGenerator? = null

    @Synchronized
    @JvmStatic
    fun startRingback(ctx: Context) {
        stopRingback()
        ringbackOn = true
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.mode = AudioManager.MODE_NORMAL
        am?.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
        ringbackTg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        val r = object : Runnable {
            override fun run() {
                if (!ringbackOn) return
                try { ringbackTg?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500) } catch (_: Exception) { }
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
        try { ringbackTg?.stopTone() } catch (_: Exception) { }
        try { ringbackTg?.release() } catch (_: Exception) { }
        ringbackTg = null
    }

    @Synchronized
    @JvmStatic
    fun playConnect(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.mode = AudioManager.MODE_NORMAL
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            h.postDelayed({
                try { tg.stopTone() } catch (_: Exception) { }
                try { tg.release() } catch (_: Exception) { }
            }, 300)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    @JvmStatic
    fun playEnd(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.mode = AudioManager.MODE_NORMAL
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
            h.postDelayed({
                try { tg.stopTone() } catch (_: Exception) { }
                try { tg.release() } catch (_: Exception) { }
            }, 400)
        } catch (_: Exception) {
        }
    }
}