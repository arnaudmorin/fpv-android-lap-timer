package com.gfpv.camtimer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NormRectTest {
    @Test
    fun edgesKeepMinimumSize() {
        val r = NormRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(0.8f - NormRect.MIN_SIZE, r.withLeft(0.95f).left, 1e-6f)
        assertEquals(0f, r.withTop(-1f).top, 1e-6f)
    }

    @Test
    fun moveStaysInsideFrame() {
        val moved = NormRect(0.2f, 0.2f, 0.8f, 0.8f).movedBy(0.5f, -0.5f)
        assertEquals(0.4f, moved.left, 1e-6f)
        assertEquals(0f, moved.top, 1e-6f)
        assertEquals(1f, moved.right, 1e-6f)
        assertEquals(0.6f, moved.bottom, 1e-6f)
    }
}
