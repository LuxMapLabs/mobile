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
)
