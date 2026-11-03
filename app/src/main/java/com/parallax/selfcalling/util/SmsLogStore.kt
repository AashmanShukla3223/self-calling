package com.parallax.selfcalling.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local SMS log storage: sent/received auto-replies and simulated messages. */
object SmsLogStore {

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)

    private const val KEY_SMS_LOG = "sms_log"
    private const val MAX_LOG_ENTRIES = 200

    @JvmStatic
    fun all(c: Context): MutableList<SmsEntry> {
        val out = mutableListOf<SmsEntry>()
        try {
            val arr = JSONArray(sp(c).getString(KEY_SMS_LOG, "[]"))
            for (i in 0 until arr.length()) {
                out.add(SmsEntry.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
        return out
    }

    @JvmStatic
    fun save(c: Context, list: List<SmsEntry>) {
        try {
            val arr = JSONArray()
            // keep only newest MAX_LOG_ENTRIES
            val trimmed = list.sortedByDescending { it.timestamp }.take(MAX_LOG_ENTRIES)
            for (e in trimmed.reversed()) {
                arr.put(e.toJson())
            }
            sp(c).edit().putString(KEY_SMS_LOG, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun add(c: Context, entry: SmsEntry) {
        val list = all(c)
        list.add(0, entry)
        save(c, list)
    }

    @JvmStatic
    fun clear(c: Context) {
        save(c, emptyList())
    }

    @JvmStatic
    fun count(c: Context): Int = all(c).size
}
