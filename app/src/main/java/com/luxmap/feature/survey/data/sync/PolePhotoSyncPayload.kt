package com.luxmap.feature.survey.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PolePhotoSyncPayload(
    @SerialName("photo_id") val photoId: String,
)
