package com.parallax.selfcalling.util

/** An identity preset: caller name + mobile number + what is heard/seen during the call. */
import org.json.JSONObject

class Preset @JvmOverloads constructor(
    @JvmField val name: String,
    @JvmField val number: String,
    @JvmField val voice: String = VOICE_MIC,
    @JvmField val voiceFile: String? = null,
    @JvmField val voiceFiles: List<String>? = null,
    @JvmField val video: Boolean = false,
    @JvmField val smsAutoReplyMessage: String? = null,
    @JvmField val smsAutoReplyKeyword: String = ""
) {
    /** Ordered list of audio files to chain (falls back to single voiceFile). */
    fun chainedFiles(): List<String> {
        val list = ArrayList<String>()
        if (voiceFiles != null) list.addAll(voiceFiles)
        if (list.isEmpty() && voiceFile != null) list.add(voiceFile)
        return list
    }

    /** Whether this preset should auto-reply to incoming SMS. */
    fun smsAutoReplyEnabled(): Boolean = smsAutoReplyMessage != null && smsAutoReplyMessage!!.isNotEmpty()

    /** Whether the incoming message matches this preset's keyword (empty = match all). */
    fun smsKeywordMatches(incomingText: String): Boolean {
        val kw = smsAutoReplyKeyword.trim()
        if (kw.isEmpty()) return true
        return incomingText.lowercase().contains(kw.lowercase())
    }

    fun toJson(): org.json.JSONObject {
        val o = org.json.JSONObject()
        o.put("name", name)
        o.put("number", number)
        o.put("voice", voice)
        if (voiceFile != null) o.put("voiceFile", voiceFile)
        val fa = org.json.JSONArray()
        for (f in chainedFiles()) fa.put(f)
        if (fa.length() > 0) o.put("voiceFiles", fa)
        o.put("video", video)
        if (smsAutoReplyMessage != null) o.put("smsAutoReplyMessage", smsAutoReplyMessage)
        o.put("smsAutoReplyKeyword", smsAutoReplyKeyword)
        return o
    }

    companion object {
        @JvmStatic
        fun fromJson(o: org.json.JSONObject): Preset {
            val files = ArrayList<String>()
            if (o.has("voiceFiles")) {
                val fa = o.getJSONArray("voiceFiles")
                for (j in 0 until fa.length()) files.add(fa.getString(j))
            }
            return Preset(
                o.getString("name"),
                o.getString("number"),
                o.optString("voice", VOICE_MIC),
                if (o.has("voiceFile")) o.getString("voiceFile") else null,
                if (files.isEmpty()) null else files,
                o.optBoolean("video", false),
                if (o.has("smsAutoReplyMessage")) o.getString("smsAutoReplyMessage") else null,
                o.optString("smsAutoReplyKeyword", "")
            )
        }
        const val VOICE_NONE = "none"
        const val VOICE_MIC = "mic"
        const val VOICE_FILE = "file"
    }
}
