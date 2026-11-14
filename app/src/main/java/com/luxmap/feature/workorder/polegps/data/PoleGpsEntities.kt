package com.luxmap.feature.workorder.polegps.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

object AttachmentStatusColumn {
    const val PENDING = "pending"
    const val DOWNLOADING = "downloading"
    const val READY = "ready"
    const val FAILED = "failed"
}

object MeasurementSourceColumn {
    const val DEVICE = "device"
    const val SERVER = "server"
}

@Entity(tableName = "local_pole_gps_order")
data class LocalPoleGpsOrderEntity(
    @PrimaryKey val workOrderId: String,
    val detailJson: String,
    val fetchedAt: Instant,
)

@Entity(tableName = "local_work_order_attachment", primaryKeys = ["workOrderId", "attachmentId"])
data class LocalWorkOrderAttachmentEntity(
    val workOrderId: String,
    val attachmentId: String,
    val fileName: String,
    val byteCount: Long,
    val downloadUrl: String,
    val status: String,
    val localPath: String?,
)

@Entity(tableName = "local_pole_gps_measurement", primaryKeys = ["workOrderId", "poleId"])
data class LocalPoleGpsMeasurementEntity(
    val workOrderId: String,
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
    val source: String,
)
