package com.luxmap.feature.workorder.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CompleteWorkOrderRequestDto(
    @SerialName("report_note") val reportNote: String,
    @SerialName("materials_used") val materialsUsed: String? = null,
    @SerialName("fault_outcomes") val faultOutcomes: List<FaultOutcomeRequestDto>? = null,
)

@Serializable
data class FaultOutcomeRequestDto(
    @SerialName("fault_id") val faultId: String,
    val outcome: String,
)

@Serializable
data class EvidenceItemDto(
    @SerialName("evidence_id") val evidenceId: String,
    val kind: String,
    @SerialName("captured_at") val capturedAt: String,
    val lat: Double,
    val lng: Double,
    @SerialName("thumbnail_url") val thumbnailUrl: String,
    @SerialName("original_url") val originalUrl: String,
)
