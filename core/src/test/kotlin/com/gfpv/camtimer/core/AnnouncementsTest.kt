package com.gfpv.camtimer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementsTest {
    @Test
    fun frenchUsesDecimalComma() {
        assertEquals("Tour 3, 12,34 secondes", Announcements.lap(true, 3, 12_340, AnnounceFormat.LAP_AND_TIME))
        assertEquals("12,34", Announcements.lap(true, 3, 12_340, AnnounceFormat.TIME_ONLY))
    }

    @Test
    fun english() {
        assertEquals("Lap 3, 12.34 seconds", Announcements.lap(false, 3, 12_340, AnnounceFormat.LAP_AND_TIME))
    }
}
