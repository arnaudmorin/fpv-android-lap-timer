package com.racegow.camtimer.tts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.racegow.camtimer.core.AnnounceFormat
import com.racegow.camtimer.core.Announcements
import com.racegow.camtimer.core.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class VoiceStatus { INITIALIZING, READY, LANGUAGE_MISSING, UNAVAILABLE }

/** Speaks lap times with the system default TTS engine. */
class LapAnnouncer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    // Music playing on the phone is ducked while a lap is announced.
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private val _status = MutableStateFlow(VoiceStatus.INITIALIZING)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    @Volatile private var ready = false
    @Volatile private var french = true
    private var language = VoiceLanguage.FRENCH
    private var rate = 1f
    private val utteranceIds = AtomicLong()
    private val tts: TextToSpeech = TextToSpeech(appContext) { onInit(it) }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _status.value = VoiceStatus.UNAVAILABLE
            return
        }
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = releaseFocusIfIdle()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = releaseFocusIfIdle()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = releaseFocusIfIdle()
        })
        ready = true
        applyVoice()
    }

    fun configure(language: VoiceLanguage, speechRate: Float) {
        synchronized(this) {
            this.language = language
            this.rate = speechRate
        }
        if (ready) applyVoice()
    }

    private fun applyVoice(): Unit = synchronized(this) {
        val wanted = when (language) {
            VoiceLanguage.FRENCH -> Locale.FRENCH
            VoiceLanguage.ENGLISH -> Locale.ENGLISH
            VoiceLanguage.SYSTEM -> Locale.getDefault()
        }
        val result = tts.setLanguage(wanted)
        val effective = if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Keep speaking in the system language until the voice data is installed.
            tts.setLanguage(Locale.getDefault())
            _status.value = VoiceStatus.LANGUAGE_MISSING
            Locale.getDefault()
        } else {
            _status.value = VoiceStatus.READY
            wanted
        }
        french = effective.language == Locale.FRENCH.language
        tts.setSpeechRate(rate)
        Unit
    }

    fun announceStart() = speak(Announcements.start(french))

    fun announceLap(number: Int, durationMs: Long, format: AnnounceFormat) =
        speak(Announcements.lap(french, number, durationMs, format))

    fun testVoice(format: AnnounceFormat) = announceLap(3, 12_340, format)

    private fun speak(text: String) {
        if (!ready) return
        audioManager.requestAudioFocus(focusRequest)
        // A new lap interrupts an announcement that is still running.
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lap-${utteranceIds.incrementAndGet()}")
    }

    private fun releaseFocusIfIdle() {
        if (!tts.isSpeaking) audioManager.abandonAudioFocusRequest(focusRequest)
    }

    /** Opens the engine's screen for downloading voice data (e.g. the French voice). */
    fun openInstallVoiceData(context: Context) = launch(context, Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))

    fun openTtsSettings(context: Context) = launch(context, Intent("com.android.settings.TTS_SETTINGS"))

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }
}
