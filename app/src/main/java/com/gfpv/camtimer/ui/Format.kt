package com.gfpv.camtimer.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Lap time with milliseconds, using the phone's decimal separator ("12,345 s" in French). */
fun formatLap(ms: Long?): String =
    if (ms == null) "–" else String.format(Locale.getDefault(), "%.3f s", ms / 1000.0)

/** Compact form for lists of laps. */
fun formatShort(ms: Long): String = String.format(Locale.getDefault(), "%.2f", ms / 1000.0)

fun formatDateTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
