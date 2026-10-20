package com.luxmap.feature.survey.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Full PolePhotoItem shape confirmed against luxmap_backend dev branch source
// (WorkOrderPolePhotoContracts.cs) - see docs/contract-drift.md "Pole photo endpoints" rows.
@Serializable
data class PolePhotoItemDto(
    @SerialName("photo_id") val photoId: String,
    @SerialName("work_order_id") val workOrderId: String,
    @SerialName("pole_id") val poleId: String,
    @SerialName("data_source") val dataSource: String,
    @SerialName("captured_at") val capturedAt: String,
    val lat: Double,
    val lng: Double,
    @SerialName("uploaded_by") val uploadedBy: String,
    @SerialName("uploaded_at") val uploadedAt: String,
    @SerialName("ai_status") val aiStatus: String,
    @SerialName("cv_state") val cvState: String? = null,
    val confidence: Double? = null,
    @SerialName("model_version") val modelVersion: String? = null,
    @SerialName("artifact_version") val artifactVersion: String? = null,
    @SerialName("analyzed_at") val analyzedAt: String? = null,
    // Added 2026-10-20 (ticket PHOTO-REVIEW) - the Field Engineer's correction/agreement on
    // cvState before a Manager decides. Null until reviewed, never defaults to cvState -
    // see docs/contract-drift.md "PATCH .../review".
    @SerialName("final_cv_state") val finalCvState: String? = null,
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    val decision: String? = null,
    @SerialName("decided_by") val decidedBy: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("decision_note") val decisionNote: String? = null,
    @SerialName("status_applied") val statusApplied: Boolean? = null,
    @SerialName("status_reason") val statusReason: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("original_url") val originalUrl: String? = null,
)

// PATCH .../pole-photos/{photo_id}/review body - see docs/contract-drift.md "PATCH .../review".
// finalCvState must be exactly "on" or "off" (never "inconclusive", never null).
@Serializable
data class PolePhotoReviewRequestDto(
    @SerialName("final_cv_state") val finalCvState: String,
    @SerialName("review_note") val reviewNote: String? = null,
)
