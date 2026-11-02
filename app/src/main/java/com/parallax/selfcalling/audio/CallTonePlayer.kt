package com.parallax.selfcalling.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.sin

/** Plays Indian-telecom-style tones using AudioTrack (generated PCM), so they sound like real
 *  calls instead of generic ToneGenerator beeps. Kotlin. */
object CallTonePlayer {

    private val h = Handler(Looper.getMainLooper())
    private var ringbackOn = false
    private var ringbackTrack: AudioTrack? = null
    private var ringbackThread: Thread? = null

    private const val SAMPLE_RATE = 8000

    /** Generates a sine wave at [freq] Hz for [durationMs] ms, amplitude 0..1. */
    private fun tone(freq: Double, durationMs: Int, amp: Double = 0.6): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            out[i] = (sin(2 * Math.PI * freq * i / SAMPLE_RATE) * amp * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Silence for [durationMs] ms. */
    private fun silence(durationMs: Int): ShortArray = ShortArray(SAMPLE_RATE * durationMs / 1000)

    /** Builds one Indian ringback cycle: 400 Hz 0.4s, 0.2s gap, 400 Hz 0.4s, 2.0s gap. */
    private fun ringbackCycle(): ShortArray {
        val parts = listOf(tone(400.0, 400), silence(200), tone(400.0, 400), silence(2000))
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var off = 0
        for (p in parts) { System.arraycopy(p, 0, out, off, p.size); off += p.size }
        return out
    }

    @Synchronized
    @JvmStatic
    fun startRingback(ctx: Context) {
        stopRingback()
        ringbackOn = true
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.mode = AudioManager.MODE_RINGTONE
        val cycle = ringbackCycle()
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, cycle.size * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(cycle, 0, cycle.size)
        track.setLoopPoints(0, cycle.size, -1)
        ringbackTrack = track
        ringbackThread = Thread {
            try {
                track.play()
                while (ringbackOn) {
                    Thread.sleep(100)
                }
            } catch (_: Exception) {
            } finally {
                try { track.stop() } catch (_: Exception) { }
                try { track.release() } catch (_: Exception) { }
            }
        }
        ringbackThread?.start()
    }

    @Synchronized
    @JvmStatic
    fun stopRingback() {
        ringbackOn = false
        try { ringbackThread?.join(300) } catch (_: Exception) { }
        ringbackThread = null
        try { ringbackTrack?.stop() } catch (_: Exception) { }
        try { ringbackTrack?.release() } catch (_: Exception) { }
        ringbackTrack = null
        val am = null
    }

    /** Plays a short generated tone at [freq] Hz for [durationMs] ms. */
    private fun playTone(ctx: Context, freq: Double, durationMs: Int, stream: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.mode = AudioManager.MODE_IN_COMMUNICATION
        val samples = tone(freq, durationMs)
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(maxOf(minBuf, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        Handler(Looper.getMainLooper()).postDelayed({
            try { track.stop() } catch (_: Exception) { }
            try { track.release() } catch (_: Exception) { }
        }, durationMs.toLong() + 50)
    }

    @Synchronized
    @JvmStatic
    fun playConnect(ctx: Context) {
        // short confirm beep: 880 Hz, 120 ms
        playTone(ctx, 880.0, 120, AudioManager.STREAM_VOICE_CALL)
    }

    @Synchronized
    @JvmStatic
    fun playEnd(ctx: Context) {
        // reorder-style drop: 480 Hz, 400 ms
        playTone(ctx, 480.0, 400, AudioManager.STREAM_VOICE_CALL)
    }
}