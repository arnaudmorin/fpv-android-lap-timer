package com.gfpv.camtimer

import android.content.Context
import androidx.room.Room
import com.gfpv.camtimer.camera.CameraRepository
import com.gfpv.camtimer.data.AppDatabase
import com.gfpv.camtimer.data.SessionDao
import com.gfpv.camtimer.data.SettingsRepository
import com.gfpv.camtimer.data.TrackDao
import com.gfpv.camtimer.data.TrackEntity
import com.gfpv.camtimer.tts.LapAnnouncer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Manual dependency injection: one instance of each long-lived object for the whole app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "camtimer.db").build()

    val trackDao: TrackDao = database.trackDao()
    val sessionDao: SessionDao = database.sessionDao()
    val settings = SettingsRepository(appContext)
    val cameras = CameraRepository(appContext)
    val announcer = LapAnnouncer(appContext)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTrack: Flow<TrackEntity?> = settings.settings
        .map { it.activeTrackId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else trackDao.observe(id) }
}
