package com.racegow.camtimer.core

import kotlin.math.abs

enum class Direction { LEFT_TO_RIGHT, RIGHT_TO_LEFT, EITHER, ANY_MOTION }

enum class Pass { LEFT_TO_RIGHT, RIGHT_TO_LEFT }

data class CrossingConfig(
    val direction: Direction = Direction.LEFT_TO_RIGHT,
    /** Minimum share of changed pixels (in %) for a frame, or a half, to count as active. */
    val sensitivityPct: Float = 5f,
    /** Gate line position, normalized 0..1 within the zone. */
    val dividerX: Float = 0.5f,
    /** Quiet frames that end a motion event. */
    val gapFrames: Int = 3,
    /** An event longer than this is closed and judged anyway. */
    val maxTransitNanos: Long = 600_000_000L,
)

sealed interface CrossingResult {
    data class Crossed(val timestampNanos: Long, val pass: Pass?) : CrossingResult
    data class Rejected(val reason: Reason) : CrossingResult

    enum class Reason { WRONG_DIRECTION, NO_CROSSING, AMBIGUOUS }
}

/**
 * Turns per-frame motion into gate crossings: consecutive active frames form one event, and the
 * event is a crossing only if the motion travelled from one side of the divider to the other.
 */
class CrossingDetector(config: CrossingConfig = CrossingConfig()) {
    @Volatile
    var config: CrossingConfig = config

    private class Sample(val t: Long, val c: Float, val left: Boolean, val right: Boolean)

    private val samples = ArrayList<Sample>()
    private var quietFrames = 0

    fun reset() {
        samples.clear()
        quietFrames = 0
    }

    /** Feeds one frame; returns a result when a motion event ends. */
    fun onFrame(motion: FrameMotion): CrossingResult? {
        val cfg = config
        val active = motion.totalPct >= cfg.sensitivityPct
        if (cfg.direction == Direction.ANY_MOTION) {
            reset()
            return if (active) CrossingResult.Crossed(motion.timestampNanos, null) else null
        }
        if (active) {
            samples += Sample(
                t = motion.timestampNanos,
                c = motion.centroidX,
                left = motion.leftPct >= cfg.sensitivityPct,
                right = motion.rightPct >= cfg.sensitivityPct,
            )
            quietFrames = 0
        } else if (samples.isNotEmpty()) {
            quietFrames++
        }
        if (samples.isEmpty()) return null
        val expired = motion.timestampNanos - samples.first().t > cfg.maxTransitNanos
        if (quietFrames < cfg.gapFrames && !expired) return null
        val result = evaluate(cfg)
        reset()
        return result
    }

    private fun evaluate(cfg: CrossingConfig): CrossingResult {
        val divider = cfg.dividerX
        val first = samples.first()
        val last = samples.last()
        val startsLeft = first.c < divider
        val endsLeft = last.c < divider
        val pass: Pass
        val time: Long
        if (startsLeft != endsLeft) {
            pass = if (startsLeft) Pass.LEFT_TO_RIGHT else Pass.RIGHT_TO_LEFT
            time = crossingTime(divider, startsLeft)
        } else if (samples.size <= FAST_PASS_FRAMES) {
            // A fast drone may only show up in 1-3 frames: use which half lit up first.
            val firstLeft = samples.indexOfFirst { it.left }
            val firstRight = samples.indexOfFirst { it.right }
            if (firstLeft < 0 || firstRight < 0) return CrossingResult.Rejected(CrossingResult.Reason.NO_CROSSING)
            when {
                firstLeft < firstRight -> {
                    pass = Pass.LEFT_TO_RIGHT
                    time = samples[firstRight].t
                }
                firstRight < firstLeft -> {
                    pass = Pass.RIGHT_TO_LEFT
                    time = samples[firstLeft].t
                }
                samples.size >= 2 && abs(last.c - first.c) >= MIN_TREND -> {
                    pass = if (last.c > first.c) Pass.LEFT_TO_RIGHT else Pass.RIGHT_TO_LEFT
                    time = samples.minBy { abs(it.c - divider) }.t
                }
                else -> return CrossingResult.Rejected(CrossingResult.Reason.AMBIGUOUS)
            }
        } else {
            return CrossingResult.Rejected(CrossingResult.Reason.NO_CROSSING)
        }
        val accepted = when (cfg.direction) {
            Direction.LEFT_TO_RIGHT -> pass == Pass.LEFT_TO_RIGHT
            Direction.RIGHT_TO_LEFT -> pass == Pass.RIGHT_TO_LEFT
            else -> true
        }
        return if (accepted) CrossingResult.Crossed(time, pass) else CrossingResult.Rejected(CrossingResult.Reason.WRONG_DIRECTION)
    }

    /** Linear interpolation of when the centroid first crossed the divider in the travel direction. */
    private fun crossingTime(divider: Float, fromLeft: Boolean): Long {
        for (k in 1 until samples.size) {
            val a = samples[k - 1]
            val b = samples[k]
            val crossed = if (fromLeft) a.c < divider && b.c >= divider else a.c >= divider && b.c < divider
            if (crossed) {
                val f = ((divider - a.c) / (b.c - a.c)).coerceIn(0f, 1f)
                return a.t + ((b.t - a.t) * f.toDouble()).toLong()
            }
        }
        return samples.last().t
    }

    companion object {
        const val FAST_PASS_FRAMES = 3
        private const val MIN_TREND = 0.05f
    }
}
