package com.gfpv.camtimer.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** A downsampled grayscale crop of the detection zone, in preview orientation. */
class LumaFrame(val width: Int, val height: Int, val data: ByteArray)

object LumaCropper {
    /**
     * Copies the zone out of a full luma (Y) plane, keeping every [step]th pixel.
     *
     * [zone] is in preview coordinates. With [mirror] (front camera, whose preview is mirrored),
     * preview column x reads image column `imageWidth - 1 - x`, so the crop matches what the
     * user sees on screen.
     */
    fun crop(
        plane: ByteArray,
        rowStride: Int,
        pixelStride: Int,
        imageWidth: Int,
        imageHeight: Int,
        zone: NormRect,
        mirror: Boolean,
        step: Int,
    ): LumaFrame {
        val x0 = (zone.left * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val x1 = (zone.right * imageWidth).toInt().coerceIn(x0 + 1, imageWidth)
        val y0 = (zone.top * imageHeight).toInt().coerceIn(0, imageHeight - 1)
        val y1 = (zone.bottom * imageHeight).toInt().coerceIn(y0 + 1, imageHeight)
        val outW = (x1 - x0 + step - 1) / step
        val outH = (y1 - y0 + step - 1) / step
        val out = ByteArray(outW * outH)
        var i = 0
        var y = y0
        while (y < y1) {
            val rowBase = y * rowStride
            var x = x0
            while (x < x1) {
                val column = if (mirror) imageWidth - 1 - x else x
                out[i++] = plane[rowBase + column * pixelStride]
                x += step
            }
            y += step
        }
        return LumaFrame(outW, outH, out)
    }
}

/**
 * Motion measured between two consecutive frames.
 * Percentages are 0..100; [centroidX] is the mean x of changed pixels, normalized 0..1 within
 * the zone, or NaN when nothing changed.
 */
data class FrameMotion(
    val timestampNanos: Long,
    val leftPct: Float,
    val rightPct: Float,
    val totalPct: Float,
    val centroidX: Float,
)

/**
 * Frame differencing, ported from the web version's detectMotion(): a pixel counts as changed
 * when its intensity differs from the previous frame by more than [pixelThreshold].
 */
class MotionDetector(private val pixelThreshold: Int = DEFAULT_PIXEL_THRESHOLD) {
    private var previous: LumaFrame? = null

    fun reset() {
        previous = null
    }

    /** Returns null for the first frame, or when the zone size changed since the last one. */
    fun process(frame: LumaFrame, dividerX: Float, timestampNanos: Long): FrameMotion? {
        val prev = previous
        previous = frame
        if (prev == null || prev.width != frame.width || prev.height != frame.height) return null

        val w = frame.width
        val h = frame.height
        val split = (dividerX * w).roundToInt().coerceIn(0, w)
        val cur = frame.data
        val old = prev.data
        var leftChanged = 0
        var rightChanged = 0
        var sumX = 0L
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val diff = abs((cur[i].toInt() and 0xFF) - (old[i].toInt() and 0xFF))
                if (diff > pixelThreshold) {
                    if (x < split) leftChanged++ else rightChanged++
                    sumX += x
                }
                i++
            }
        }
        val changed = leftChanged + rightChanged
        return FrameMotion(
            timestampNanos = timestampNanos,
            leftPct = percent(leftChanged, split * h),
            rightPct = percent(rightChanged, (w - split) * h),
            totalPct = percent(changed, w * h),
            centroidX = if (changed == 0) Float.NaN else (sumX.toFloat() / changed + 0.5f) / w,
        )
    }

    companion object {
        const val DEFAULT_PIXEL_THRESHOLD = 40
    }
}

private fun percent(count: Int, total: Int): Float = if (total <= 0) 0f else count * 100f / total
