package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "local_pole_photo")
data class LocalPolePhotoEntity(
    @PrimaryKey val photoId: String,
    val workOrderId: String,
    val poleId: String,
    val filePath: String,
    val capturedAt: Instant,
    val lat: Double,
    val lng: Double,
    // AI classification result, filled in after fetchResult() reads it back from the server -
    // null until then. See PolePhotoRepository.fetchResult and contract-drift.md "Pole photo endpoints".
    val aiStatus: String? = null,
    val cvState: String? = null,
    val confidence: Double? = null,
    val modelVersion: String? = null,
    val analyzedAt: Instant? = null,
    val decision: String? = null,
    val decisionNote: String? = null,
    val decidedBy: String? = null,
    val decidedAt: Instant? = null,
)
