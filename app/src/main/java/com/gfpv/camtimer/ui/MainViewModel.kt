package com.gfpv.camtimer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gfpv.camtimer.AppContainer
import com.gfpv.camtimer.camera.CameraOption
import com.gfpv.camtimer.core.NormRect
import com.gfpv.camtimer.data.AppSettings
import com.gfpv.camtimer.data.TrackEntity
import com.gfpv.camtimer.tts.VoiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings, tracks and camera list shared by the Tracks and Settings tabs. */
class MainViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val tracks: StateFlow<List<TrackEntity>> =
        container.trackDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activeTrack: StateFlow<TrackEntity?> =
        container.activeTrack.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val voiceStatus: StateFlow<VoiceStatus> = container.announcer.status

    private val _cameras = MutableStateFlow<List<CameraOption>?>(null)
    val cameras: StateFlow<List<CameraOption>?> = _cameras.asStateFlow()

    init {
        viewModelScope.launch { ensureActiveTrack() }
        viewModelScope.launch {
            container.settings.settings.collect { container.announcer.configure(it.voiceLanguage, it.speechRate) }
        }
    }

    /** First launch gets a ready-to-use track; a dangling active id falls back to the first track. */
    private suspend fun ensureActiveTrack() {
        val current = container.settings.settings.first()
        val all = container.trackDao.getAll()
        if (all.isEmpty()) {
            val id = container.trackDao.insert(TrackEntity(name = "Track 1"))
            container.settings.update { it.copy(activeTrackId = id) }
        } else if (all.none { it.id == current.activeTrackId }) {
            container.settings.update { it.copy(activeTrackId = all.first().id) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun selectTrack(id: Long) = updateSettings { it.copy(activeTrackId = id) }

    private fun isNameAvailable(name: String, exceptId: Long? = null) =
        name.isNotEmpty() && tracks.value.none { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    /** Returns false when the name is blank or already used. */
    fun createTrack(name: String): Boolean {
        val trimmed = name.trim()
        if (!isNameAvailable(trimmed)) return false
        viewModelScope.launch {
            val id = container.trackDao.insert(TrackEntity(name = trimmed))
            container.settings.update { it.copy(activeTrackId = id) }
        }
        return true
    }

    /** Returns false when the name is blank or used by another track. */
    fun renameTrack(id: Long, name: String): Boolean {
        val trimmed = name.trim()
        if (!isNameAvailable(trimmed, exceptId = id)) return false
        viewModelScope.launch { container.trackDao.rename(id, trimmed) }
        return true
    }

    fun deleteTrack(id: Long) {
        viewModelScope.launch {
            container.trackDao.delete(id)
            if (settings.value.activeTrackId == id) {
                val next = container.trackDao.getAll().firstOrNull()?.id
                container.settings.update { it.copy(activeTrackId = next) }
            }
        }
    }

    fun saveZone(trackId: Long, zone: NormRect, dividerX: Float) {
        val z = zone.coerced()
        viewModelScope.launch {
            container.trackDao.updateZone(trackId, z.left, z.top, z.right, z.bottom, dividerX.coerceIn(0.1f, 0.9f))
        }
    }

    fun loadCameras() {
        viewModelScope.launch {
            _cameras.value = runCatching { container.cameras.cameras() }.getOrDefault(emptyList())
        }
    }

    /** A new camera has its own frame rates, so the rate goes back to automatic. */
    fun selectCamera(id: String) = updateSettings { it.copy(cameraId = id, fps = 0) }

    fun testVoice() = container.announcer.testVoice(settings.value.announceFormat)

    fun openTtsSettings(context: Context) = container.announcer.openTtsSettings(context)

    fun openInstallVoiceData(context: Context) = container.announcer.openInstallVoiceData(context)
}
