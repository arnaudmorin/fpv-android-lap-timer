package com.gfpv.camtimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.gfpv.camtimer.core.LapStats
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TrackDao {
    @Query("SELECT * FROM tracks ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY name COLLATE NOCASE")
    abstract suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    abstract fun observe(id: Long): Flow<TrackEntity?>

    @Insert
    abstract suspend fun insert(track: TrackEntity): Long

    @Query("UPDATE tracks SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query(
        "UPDATE tracks SET zoneLeft = :left, zoneTop = :top, zoneRight = :right, zoneBottom = :bottom, " +
            "dividerX = :dividerX WHERE id = :id",
    )
    abstract suspend fun updateZone(id: Long, left: Float, top: Float, right: Float, bottom: Float, dividerX: Float)

    @Query("DELETE FROM laps WHERE sessionId IN (SELECT id FROM sessions WHERE trackId = :trackId)")
    abstract suspend fun deleteLapsOfTrack(trackId: Long)

    @Query("DELETE FROM sessions WHERE trackId = :trackId")
    abstract suspend fun deleteSessionsOfTrack(trackId: Long)

    @Query("DELETE FROM tracks WHERE id = :id")
    abstract suspend fun deleteTrackRow(id: Long)

    @Transaction
    open suspend fun delete(id: Long) {
        deleteLapsOfTrack(id)
        deleteSessionsOfTrack(id)
        deleteTrackRow(id)
    }
}

@Dao
abstract class SessionDao {
    @Query("SELECT * FROM sessions WHERE trackId = :trackId ORDER BY startedAt DESC")
    abstract fun observeForTrack(trackId: Long): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM laps WHERE sessionId IN (SELECT id FROM sessions WHERE trackId = :trackId) " +
            "ORDER BY sessionId, lapIndex",
    )
    abstract fun observeLapsForTrack(trackId: Long): Flow<List<LapEntity>>

    @Insert
    abstract suspend fun insertSession(session: SessionEntity): Long

    @Insert
    abstract suspend fun insertLaps(laps: List<LapEntity>)

    @Query("SELECT timeMs FROM laps WHERE sessionId = :sessionId ORDER BY lapIndex")
    abstract suspend fun lapTimes(sessionId: Long): List<Long>

    @Query("UPDATE sessions SET bestLapMs = :bestLapMs, best3TotalMs = :best3TotalMs WHERE id = :sessionId")
    abstract suspend fun updateBests(sessionId: Long, bestLapMs: Long?, best3TotalMs: Long?)

    @Query("DELETE FROM laps WHERE sessionId = :sessionId")
    abstract suspend fun deleteLaps(sessionId: Long)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    abstract suspend fun deleteSessionRow(sessionId: Long)

    @Transaction
    open suspend fun insertWithLaps(session: SessionEntity, lapsMs: List<Long>): Long {
        val id = insertSession(session)
        insertLaps(lapsMs.mapIndexed { i, ms -> LapEntity(id, i, ms) })
        return id
    }

    /**
     * Removes one lap, renumbers the following ones and recomputes the session records.
     * A session left without laps is deleted. Returns true when the session still exists.
     */
    @Transaction
    open suspend fun removeLap(sessionId: Long, lapIndex: Int): Boolean {
        val laps = lapTimes(sessionId).toMutableList()
        if (lapIndex !in laps.indices) return laps.isNotEmpty()
        laps.removeAt(lapIndex)
        if (laps.isEmpty()) {
            delete(sessionId)
            return false
        }
        deleteLaps(sessionId)
        insertLaps(laps.mapIndexed { i, ms -> LapEntity(sessionId, i, ms) })
        updateBests(sessionId, LapStats.bestLap(laps), LapStats.best3Consecutive(laps)?.totalMs)
        return true
    }

    @Transaction
    open suspend fun delete(sessionId: Long) {
        deleteLaps(sessionId)
        deleteSessionRow(sessionId)
    }
}
