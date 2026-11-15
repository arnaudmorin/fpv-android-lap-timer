package com.gfpv.camtimer.core

import java.util.Locale

enum class VoiceLanguage { FRENCH, ENGLISH, SYSTEM }

enum class AnnounceFormat { LAP_AND_TIME, TIME_ONLY }

/** Spoken phrases. Times use the language's decimal separator so the TTS engine reads them naturally. */
object Announcements {
    fun lap(french: Boolean, number: Int, durationMs: Long, format: AnnounceFormat): String {
        val seconds = durationMs / 1000.0
        return if (french) {
            val time = String.format(Locale.FRENCH, "%.2f", seconds)
            if (format == AnnounceFormat.LAP_AND_TIME) "Tour $number, $time secondes" else time
        } else {
            val time = String.format(Locale.ENGLISH, "%.2f", seconds)
            if (format == AnnounceFormat.LAP_AND_TIME) "Lap $number, $time seconds" else time
        }
    }

    fun start(french: Boolean): String = if (french) "Départ" else "Start"
}
