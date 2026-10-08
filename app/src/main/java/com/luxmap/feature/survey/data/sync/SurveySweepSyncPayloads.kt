package com.luxmap.feature.survey.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSurveySweepPayload(
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class UploadSurveyClipPayload(
    @SerialName("session_id") val sessionId: String,
    @SerialName("clip_no") val clipNo: Int,
)

@Serializable
data class UploadSurveyRawPayload(
    @SerialName("session_id") val sessionId: String,
    val kind: String,
)

@Serializable
data class SubmitSurveySweepPayload(
    @SerialName("session_id") val sessionId: String,
)
