package com.parallax.selfcalling.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** One step in a shortcut set: call (simulated incoming) or message (SMS). */
class ShortcutStep(
    @JvmField val snapshot: Preset,
    @JvmField val delaySec: Int = 0,
    /** step type: TYPE_CALL or TYPE_MESSAGE */
    @JvmField val type: String = TYPE_CALL,
    /** message body for TYPE_MESSAGE steps */
    @JvmField val messageText: String = "",
    @JvmField val ringTimeoutSec: Int = -1,   // -1 = use global
    @JvmField val autoAnswerSec: Int = -2      // -2 = use global, -1 = off
) {
    companion object {
        const val TYPE_CALL = "call"
        const val TYPE_MESSAGE = "message"
        @JvmOverloads
        fun call(snapshot: Preset, delaySec: Int = 0,
                 ringTimeoutSec: Int = -1, autoAnswerSec: Int = -2) =
            ShortcutStep(snapshot, delaySec, TYPE_CALL, "", ringTimeoutSec, autoAnswerSec)
        @JvmOverloads
        fun message(snapshot: Preset, messageText: String, delaySec: Int = 0) =
            ShortcutStep(snapshot, delaySec, TYPE_MESSAGE, messageText, -1, -2)
    }
}

/** A reusable, ordered set of shortcut steps (calls and/or messages). */
class ShortcutSet(
    @JvmField val id: Int,
    @JvmField var name: String,
    @JvmField val steps: MutableList<ShortcutStep> = mutableListOf()
)

object ShortcutSets {

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)

    private const val KEY_SETS = "shortcut_sets"
    private const val KEY_NEXT_ID = "shortcut_next_id"

    @JvmStatic
    fun all(c: Context): MutableList<ShortcutSet> {
        val out = mutableListOf<ShortcutSet>()
        try {
            val arr = JSONArray(sp(c).getString(KEY_SETS, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val steps = mutableListOf<ShortcutStep>()
                val sa = o.optJSONArray("steps")
                if (sa != null) {
                    for (j in 0 until sa.length()) {
                        val so = sa.getJSONObject(j)
                        steps.add(ShortcutStep(
                            Preset.fromJson(so.getJSONObject("snapshot")),
                            so.optInt("delaySec", 0),
                            so.optString("type", ShortcutStep.TYPE_CALL),
                            so.optString("messageText", ""),
                            so.optInt("ringTimeoutSec", -1),
                            so.optInt("autoAnswerSec", -2)
                        ))
                    }
                }
                out.add(ShortcutSet(o.getInt("id"), o.getString("name"), steps))
            }
        } catch (_: Exception) {
        }
        return out
    }

    @JvmStatic
    fun save(c: Context, list: List<ShortcutSet>) {
        try {
            val arr = JSONArray()
            for (s in list) {
                val o = JSONObject()
                o.put("id", s.id)
                o.put("name", s.name)
                val sa = JSONArray()
                for (st in s.steps) {
                    val so = JSONObject()
                    so.put("snapshot", st.snapshot.toJson())
                    so.put("delaySec", st.delaySec)
                    so.put("type", st.type)
                    so.put("messageText", st.messageText)
                    so.put("ringTimeoutSec", st.ringTimeoutSec)
                    so.put("autoAnswerSec", st.autoAnswerSec)
                    sa.put(so)
                }
                o.put("steps", sa)
                arr.put(o)
            }
            sp(c).edit().putString(KEY_SETS, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun get(c: Context, id: Int): ShortcutSet? = all(c).firstOrNull { it.id == id }

    @JvmStatic
    fun nextId(c: Context): Int {
        val n = sp(c).getInt(KEY_NEXT_ID, 1)
        sp(c).edit().putInt(KEY_NEXT_ID, n + 1).apply()
        return n
    }

    @JvmStatic
    fun upsert(c: Context, set: ShortcutSet) {
        val list = all(c)
        val idx = list.indexOfFirst { it.id == set.id }
        if (idx >= 0) list[idx] = set else list.add(set)
        save(c, list)
    }

    @JvmStatic
    fun delete(c: Context, id: Int) {
        save(c, all(c).filter { it.id != id })
    }
}
