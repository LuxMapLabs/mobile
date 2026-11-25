package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "local_lux_reading")
data class LocalLuxReadingEntity(
    @PrimaryKey val clientOpId: String,
    val workOrderId: String,
    val poleId: String,
    val photoId: String,
    val measuredAt: Instant,
    val luxValue: Double,
    val meterModel: String,
    val dataSource: String,
    val serverLuxId: String? = null,
    val createdAt: Instant,
    /** LUX-BASELINE stationary protocol; all null on readings recorded before it (sent as reference readings). */
    val samplesJson: String? = null,
    val moduleId: String? = null,
    val sensorHeightM: Double? = null,
)
