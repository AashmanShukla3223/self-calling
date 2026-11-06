package com.parallax.selfcalling.util

import android.content.Context
import android.content.SharedPreferences
import com.parallax.selfcalling.R
import org.json.JSONArray
import org.json.JSONObject

/** App preferences: identity presets, call timing, auto-answer. Kotlin. */
object Prefs {

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)

    // ---- identity presets (name + number + voice) ----

    @JvmStatic
    fun presets(c: Context): List<Preset> {
        val out = ArrayList<Preset>()
        try {
            val arr = JSONArray(sp(c).getString("presets", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val files = ArrayList<String>()
                if (o.has("voiceFiles")) {
                    val fa = o.getJSONArray("voiceFiles")
                    for (j in 0 until fa.length()) files.add(fa.getString(j))
                }
                out.add(Preset(
                    o.getString("name"),
                    o.getString("number"),
                    o.optString("voice", Preset.VOICE_MIC),
                    if (o.has("voiceFile")) o.getString("voiceFile") else null,
                    if (files.isEmpty()) null else files,
                    o.optBoolean("video", false)
                ))
            }
        } catch (_: Exception) {
        }
        if (out.isEmpty()) {
            out.add(Preset("You", "1234567890"))
            out.add(Preset("Teacher", "+919876500010"))
            out.add(Preset("Worker", "+919876500011"))
            out.add(Preset("Delivery Driver", "+919876500012"))
            out.add(Preset("Boss", "+919876500013"))
            out.add(Preset("Newspaper Deliverer", "+919876500014"))
        }
        return out
    }

    @JvmStatic
    fun savePresets(c: Context, list: List<Preset>) {
        try {
            val arr = JSONArray()
            for (p in list) {
                val o = JSONObject()
                o.put("name", p.name)
                o.put("number", p.number)
                o.put("voice", p.voice)
                if (p.voiceFile != null) o.put("voiceFile", p.voiceFile)
                val fa = JSONArray()
                for (f in p.chainedFiles()) fa.put(f)
                if (fa.length() > 0) o.put("voiceFiles", fa)
                o.put("video", p.video)
                arr.put(o)
            }
            sp(c).edit().putString("presets", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun activeIndex(c: Context): Int {
        val l = presets(c)
        return sp(c).getInt("active_preset", 0).coerceIn(0, l.size - 1)
    }

    @JvmStatic
    fun setActiveIndex(c: Context, i: Int) {
        sp(c).edit().putInt("active_preset", i).apply()
    }

    @JvmStatic
    fun activePreset(c: Context): Preset = presets(c)[activeIndex(c)]

    // ---- call timing ----

    /** Delay before an outgoing self-call connects. Default 2000 ms. */
    @JvmStatic
    fun connectDelayMs(c: Context): Int = sp(c).getInt("connect_delay", 2000)

    @JvmStatic
    fun setConnectDelayMs(c: Context, ms: Int) {
        sp(c).edit().putInt("connect_delay", ms).apply()
    }

    /** Auto-end after N seconds once connected; -1 = never. */
    @JvmStatic
    fun autoEndSec(c: Context): Int = sp(c).getInt("auto_end", -1)

    @JvmStatic
    fun setAutoEndSec(c: Context, seconds: Int) {
        sp(c).edit().putInt("auto_end", seconds).apply()
    }

    /** -1 = off, otherwise seconds (incoming auto-answer, Android 8+). */
    @JvmStatic
    fun autoAnswerDelay(c: Context): Int = sp(c).getInt("auto_answer_delay", -1)

    @JvmStatic
    fun setAutoAnswerDelay(c: Context, seconds: Int) {
        sp(c).edit().putInt("auto_answer_delay", seconds).apply()
    }

    /** Wait after tapping "Simulate incoming call" before it starts ringing. 0 = now. */
    @JvmStatic
    fun incomingDelaySec(c: Context): Int = sp(c).getInt("incoming_delay", 0)

    @JvmStatic
    fun setIncomingDelaySec(c: Context, seconds: Int) {
        sp(c).edit().putInt("incoming_delay", seconds).apply()
    }

    /** Seconds a simulated incoming call rings before auto-dismissing. 0 = never. Default 45. */
    @JvmStatic
    fun ringTimeoutSec(c: Context): Int = sp(c).getInt("ring_timeout", 45)

    @JvmStatic
    fun setRingTimeoutSec(c: Context, seconds: Int) {
        sp(c).edit().putInt("ring_timeout", seconds).apply()
    }

    /** Ringback tone heard while an outgoing self-call is dialing. Default on. */
    @JvmStatic
    fun dialingTone(c: Context): Boolean = sp(c).getBoolean("tone_dialing", true)

    @JvmStatic
    fun setDialingTone(c: Context, enabled: Boolean) {
        sp(c).edit().putBoolean("tone_dialing", enabled).apply()
    }

    /** Tone played at the moment a call connects. Default on. */
    @JvmStatic
    fun connectTone(c: Context): Boolean = sp(c).getBoolean("tone_connect", true)

    @JvmStatic
    fun setConnectTone(c: Context, enabled: Boolean) {
        sp(c).edit().putBoolean("tone_connect", enabled).apply()
    }

    /** Tone played when a call ends. Default on. */
    @JvmStatic
    fun endTone(c: Context): Boolean = sp(c).getBoolean("tone_end", true)

    @JvmStatic
    fun setEndTone(c: Context, enabled: Boolean) {
        sp(c).edit().putBoolean("tone_end", enabled).apply()
    }

    /** Legacy mic-loopback flag; superseded by per-identity voice mode. */
    @JvmStatic
    fun loopback(c: Context): Boolean = sp(c).getBoolean("loopback", false)

    @JvmStatic
    fun setLoopback(c: Context, b: Boolean) {
        sp(c).edit().putBoolean("loopback", b).apply()
    }

    // ---- dialer branding ----

    /** Label shown by the dialer as "Calls via ...". Blank falls back to the app name. */
    @JvmStatic
    fun branding(c: Context): String =
        sp(c).getString("branding", "")!!.trim().ifEmpty { c.getString(R.string.app_name) }

    @JvmStatic
    fun setBranding(c: Context, value: String?) {
        sp(c).edit().putString("branding", value?.trim().orEmpty()).apply()
    }

    // ---- SMS auto-reply ----

    /** Whether incoming SMS gets an automatic reply. Default off. */
    @JvmStatic
    fun smsAutoReplyEnabled(c: Context): Boolean = sp(c).getBoolean("sms_auto_reply", false)

    @JvmStatic
    fun setSmsAutoReplyEnabled(c: Context, enabled: Boolean) {
        sp(c).edit().putBoolean("sms_auto_reply", enabled).apply()
    }

    /** The reply message sent back for every incoming SMS. Default empty. */
    @JvmStatic
    fun smsAutoReplyMessage(c: Context): String =
        sp(c).getString("sms_auto_reply_message", "") ?: ""

    @JvmStatic
    fun setSmsAutoReplyMessage(c: Context, message: String?) {
        sp(c).edit().putString("sms_auto_reply_message", message?.trim().orEmpty()).apply()
    }
}
