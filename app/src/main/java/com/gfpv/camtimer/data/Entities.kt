package com.gfpv.camtimer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gfpv.camtimer.core.NormRect

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val zoneLeft: Float = NormRect.DEFAULT.left,
    val zoneTop: Float = NormRect.DEFAULT.top,
    val zoneRight: Float = NormRect.DEFAULT.right,
    val zoneBottom: Float = NormRect.DEFAULT.bottom,
    /** Gate line position, normalized within the zone. */
    val dividerX: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis(),
)

val TrackEntity.zone: NormRect get() = NormRect(zoneLeft, zoneTop, zoneRight, zoneBottom)

@Entity(tableName = "sessions", indices = [Index("trackId")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val startedAt: Long,
    val bestLapMs: Long?,
    val best3TotalMs: Long?,
)

@Entity(tableName = "laps", primaryKeys = ["sessionId", "lapIndex"], indices = [Index("sessionId")])
data class LapEntity(
    val sessionId: Long,
    val lapIndex: Int,
    val timeMs: Long,
)
