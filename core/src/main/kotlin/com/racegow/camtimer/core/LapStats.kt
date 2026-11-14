package com.racegow.camtimer.core

data class Best3(val totalMs: Long, val startIndex: Int, val lapsMs: List<Long>)

object LapStats {
    fun bestLap(lapsMs: List<Long>): Long? = lapsMs.minOrNull()

    /** Lowest sum of three consecutive laps, as in the web version's calculateBest3Consecutive(). */
    fun best3Consecutive(lapsMs: List<Long>): Best3? {
        if (lapsMs.size < 3) return null
        var bestStart = 0
        var bestSum = Long.MAX_VALUE
        for (i in 0..lapsMs.size - 3) {
            val sum = lapsMs[i] + lapsMs[i + 1] + lapsMs[i + 2]
            if (sum < bestSum) {
                bestSum = sum
                bestStart = i
            }
        }
        return Best3(bestSum, bestStart, lapsMs.subList(bestStart, bestStart + 3).toList())
    }
}
