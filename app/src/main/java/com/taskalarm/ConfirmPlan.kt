package com.taskalarm

// 复查只关心“还剩几轮、下一轮是第几次”，与 Android 状态无关，便于单测
object ConfirmPlan {
    data class Step(val left: Int, val round: Int)

    fun next(alarm: Alarm, round: Int, left: Int): Step? {
        if (!alarm.confirmEnabled || alarm.confirmCount <= 0) return null
        val remaining = if (round <= 0) alarm.confirmCount else left - 1
        if (remaining <= 0) return null
        val nextRound = if (round <= 0) 1 else round + 1
        return Step(remaining, nextRound)
    }

    fun deadline(now: Long, alarm: Alarm): Long =
        now + alarm.confirmMinutes.coerceAtLeast(1) * 60_000L
}
