package com.luxmap.feature.survey.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PolePhotoUploadResponseDto(
    @SerialName("photo_id") val photoId: String,
    @SerialName("ai_status") val aiStatus: String? = null,
    @SerialName("cv_state") val cvState: String? = null,
    val confidence: Double? = null,
    @SerialName("model_version") val modelVersion: String? = null,
    @SerialName("analyzed_at") val analyzedAt: String? = null,
)
