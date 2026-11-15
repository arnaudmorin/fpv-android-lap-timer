package com.gfpv.camtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossingDetectorTest {
    private fun active(tMs: Long, centroid: Float, left: Float = 10f, right: Float = 10f) =
        FrameMotion(tMs * MS, left, right, maxOf(left, right), centroid)

    private fun quiet(tMs: Long) = FrameMotion(tMs * MS, 0f, 0f, 0f, Float.NaN)

    /** Feeds all frames and returns the single non-null result. */
    private fun run(detector: CrossingDetector, frames: List<FrameMotion>): CrossingResult {
        val results = frames.mapNotNull { detector.onFrame(it) }
        assertEquals(1, results.size)
        return results.single()
    }

    private fun sweep(vararg centroids: Float): List<FrameMotion> {
        val frames = centroids.mapIndexed { i, c -> active(i * 33L, c) }
        val end = centroids.size * 33L
        return frames + listOf(quiet(end), quiet(end + 33), quiet(end + 66))
    }

    @Test
    fun leftToRightIsAcceptedWithInterpolatedTime() {
        val result = run(CrossingDetector(), sweep(0.2f, 0.4f, 0.6f, 0.8f))
        assertTrue(result is CrossingResult.Crossed)
        result as CrossingResult.Crossed
        assertEquals(Pass.LEFT_TO_RIGHT, result.pass)
        // Divider 0.5 lies halfway between the frames at 33 ms (0.4) and 66 ms (0.6).
        assertEquals(49.5 * MS, result.timestampNanos.toDouble(), 10_000.0)
    }

    @Test
    fun rightToLeftIsRejectedInLeftToRightMode() {
        val result = run(CrossingDetector(), sweep(0.8f, 0.6f, 0.4f, 0.2f))
        assertEquals(CrossingResult.Rejected(CrossingResult.Reason.WRONG_DIRECTION), result)
    }

    @Test
    fun motionStayingOnOneSideIsRejected() {
        val frames = listOf(0.1f, 0.2f, 0.3f, 0.2f).mapIndexed { i, c -> active(i * 33L, c, right = 0f) } +
            listOf(quiet(200), quiet(233), quiet(266))
        assertEquals(CrossingResult.Rejected(CrossingResult.Reason.NO_CROSSING), run(CrossingDetector(), frames))
    }

    @Test
    fun fastTwoFramePassUsesHalfActivationOrder() {
        // Both centroids stay left of the divider (frame differencing smears the blob), but the
        // left half lights up first and the right half on the next frame.
        val frames = listOf(
            active(0, 0.30f, left = 10f, right = 0f),
            active(33, 0.45f, left = 10f, right = 8f),
            quiet(66), quiet(100), quiet(133),
        )
        val result = run(CrossingDetector(), frames)
        assertEquals(CrossingResult.Crossed(33 * MS, Pass.LEFT_TO_RIGHT), result)
    }

    @Test
    fun eitherDirectionAcceptsRightToLeft() {
        val detector = CrossingDetector(CrossingConfig(direction = Direction.EITHER))
        val result = run(detector, sweep(0.9f, 0.7f, 0.3f))
        assertTrue(result is CrossingResult.Crossed)
        assertEquals(Pass.RIGHT_TO_LEFT, (result as CrossingResult.Crossed).pass)
    }

    @Test
    fun rightToLeftModeAcceptsRightToLeft() {
        val detector = CrossingDetector(CrossingConfig(direction = Direction.RIGHT_TO_LEFT))
        assertTrue(run(detector, sweep(0.9f, 0.7f, 0.3f)) is CrossingResult.Crossed)
    }

    @Test
    fun anyMotionTriggersImmediately() {
        val detector = CrossingDetector(CrossingConfig(direction = Direction.ANY_MOTION))
        assertNull(detector.onFrame(quiet(0)))
        assertEquals(CrossingResult.Crossed(33 * MS, null), detector.onFrame(active(33, 0.1f)))
    }

    @Test
    fun belowSensitivityIsIgnored() {
        val detector = CrossingDetector(CrossingConfig(sensitivityPct = 20f))
        val frames = sweep(0.2f, 0.4f, 0.6f, 0.8f)
        assertTrue(frames.all { detector.onFrame(it) == null })
    }

    @Test
    fun longEventIsClosedAfterMaxTransit() {
        val detector = CrossingDetector(CrossingConfig(maxTransitNanos = 200 * MS))
        // Continuous motion that crosses the divider: judged once the event exceeds 200 ms.
        val results = (0..10).mapNotNull { i -> detector.onFrame(active(i * 33L, 0.1f + i * 0.08f)) }
        assertEquals(1, results.size)
        assertTrue(results.single() is CrossingResult.Crossed)
    }

    private companion object {
        const val MS = 1_000_000L
    }
}
