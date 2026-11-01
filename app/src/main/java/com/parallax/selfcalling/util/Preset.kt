package com.parallax.selfcalling.util

/** An identity preset: caller name + mobile number + what is heard/seen during the call. */
class Preset @JvmOverloads constructor(
    @JvmField val name: String,
    @JvmField val number: String,
    @JvmField val voice: String = VOICE_MIC,
    @JvmField val voiceFile: String? = null,
    @JvmField val voiceFiles: List<String>? = null,
    @JvmField val video: Boolean = false
) {
    /** Ordered list of audio files to chain (falls back to single voiceFile). */
    fun chainedFiles(): List<String> {
        val list = ArrayList<String>()
        if (voiceFiles != null) list.addAll(voiceFiles)
        if (list.isEmpty() && voiceFile != null) list.add(voiceFile)
        return list
    }
    companion object {
        const val VOICE_NONE = "none"
        const val VOICE_MIC = "mic"
        const val VOICE_FILE = "file"
    }
}
