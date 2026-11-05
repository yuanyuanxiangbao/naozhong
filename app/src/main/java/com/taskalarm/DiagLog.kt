package com.taskalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LogEntry(val time: Long, val event: String, val detail: String) {
    fun timeText(): String = FORMAT.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

    companion object {
        private val FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    }
}

object DiagLog {
    private const val PREFS = "diag_log"
    private const val KEY = "events"
    private const val MAX_ENTRIES = 300
    private val FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun record(context: Context, event: String, detail: String = "") {
        val array = loadArray(context)
        array.put(JSONObject().apply {
            put("t", System.currentTimeMillis())
            put("e", event)
            put("d", detail)
        })
        while (array.length() > MAX_ENTRIES) array.remove(0)
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    @Synchronized
    fun all(context: Context): List<LogEntry> {
        val array = loadArray(context)
        return (array.length() - 1 downTo 0).map { index ->
            val item = array.getJSONObject(index)
            LogEntry(
                item.optLong("t"),
                item.optString("e"),
                item.optString("d"),
            )
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    fun formatTime(millis: Long): String =
        FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private fun loadArray(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }
}
