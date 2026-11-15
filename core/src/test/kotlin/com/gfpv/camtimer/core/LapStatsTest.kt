package com.gfpv.camtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LapStatsTest {
    @Test
    fun best3UsesLowestConsecutiveSum() {
        val laps = listOf(5000L, 4000L, 4500L, 3900L, 6000L)
        assertEquals(Best3(12_400, 1, listOf(4000L, 4500L, 3900L)), LapStats.best3Consecutive(laps))
        assertEquals(3900L, LapStats.bestLap(laps))
    }

    @Test
    fun best3NeedsThreeLaps() {
        assertNull(LapStats.best3Consecutive(listOf(1000L, 2000L)))
        assertNull(LapStats.bestLap(emptyList()))
    }
}
