package com.taskalarm

import org.json.JSONObject

data class Alarm(
    var id: Int = 0,
    var hour: Int = 7,
    var minute: Int = 0,
    var label: String = "",
    var repeatDays: Int = 0,
    var enabled: Boolean = true,
    var mathEnabled: Boolean = true,
    var mathCount: Int = 2,
    var mathHard: Boolean = false,
    var shakeEnabled: Boolean = false,
    var shakeSeconds: Int = 15,
    var confirmEnabled: Boolean = true,
    var confirmMinutes: Int = 5,
    var confirmCount: Int = 2,
    var lastFiredWindow: Long = -1L,
    var isTest: Boolean = false,
) {
    fun repeatText(): String {
        if (repeatDays == 0) return "仅一次"
        val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val parts = (0..6).filter { repeatDays and (1 shl it) != 0 }.map { names[it] }
        return if (parts.size == 7) "每天" else parts.joinToString(" ")
    }

    fun tasksText(): String {
        val parts = mutableListOf<String>()
        if (mathEnabled) parts += "数学 ${mathCount} 题"
        if (shakeEnabled) parts += "摇一摇 ${shakeSeconds} 秒"
        return if (parts.isEmpty()) "无任务" else parts.joinToString(" + ")
    }

    fun confirmText(): String =
        if (!confirmEnabled || confirmCount <= 0) "" else "复查 ${confirmMinutes} 分钟 ×$confirmCount"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("repeatDays", repeatDays)
        put("enabled", enabled)
        put("mathEnabled", mathEnabled)
        put("mathCount", mathCount)
        put("mathHard", mathHard)
        put("shakeEnabled", shakeEnabled)
        put("shakeSeconds", shakeSeconds)
        put("confirmEnabled", confirmEnabled)
        put("confirmMinutes", confirmMinutes)
        put("confirmCount", confirmCount)
        put("lastFiredWindow", lastFiredWindow)
        put("isTest", isTest)
    }

    companion object {
        fun fromJson(json: JSONObject): Alarm = Alarm(
            id = json.optInt("id", 0),
            hour = json.optInt("hour", 7),
            minute = json.optInt("minute", 0),
            label = json.optString("label", ""),
            repeatDays = json.optInt("repeatDays", 0),
            enabled = json.optBoolean("enabled", true),
            mathEnabled = json.optBoolean("mathEnabled", true),
            mathCount = json.optInt("mathCount", 2),
            mathHard = json.optBoolean("mathHard", false),
            shakeEnabled = json.optBoolean("shakeEnabled", false),
            shakeSeconds = json.optInt("shakeSeconds", 15),
            confirmEnabled = json.optBoolean("confirmEnabled", true),
            confirmMinutes = json.optInt("confirmMinutes", 5),
            confirmCount = json.optInt("confirmCount", 2),
            lastFiredWindow = json.optLong("lastFiredWindow", -1L),
            isTest = json.optBoolean("isTest", false),
        )
    }
}
