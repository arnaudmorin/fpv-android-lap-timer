package com.gfpv.camtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LapTimerTest {
    private val s = 1_000_000_000L

    @Test
    fun firstTriggerStartsLapOneAndDebounceApplies() {
        val timer = LapTimer(minLapNanos = 1 * s)
        assertEquals(LapEvent.Started, timer.onTrigger(0))
        assertNull(timer.onTrigger(s / 2))
        assertEquals(LapEvent.Lap(1, 10 * s), timer.onTrigger(10 * s))
        assertNull(timer.onTrigger(10 * s + s / 2))
        assertEquals(LapEvent.Lap(2, 12 * s), timer.onTrigger(22 * s))
        assertEquals(listOf(10 * s, 12 * s), timer.laps)
    }

    @Test
    fun removedLapIsForgottenAndNumberingFollows() {
        val timer = LapTimer(minLapNanos = s)
        timer.onTrigger(0)
        timer.onTrigger(10 * s)
        timer.onTrigger(12 * s) // false trigger: a 2 s "lap"
        timer.removeLap(1)
        assertEquals(listOf(10 * s), timer.laps)
        assertEquals(LapEvent.Lap(2, 11 * s), timer.onTrigger(23 * s))
    }

    @Test
    fun resetStartsOver() {
        val timer = LapTimer(minLapNanos = s)
        timer.onTrigger(0)
        timer.onTrigger(5 * s)
        timer.reset()
        assertEquals(emptyList<Long>(), timer.laps)
        assertEquals(LapEvent.Started, timer.onTrigger(5 * s + 1))
    }
}
