package com.luxmap.feature.polelocation.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

object PoleLocationUpdateStatus {
    const val PENDING = "pending"
    const val QUEUED = "queued"
    const val DONE = "done"
    const val CONFLICT = "conflict"
    const val FAILED = "failed"
}

@Entity(tableName = "local_pole_location_update")
data class LocalPoleLocationUpdateEntity(
    @PrimaryKey val poleId: String,
    val photoFilePath: String,
    val lat: Double,
    val lng: Double,
    val capturedAt: Instant,
    val status: String,
    val clientOpId: String,
)
