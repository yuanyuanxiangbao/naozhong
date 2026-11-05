package com.taskalarm

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object NextTrigger {
    private val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    fun next(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        var candidate = now
            .withHour(alarm.hour)
            .withMinute(alarm.minute)
            .withSecond(0)
            .withNano(0)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)

        if (alarm.repeatDays == 0) return candidate

        repeat(7) {
            val bit = 1 shl (candidate.dayOfWeek.value - 1)
            if (alarm.repeatDays and bit != 0) return candidate
            candidate = candidate.plusDays(1)
        }
        return null
    }

    fun windowMillis(dateTime: ZonedDateTime): Long = dateTime.toInstant().toEpochMilli()

    fun format(dateTime: ZonedDateTime): String = dateTime.format(formatter)
}
