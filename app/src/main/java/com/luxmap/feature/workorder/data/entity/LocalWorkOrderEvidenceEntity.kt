package com.luxmap.feature.workorder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "local_work_order_evidence")
data class LocalWorkOrderEvidenceEntity(
    @PrimaryKey val clientOpId: String,
    val workOrderId: String,
    val kind: String,
    val filePath: String,
    val capturedAt: Instant,
    val lat: Double,
    val lng: Double,
    val uploadStatus: String,
)
