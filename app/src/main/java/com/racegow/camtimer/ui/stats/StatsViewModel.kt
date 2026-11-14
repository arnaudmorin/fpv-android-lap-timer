package com.racegow.camtimer.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racegow.camtimer.AppContainer
import com.racegow.camtimer.core.Best3
import com.racegow.camtimer.core.LapStats
import com.racegow.camtimer.data.SessionEntity
import com.racegow.camtimer.data.TrackEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionSummary(val session: SessionEntity, val lapsMs: List<Long>)

data class StatsUiState(
    val track: TrackEntity? = null,
    /** Newest first. */
    val sessions: List<SessionSummary> = emptyList(),
    val bestLapMs: Long? = null,
    val best3: Best3? = null,
)

class StatsViewModel(private val container: AppContainer) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<StatsUiState> = container.activeTrack
        .flatMapLatest { track ->
            if (track == null) {
                flowOf(StatsUiState())
            } else {
                combine(
                    container.sessionDao.observeForTrack(track.id),
                    container.sessionDao.observeLapsForTrack(track.id),
                ) { sessions, laps ->
                    val lapsBySession = laps.groupBy({ it.sessionId }, { it.timeMs })
                    val summaries = sessions.map { SessionSummary(it, lapsBySession[it.id].orEmpty()) }
                    StatsUiState(
                        track = track,
                        sessions = summaries,
                        bestLapMs = summaries.mapNotNull { it.session.bestLapMs }.minOrNull(),
                        best3 = summaries.mapNotNull { LapStats.best3Consecutive(it.lapsMs) }.minByOrNull { it.totalMs },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun deleteSession(id: Long) {
        viewModelScope.launch { container.sessionDao.delete(id) }
    }
}
