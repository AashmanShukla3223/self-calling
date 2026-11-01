package com.parallax.selfcalling.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

/** Plays one or more local audio files chained (in order, looping the whole chain) into the
 *  call's voice path (earpiece) while a call is active. Kotlin. */
object CallFilePlayer {

    private var player: MediaPlayer? = null
    private var am: AudioManager? = null
    private var prevMode = 0
    private var chain: List<String> = emptyList()
    private var index = 0

    @Synchronized
    @JvmStatic
    fun start(ctx: Context, path: String?) {
        startFiles(ctx, if (path.isNullOrEmpty()) emptyList() else listOf(path))
    }

    @Synchronized
    @JvmStatic
    fun startFiles(ctx: Context, paths: List<String>?) {
        stop()
        val valid = (paths ?: emptyList()).filter { p ->
            val f = File(p); f.exists() && f.canRead()
        }
        if (valid.isEmpty()) return
        chain = valid
        index = 0
        am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.let {
            prevMode = it.mode
            it.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        playCurrent(ctx)
    }

    private fun playCurrent(ctx: Context) {
        val f = File(chain[index])
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                setDataSource(f.absolutePath)
                isLooping = false
                setOnCompletionListener { mp ->
                    try { mp.release() } catch (_: Exception) { }
                    player = null
                    index = (index + 1) % chain.size
                    // Only continue if not stopped externally.
                    if (chain.isNotEmpty()) playCurrent(ctx)
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            // Skip unreadable entry.
            index = (index + 1) % chain.size
            if (index != 0 && chain.isNotEmpty()) playCurrent(ctx) else stop()
        }
    }

    /** Pauses the chain during a held call; position is preserved. */
    @Synchronized
    @JvmStatic
    fun pause() {
        try {
            player?.pause()
        } catch (_: Exception) {
        }
    }

    /** Resumes the paused chain after un-holding. */
    @Synchronized
    @JvmStatic
    fun resume() {
        try {
            player?.start()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    @JvmStatic
    fun stop() {
        player?.let {
            try { it.stop() } catch (_: Exception) {
            }
            try { it.release() } catch (_: Exception) {
            }
        }
        player = null
        chain = emptyList()
        index = 0
        am?.let {
            try { it.mode = prevMode } catch (_: Exception) {
            }
        }
        am = null
    }
}