package com.parallax.selfcalling.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process

/**
 * Live mic loopback: pumps microphone audio into the call's voice path
 * (`MODE_IN_COMMUNICATION` + `USAGE_VOICE_COMMUNICATION`) so you hear yourself. Kotlin.
 */
object LoopbackEngine {

    private var running = false
    private var worker: Thread? = null
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private var am: AudioManager? = null
    private var prevMode = 0

    @Synchronized
    @JvmStatic
    fun start(ctx: Context) {
        if (running) return
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        val sr = 16000
        val buf = maxOf(
            AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        ) * 4

        try {
            rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf)
            track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                buf * 2, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        } catch (_: Exception) {
            release()
            return
        }
        val r = rec
        val t = track
        if (r == null || t == null
            || r.state != AudioRecord.STATE_INITIALIZED
            || t.state != AudioTrack.STATE_INITIALIZED) {
            release()
            return
        }

        am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.let {
            prevMode = it.mode
            it.mode = AudioManager.MODE_IN_COMMUNICATION
        }

        running = true
        val pcm = ShortArray(1280)
        worker = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                r.startRecording()
                t.play()
                while (running) {
                    val n = r.read(pcm, 0, pcm.size)
                    if (n > 0) {
                        t.write(pcm, 0, n)
                    } else if (n < 0) {
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }, "selfcall-loopback").also { it.start() }
    }

    @Synchronized
    @JvmStatic
    fun stop() {
        running = false
        worker?.let {
            try { it.join(500) } catch (_: InterruptedException) {
            }
        }
        worker = null
        release()
        am?.let {
            try { it.mode = prevMode } catch (_: Exception) {
            }
        }
        am = null
    }

    private fun release() {
        rec?.let {
            try { it.release() } catch (_: Exception) {
            }
        }
        rec = null
        track?.let {
            try { it.release() } catch (_: Exception) {
            }
        }
        track = null
    }
}
