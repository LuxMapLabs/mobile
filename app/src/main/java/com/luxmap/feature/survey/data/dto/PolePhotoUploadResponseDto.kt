package com.luxmap.feature.survey.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PolePhotoUploadResponseDto(
    @SerialName("photo_id") val photoId: String,
)
