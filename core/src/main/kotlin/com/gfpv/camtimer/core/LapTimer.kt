package com.gfpv.camtimer.core

sealed interface LapEvent {
    /** First gate pass of the session: lap 1 starts, nothing is recorded yet. */
    data object Started : LapEvent

    data class Lap(val number: Int, val durationNanos: Long) : LapEvent
}

/** Port of the web version's triggerLap(): debounced gate passes turned into lap times. */
class LapTimer(minLapNanos: Long) {
    @Volatile
    var minLapNanos: Long = minLapNanos

    private val _laps = ArrayList<Long>()
    val laps: List<Long> get() = _laps

    private var lastTriggerNanos: Long? = null
    private var lapStartNanos: Long? = null

    fun reset() {
        _laps.clear()
        lastTriggerNanos = null
        lapStartNanos = null
    }

    /** Drops a wrong lap; later laps move up one place, timing of the current lap is unchanged. */
    fun removeLap(index: Int) {
        if (index in _laps.indices) _laps.removeAt(index)
    }

    /** Returns null when the trigger falls inside the minimum lap time of the previous one. */
    fun onTrigger(timestampNanos: Long): LapEvent? {
        val last = lastTriggerNanos
        if (last != null && timestampNanos - last < minLapNanos) return null
        lastTriggerNanos = timestampNanos
        val start = lapStartNanos
        lapStartNanos = timestampNanos
        if (start == null) return LapEvent.Started
        val duration = timestampNanos - start
        _laps += duration
        return LapEvent.Lap(_laps.size, duration)
    }
}
