package com.parallax.selfcalling.util

import android.content.Context
import org.json.JSONArray

/** Self-call history stored as JSON in SharedPreferences (no database). Kotlin. */
object CallLogStore {

    private const val KEY = "call_log"
    private const val MAX = 100

    @JvmStatic
    fun all(c: Context): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        try {
            val arr = JSONArray(
                c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)
                    .getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                out.add(CallEntry.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
        return out
    }

    @JvmStatic
    fun add(c: Context, e: CallEntry) {
        try {
            val sp = c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)
            val arr = JSONArray(sp.getString(KEY, "[]"))
            val n = JSONArray()
            n.put(e.toJson())
            var i = 0
            while (i < arr.length() && n.length() < MAX) {
                n.put(arr.get(i))
                i++
            }
            sp.edit().putString(KEY, n.toString()).apply()
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun clear(c: Context) {
        c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
