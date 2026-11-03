package com.parallax.selfcalling.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Backup/restore of presets and shortcut sets to a JSON file. */
object BackupRestore {

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("selfcall", Context.MODE_PRIVATE)

    private const val KEY_PRESETS = "presets"
    private const val KEY_SETS = "shortcut_sets"
    private const val KEY_ACTIVE_INDEX = "active_preset"

    /** Export everything to a JSON string. */
    @JvmStatic
    fun export(c: Context): String {
        val out = JSONObject()
        try {
            out.put("presets", JSONArray(sp(c).getString(KEY_PRESETS, "[]")))
            out.put("shortcut_sets", JSONArray(sp(c).getString(KEY_SETS, "[]")))
            out.put("active_preset", sp(c).getInt(KEY_ACTIVE_INDEX, 0))
            out.put("version", 1)
        } catch (_: Exception) {
        }
        return out.toString()
    }

    /** Import from a JSON string, replacing current data. */
    @JvmStatic
    fun importData(c: Context, json: String): Boolean {
        try {
            val o = JSONObject(json)
            if (o.has("presets")) {
                sp(c).edit().putString(KEY_PRESETS, o.getString("presets")).apply()
            }
            if (o.has("shortcut_sets")) {
                sp(c).edit().putString(KEY_SETS, o.getString("shortcut_sets")).apply()
            }
            if (o.has("active_preset")) {
                sp(c).edit().putInt(KEY_ACTIVE_INDEX, o.getInt("active_preset")).apply()
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
