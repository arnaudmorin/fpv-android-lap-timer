package com.gfpv.camtimer.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionDetectorTest {
    // 8x4 image with rowStride 10 (2 padding bytes per row); value = y * 10 + x.
    private val width = 8
    private val height = 4
    private val stride = 10
    private val plane = ByteArray(stride * height) { (it / stride * 10 + it % stride).toByte() }

    @Test
    fun cropRespectsRowStride() {
        val frame = LumaCropper.crop(plane, stride, 1, width, height, NormRect(0f, 0f, 1f, 1f), mirror = false, step = 1)
        assertEquals(8, frame.width)
        assertEquals(4, frame.height)
        assertEquals(17, frame.data[8 + 7].toInt()) // row 1, column 7
        assertEquals(37, frame.data[3 * 8 + 7].toInt())
    }

    @Test
    fun cropOfPartialZone() {
        val frame = LumaCropper.crop(plane, stride, 1, width, height, NormRect(0.25f, 0.5f, 0.75f, 1f), mirror = false, step = 1)
        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertArrayEquals(byteArrayOf(22, 23, 24, 25, 32, 33, 34, 35), frame.data)
    }

    @Test
    fun cropMirrorsForFrontCamera() {
        val frame = LumaCropper.crop(plane, stride, 1, width, height, NormRect(0f, 0f, 1f, 1f), mirror = true, step = 1)
        assertArrayEquals(byteArrayOf(7, 6, 5, 4, 3, 2, 1, 0), frame.data.copyOfRange(0, 8))
    }

    @Test
    fun cropSubsamples() {
        val frame = LumaCropper.crop(plane, stride, 1, width, height, NormRect(0f, 0f, 1f, 1f), mirror = false, step = 2)
        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertArrayEquals(byteArrayOf(0, 2, 4, 6, 20, 22, 24, 26), frame.data)
    }

    @Test
    fun measuresHalvesAndCentroid() {
        val detector = MotionDetector()
        val w = 10
        val h = 2
        val before = LumaFrame(w, h, ByteArray(w * h))
        // Pixels at x = 7 and 8 change on both rows.
        val after = LumaFrame(w, h, ByteArray(w * h) { if (it % w == 7 || it % w == 8) 200.toByte() else 0 })

        assertNull(detector.process(before, 0.5f, 0))
        val motion = detector.process(after, 0.5f, 33)!!
        assertEquals(20f, motion.totalPct, 0.001f)
        assertEquals(0f, motion.leftPct, 0.001f)
        assertEquals(40f, motion.rightPct, 0.001f)
        assertEquals(0.8f, motion.centroidX, 0.001f)
    }

    @Test
    fun smallChangesBelowThresholdAreIgnored() {
        val detector = MotionDetector()
        detector.process(LumaFrame(4, 1, ByteArray(4) { 100 }), 0.5f, 0)
        val motion = detector.process(LumaFrame(4, 1, ByteArray(4) { 130.toByte() }), 0.5f, 33)!!
        assertEquals(0f, motion.totalPct, 0.001f)
        assertTrue(motion.centroidX.isNaN())
    }

    @Test
    fun zoneResizeRestartsComparison() {
        val detector = MotionDetector()
        detector.process(LumaFrame(4, 1, ByteArray(4)), 0.5f, 0)
        assertNull(detector.process(LumaFrame(2, 1, ByteArray(2)), 0.5f, 33))
    }
}
