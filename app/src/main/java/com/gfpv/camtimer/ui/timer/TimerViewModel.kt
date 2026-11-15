package com.gfpv.camtimer.ui.timer

import android.os.SystemClock
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gfpv.camtimer.AppContainer
import com.gfpv.camtimer.camera.LumaFrameListener
import com.gfpv.camtimer.camera.MotionAnalyzer
import com.gfpv.camtimer.core.CrossingDetector
import com.gfpv.camtimer.core.CrossingResult
import com.gfpv.camtimer.core.FrameMotion
import com.gfpv.camtimer.core.LapEvent
import com.gfpv.camtimer.core.LapStats
import com.gfpv.camtimer.core.LapTimer
import com.gfpv.camtimer.core.LumaCropper
import com.gfpv.camtimer.core.MotionDetector
import com.gfpv.camtimer.core.NormRect
import com.gfpv.camtimer.data.AppSettings
import com.gfpv.camtimer.data.SessionEntity
import com.gfpv.camtimer.data.TrackEntity
import com.gfpv.camtimer.data.zone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

enum class FlashKind { LAP, CROSSED, REJECTED }

/** A short colored flash of the zone; [id] makes every flash a distinct value. */
data class Flash(val id: Long, val kind: FlashKind)

data class TimerUiState(
    val running: Boolean = false,
    /** The first gate pass happened, lap 1 is being timed. */
    val started: Boolean = false,
    val lapsMs: List<Long> = emptyList(),
    val startedAtRealtime: Long = 0L,
    val lapStartedAtRealtime: Long = 0L,
)

/**
 * Runs detection on the camera's analysis thread and keeps the live session.
 * Crossings are also detected when the timer is stopped, so the zone can be tested before a run.
 */
class TimerViewModel(private val container: AppContainer) : ViewModel(), LumaFrameListener {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analyzer = MotionAnalyzer(this)

    // Only touched on the analysis thread, except lapTimer which is guarded by [lock].
    private val motionDetector = MotionDetector()
    private val crossingDetector = CrossingDetector()
    private val lapTimer = LapTimer(minLapNanos = 1_000_000_000L)
    private val lock = Any()

    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    private val _motion = MutableStateFlow<FrameMotion?>(null)
    val motion: StateFlow<FrameMotion?> = _motion.asStateFlow()

    private val _flash = MutableStateFlow<Flash?>(null)
    val flash: StateFlow<Flash?> = _flash.asStateFlow()

    /** Width / height of the upright analysis frame; the preview box uses the same ratio. */
    private val _frameAspect = MutableStateFlow(3f / 4f)
    val frameAspect: StateFlow<Float> = _frameAspect.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val flashIds = AtomicLong()

    @Volatile private var zone = NormRect.DEFAULT
    @Volatile private var mirror = false
    @Volatile private var running = false
    @Volatile private var settings = AppSettings()
    private var track: TrackEntity? = null
    private var sessionStartedAt = 0L

