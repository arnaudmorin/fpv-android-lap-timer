package com.racegow.camtimer.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.racegow.camtimer.core.AnnounceFormat
import com.racegow.camtimer.core.Direction
import com.racegow.camtimer.core.VoiceLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val activeTrackId: Long? = null,
    val cameraId: String? = null,
    /** Target frame rate; 0 lets the camera decide. */
    val fps: Int = 0,
    val direction: Direction = Direction.LEFT_TO_RIGHT,
    val sensitivity: Float = 5f,
    val minLapSeconds: Float = 1f,
    val maxTransitMs: Int = 600,
    val gapFrames: Int = 3,
    val showDebug: Boolean = false,
    val announce: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.FRENCH,
    val announceFormat: AnnounceFormat = AnnounceFormat.LAP_AND_TIME,
    val speechRate: Float = 1f,
    val keepScreenOn: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> transform(prefs.toSettings()).writeTo(prefs) }
    }

    private object Keys {
        val activeTrack = longPreferencesKey("active_track_id")
        val camera = stringPreferencesKey("camera_id")
        val fps = intPreferencesKey("fps")
        val direction = stringPreferencesKey("direction")
        val sensitivity = floatPreferencesKey("sensitivity")
        val minLap = floatPreferencesKey("min_lap_seconds")
        val maxTransit = intPreferencesKey("max_transit_ms")
        val gapFrames = intPreferencesKey("gap_frames")
        val showDebug = booleanPreferencesKey("show_debug")
        val announce = booleanPreferencesKey("announce")
        val voiceLanguage = stringPreferencesKey("voice_language")
        val announceFormat = stringPreferencesKey("announce_format")
        val speechRate = floatPreferencesKey("speech_rate")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            activeTrackId = this[Keys.activeTrack],
            cameraId = this[Keys.camera],
            fps = this[Keys.fps] ?: d.fps,
            direction = enumOrDefault(this[Keys.direction], d.direction),
            sensitivity = this[Keys.sensitivity] ?: d.sensitivity,
            minLapSeconds = this[Keys.minLap] ?: d.minLapSeconds,
            maxTransitMs = this[Keys.maxTransit] ?: d.maxTransitMs,
            gapFrames = this[Keys.gapFrames] ?: d.gapFrames,
            showDebug = this[Keys.showDebug] ?: d.showDebug,
            announce = this[Keys.announce] ?: d.announce,
            voiceLanguage = enumOrDefault(this[Keys.voiceLanguage], d.voiceLanguage),
            announceFormat = enumOrDefault(this[Keys.announceFormat], d.announceFormat),
            speechRate = this[Keys.speechRate] ?: d.speechRate,
            keepScreenOn = this[Keys.keepScreenOn] ?: d.keepScreenOn,
        )
    }

    private fun AppSettings.writeTo(prefs: MutablePreferences) {
        if (activeTrackId != null) prefs[Keys.activeTrack] = activeTrackId else prefs.remove(Keys.activeTrack)
        if (cameraId != null) prefs[Keys.camera] = cameraId else prefs.remove(Keys.camera)
        prefs[Keys.fps] = fps
        prefs[Keys.direction] = direction.name
        prefs[Keys.sensitivity] = sensitivity
        prefs[Keys.minLap] = minLapSeconds
        prefs[Keys.maxTransit] = maxTransitMs
        prefs[Keys.gapFrames] = gapFrames
        prefs[Keys.showDebug] = showDebug
        prefs[Keys.announce] = announce
        prefs[Keys.voiceLanguage] = voiceLanguage.name
        prefs[Keys.announceFormat] = announceFormat.name
        prefs[Keys.speechRate] = speechRate
        prefs[Keys.keepScreenOn] = keepScreenOn
    }
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
