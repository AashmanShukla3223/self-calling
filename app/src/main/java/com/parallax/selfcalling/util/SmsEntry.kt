package com.parallax.selfcalling.util

/** A single SMS log entry: direction, number, message, timestamp, status. */
class SmsEntry @JvmOverloads constructor(
    @JvmField val timestamp: Long = System.currentTimeMillis(),
    @JvmField val direction: String = "in",   // "in" or "out"
    @JvmField val number: String = "",
    @JvmField val message: String = "",
    @JvmField val status: String = "sent"     // "sent", "failed", "received"
) {
    fun toJson(): org.json.JSONObject {
        val o = org.json.JSONObject()
        o.put("timestamp", timestamp)
        o.put("direction", direction)
        o.put("number", number)
        o.put("message", message)
        o.put("status", status)
        return o
    }

    companion object {
        @JvmStatic
        fun fromJson(o: org.json.JSONObject): SmsEntry {
            return SmsEntry(
                o.optLong("timestamp", System.currentTimeMillis()),
                o.optString("direction", "in"),
                o.optString("number", ""),
                o.optString("message", ""),
                o.optString("status", "sent")
            )
        }
    }
}