    /** Session saved by the last Stop, so laps deleted afterwards are removed from it too. */
    private var savedSessionId: Long? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch { container.settings.settings.collect { applySettings(it) } }
        viewModelScope.launch {
            container.activeTrack.collect { t ->
                // Laps on screen belong to the previous track: start from a clean timer.
                if (track != null && t?.id != track?.id) clear()
                track = t
                if (t != null) setZone(t.zone, t.dividerX)
            }
        }
    }

    private fun applySettings(s: AppSettings) {
        settings = s
        crossingDetector.config = crossingDetector.config.copy(
            direction = s.direction,
            sensitivityPct = s.sensitivity,
            gapFrames = s.gapFrames,
            maxTransitNanos = s.maxTransitMs * 1_000_000L,
        )
        lapTimer.minLapNanos = (s.minLapSeconds * 1_000_000_000.0).toLong()
        if (!s.showDebug) _motion.value = null
    }

    /** Live zone while it is being dragged; MainViewModel.saveZone persists it. */
    fun setZone(zone: NormRect, dividerX: Float) {
        this.zone = zone
        crossingDetector.config = crossingDetector.config.copy(dividerX = dividerX)
    }

    suspend fun bindCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, rotation: Int) {
        val s = container.settings.settings.first()
        try {
            val bound = container.cameras.bind(owner, surfaceProvider, rotation, s.cameraId, s.fps, analyzer, analysisExecutor)
            mirror = bound.option.isFront
            analysisExecutor.execute {
                motionDetector.reset()
                crossingDetector.reset()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _messages.emit("Camera error: ${e.message}")
        }
    }

    fun unbindCamera() = container.cameras.unbindAll()

    override fun onLumaFrame(plane: ByteArray, rowStride: Int, pixelStride: Int, width: Int, height: Int, timestampNanos: Long) {
        val aspect = width.toFloat() / height
        if (_frameAspect.value != aspect) _frameAspect.value = aspect

        val crop = LumaCropper.crop(plane, rowStride, pixelStride, width, height, zone, mirror, ANALYSIS_STEP)
        val motion = motionDetector.process(crop, crossingDetector.config.dividerX, timestampNanos) ?: return
        if (settings.showDebug) _motion.value = motion

        when (val result = crossingDetector.onFrame(motion)) {
            null -> Unit
            is CrossingResult.Rejected -> flash(FlashKind.REJECTED)
            is CrossingResult.Crossed -> if (running) onGatePass(result.timestampNanos) else flash(FlashKind.CROSSED)
        }
    }

    private fun onGatePass(timestampNanos: Long) {
        val event = synchronized(lock) { if (running) lapTimer.onTrigger(timestampNanos) else null } ?: return
        flash(FlashKind.LAP)
        val now = SystemClock.elapsedRealtime()
        val s = settings
        when (event) {
            LapEvent.Started -> {
                _state.update { it.copy(started = true, startedAtRealtime = now, lapStartedAtRealtime = now) }
                if (s.announce) container.announcer.announceStart()
            }
            is LapEvent.Lap -> {
                val ms = event.durationNanos / 1_000_000
                _state.update { it.copy(lapsMs = it.lapsMs + ms, lapStartedAtRealtime = now) }
                if (s.announce) container.announcer.announceLap(event.number, ms, s.announceFormat)
            }
        }
    }

    private fun flash(kind: FlashKind) {
        _flash.value = Flash(flashIds.incrementAndGet(), kind)
    }

    fun start() {
        if (running || track == null) return
        synchronized(lock) {
            lapTimer.reset()
            running = true
        }
        sessionStartedAt = System.currentTimeMillis()
        savedSessionId = null
        analysisExecutor.execute { crossingDetector.reset() }
        _state.value = TimerUiState(running = true)
    }

    /** Stops the session and saves it to the active track when it has laps. */
    fun stop(backgrounded: Boolean = false) {
        if (!running) return
        val laps = synchronized(lock) {
            running = false
            lapTimer.laps.map { it / 1_000_000 }
        }
        _state.update { it.copy(running = false) }
        val trackId = track?.id
        if (laps.isEmpty() || trackId == null) {
            _messages.tryEmit(if (backgrounded) "Session stopped: the app went to the background" else "Stopped: no laps recorded")
            return
        }
        val session = SessionEntity(
            trackId = trackId,
            startedAt = sessionStartedAt,
            bestLapMs = LapStats.bestLap(laps),
            best3TotalMs = LapStats.best3Consecutive(laps)?.totalMs,
        )
        saveJob = viewModelScope.launch {
            savedSessionId = container.sessionDao.insertWithLaps(session, laps)
            val prefix = if (backgrounded) "App went to the background. " else ""
            _messages.emit("${prefix}Session saved: ${laps.size} laps")
        }
    }

    fun clear() {
        if (running) return
        _state.value = TimerUiState()
        savedSessionId = null
    }

    /**
     * Deletes a wrong lap (e.g. a false trigger). During a session it is simply dropped; after
     * Stop & save it is also removed from the saved session.
     */
    fun deleteLap(index: Int) {
        if (index !in _state.value.lapsMs.indices) return
        if (running) {
            // Same lock as onGatePass, so both lists stay index-aligned.
            synchronized(lock) {
                lapTimer.removeLap(index)
                _state.update { it.copy(lapsMs = it.lapsMs.withoutIndex(index)) }
            }
            return
        }
        _state.update { it.copy(lapsMs = it.lapsMs.withoutIndex(index)) }
        val job = saveJob
        viewModelScope.launch {
            job?.join()
            val id = savedSessionId ?: return@launch
            if (!container.sessionDao.removeLap(id, index)) savedSessionId = null
        }
    }

    override fun onCleared() {
        analysisExecutor.shutdown()
    }

    private fun List<Long>.withoutIndex(index: Int) = filterIndexed { i, _ -> i != index }

    private companion object {
        /** Every 2nd pixel in both directions: 4x less work, plenty for a drone-sized blob. */
        const val ANALYSIS_STEP = 2
    }
}
