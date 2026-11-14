package com.racegow.camtimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Transaction
    open suspend fun delete(sessionId: Long) {
        deleteLaps(sessionId)
        deleteSessionRow(sessionId)
    }
}
