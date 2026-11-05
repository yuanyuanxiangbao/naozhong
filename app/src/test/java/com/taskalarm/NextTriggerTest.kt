package com.taskalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun onceAlarmLaterTodayStaysToday() {
        val now = ZonedDateTime.of(2026, 11, 5, 6, 30, 0, 0, zone)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = 0)
        val next = NextTrigger.next(alarm, now)!!
        assertEquals(5, next.dayOfMonth)
        assertEquals(7, next.hour)
        assertEquals(0, next.minute)
    }

    @Test
    fun onceAlarmPassedMovesToTomorrow() {
        val now = ZonedDateTime.of(2026, 11, 5, 8, 0, 0, 0, zone)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = 0)
        val next = NextTrigger.next(alarm, now)!!
        assertEquals(6, next.dayOfMonth)
    }

    @Test
    fun weeklyAlarmSkipsToSelectedDay() {
        // 2026-11-05 是周四，只选周一
        val now = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, zone)
        val alarm = Alarm(hour = 8, minute = 0, repeatDays = 1 shl 0)
        val next = NextTrigger.next(alarm, now)!!
        assertEquals(9, next.dayOfMonth)
        assertEquals(8, next.hour)
    }

    @Test
    fun weeklyAlarmTodayBeforeTimeStaysToday() {
        val now = ZonedDateTime.of(2026, 11, 5, 7, 0, 0, 0, zone)
        val alarm = Alarm(hour = 8, minute = 0, repeatDays = 1 shl 3)
        val next = NextTrigger.next(alarm, now)!!
        assertEquals(5, next.dayOfMonth)
    }

    @Test
    fun disabledAlarmHasNoNextTrigger() {
        val now = ZonedDateTime.of(2026, 11, 5, 6, 0, 0, 0, zone)
        val alarm = Alarm(hour = 7, minute = 0, enabled = false)
        assertNull(NextTrigger.next(alarm, now))
    }

    @Test
    fun daylightSavingKeepsLocalWallClock() {
        val newYork = ZoneId.of("America/New_York")
        // 2026-03-08 是美国夏令时开始日
        val now = ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, newYork)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = 0b1111111)
        val next = NextTrigger.next(alarm, now)!!
        assertEquals(8, next.dayOfMonth)
        assertEquals(7, next.hour)
    }
}
