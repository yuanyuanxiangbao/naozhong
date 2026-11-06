package com.taskalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmPlanTest {
    @Test
    fun initialRoundCreatesFirstFollowUp() {
        val alarm = Alarm(confirmEnabled = true, confirmMinutes = 5, confirmCount = 2)
        val step = ConfirmPlan.next(alarm, round = 0, left = 0)!!
        assertEquals(2, step.left)
        assertEquals(1, step.round)
    }

    @Test
    fun eachRoundDecrementsRemaining() {
        val alarm = Alarm(confirmEnabled = true, confirmCount = 2)
        val step = ConfirmPlan.next(alarm, round = 1, left = 2)!!
        assertEquals(1, step.left)
        assertEquals(2, step.round)
    }

    @Test
    fun lastRoundHasNoNext() {
        val alarm = Alarm(confirmEnabled = true, confirmCount = 2)
        assertNull(ConfirmPlan.next(alarm, round = 2, left = 1))
    }

    @Test
    fun disabledHasNoPlan() {
        val alarm = Alarm(confirmEnabled = false, confirmCount = 2)
        assertNull(ConfirmPlan.next(alarm, round = 0, left = 0))
    }

    @Test
    fun deadlineUsesConfiguredMinutes() {
        val alarm = Alarm(confirmMinutes = 5)
        val now = 1_000_000L
        assertEquals(now + 300_000L, ConfirmPlan.deadline(now, alarm))
    }
}
