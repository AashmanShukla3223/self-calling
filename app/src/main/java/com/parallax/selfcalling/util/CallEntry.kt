package com.parallax.selfcalling.util

import org.json.JSONObject

/** One entry in the self-call history. Kotlin. */
class CallEntry @JvmOverloads constructor(
    @JvmField val ts: Long,
    @JvmField val kind: String,          // "out" | "in"
    @JvmField val answered: Boolean,
    @JvmField val durationSec: Int,
    @JvmField val autoAnswered: Boolean,
    @JvmField val video: Boolean = false
) {

    fun toJson(): JSONObject = JSONObject()
        .put("ts", ts)
        .put("kind", kind)
        .put("answered", answered)
        .put("duration", durationSec)
        .put("auto", autoAnswered)
        .put("video", video)

    companion object {
        @JvmStatic
        fun fromJson(o: JSONObject): CallEntry = CallEntry(
            o.getLong("ts"),
            o.getString("kind"),
            o.getBoolean("answered"),
            o.getInt("duration"),
            o.getBoolean("auto"),
            o.optBoolean("video", false)
        )

        @JvmStatic
        fun fmtDuration(s: Int): String = String.format("%d:%02d", s / 60, s % 60)
    }
}
