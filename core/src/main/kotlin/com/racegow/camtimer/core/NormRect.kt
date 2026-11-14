package com.racegow.camtimer.core

/**
 * Rectangle in normalized 0..1 coordinates of the camera preview (x to the right, y down).
 * Storing the zone this way keeps it valid whatever the camera resolution is.
 */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun withLeft(value: Float) = copy(left = value.coerceIn(0f, right - MIN_SIZE))
    fun withRight(value: Float) = copy(right = value.coerceIn(left + MIN_SIZE, 1f))
    fun withTop(value: Float) = copy(top = value.coerceIn(0f, bottom - MIN_SIZE))
    fun withBottom(value: Float) = copy(bottom = value.coerceIn(top + MIN_SIZE, 1f))

    /** Moves the rectangle by (dx, dy) without leaving the 0..1 frame. */
    fun movedBy(dx: Float, dy: Float): NormRect {
        val mx = dx.coerceIn(-left, 1f - right)
        val my = dy.coerceIn(-top, 1f - bottom)
        return NormRect(left + mx, top + my, right + mx, bottom + my)
    }

    /** Clamps into the frame and enforces the minimum size. */
    fun coerced(): NormRect {
        val l = left.coerceIn(0f, 1f - MIN_SIZE)
        val t = top.coerceIn(0f, 1f - MIN_SIZE)
        return NormRect(l, t, right.coerceIn(l + MIN_SIZE, 1f), bottom.coerceIn(t + MIN_SIZE, 1f))
    }

    companion object {
        const val MIN_SIZE = 0.05f
        val DEFAULT = NormRect(0.2f, 0.2f, 0.8f, 0.8f)
    }
}
