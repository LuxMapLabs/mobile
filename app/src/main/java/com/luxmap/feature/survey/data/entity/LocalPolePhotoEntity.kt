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
    // The real server-assigned photo_id, filled in by UploadPolePhotoSyncHandler once the
    // upload succeeds. Server generates its own id (WorkOrderPolePhotoService.cs) - it is NOT
    // the same as photoId above (that is only this row's local primary key / client_op_id).
    // GET .../pole-photos/{photo_id} must use this field, never photoId.
    val serverPhotoId: String? = null,
    // AI classification result, filled in after fetchResult() reads it back from the server -
    // null until then. See PolePhotoRepository.fetchResult and contract-drift.md "Pole photo endpoints".
    val aiStatus: String? = null,
    val cvState: String? = null,
    val confidence: Double? = null,
    val modelVersion: String? = null,
    val analyzedAt: Instant? = null,
    // Field Engineer's correction/agreement on cvState, added 2026-10-20 (ticket PHOTO-REVIEW).
    // Null until reviewed - never defaults to cvState. See contract-drift.md "PATCH .../review".
    val finalCvState: String? = null,
    val reviewNote: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: Instant? = null,
    val decision: String? = null,
    val decisionNote: String? = null,
    val decidedBy: String? = null,
    val decidedAt: Instant? = null,
)
