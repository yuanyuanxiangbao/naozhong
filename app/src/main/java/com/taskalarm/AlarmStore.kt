package com.taskalarm

import android.content.Context
import org.json.JSONArray

object AlarmStore {
    private const val PREFS = "task_alarm"
    private const val KEY_LIST = "alarms"
    private const val KEY_RINGING_ID = "ringing_id"
    private const val KEY_RINGING_WINDOW = "ringing_window"
    private const val KEY_RINGING_START = "ringing_start"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): MutableList<Alarm> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { Alarm.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun save(context: Context, alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_LIST, array.toString()).apply()
    }

    fun find(context: Context, id: Int): Alarm? = load(context).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(context: Context, alarm: Alarm) {
        val alarms = load(context)
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) alarms[index] = alarm else alarms.add(alarm)
        save(context, alarms)
    }

    @Synchronized
    fun delete(context: Context, id: Int) {
        val alarms = load(context)
        alarms.removeAll { it.id == id }
        save(context, alarms)
        clearExpected(context, id)
    }

    fun nextId(context: Context): Int =
        (load(context).maxOfOrNull { it.id } ?: 0) + 1

    fun setExpected(context: Context, id: Int, millis: Long?) {
        val editor = prefs(context).edit()
        if (millis == null) editor.remove("expected_$id") else editor.putLong("expected_$id", millis)
        editor.apply()
    }

    fun getExpected(context: Context, id: Int): Long? {
        val prefs = prefs(context)
        if (!prefs.contains("expected_$id")) return null
        return prefs.getLong("expected_$id", 0L)
    }

    fun clearExpected(context: Context, id: Int) {
        prefs(context).edit().remove("expected_$id").apply()
    }

    fun setLastFired(context: Context, id: Int, window: Long) {
        val alarms = load(context)
        val alarm = alarms.firstOrNull { it.id == id } ?: return
        alarm.lastFiredWindow = window
        save(context, alarms)
    }

    fun setRinging(context: Context, id: Int, window: Long) {
        prefs(context).edit()
            .putInt(KEY_RINGING_ID, id)
            .putLong(KEY_RINGING_WINDOW, window)
            .putLong(KEY_RINGING_START, System.currentTimeMillis())
            .apply()
    }

    fun ringingId(context: Context): Int = prefs(context).getInt(KEY_RINGING_ID, -1)

    fun ringingWindow(context: Context): Long = prefs(context).getLong(KEY_RINGING_WINDOW, -1L)

    fun isRinging(context: Context): Boolean = ringingId(context) >= 0

    fun clearRinging(context: Context) {
        prefs(context).edit()
            .remove(KEY_RINGING_ID)
            .remove(KEY_RINGING_WINDOW)
            .remove(KEY_RINGING_START)
            .apply()
    }
}
