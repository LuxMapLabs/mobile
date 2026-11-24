package com.luxmap.feature.survey.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateLuxReadingRequestDto(
    @SerialName("client_op_id") val clientOpId: String,
    @SerialName("pole_id") val poleId: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("lux_value") val luxValue: Double,
    @SerialName("data_source") val dataSource: String,
    @SerialName("meter_model") val meterModel: String,
    @SerialName("photo_id") val photoId: String? = null,
)

@Serializable
data class LuxReadingDto(
    @SerialName("lux_id") val luxId: String,
    @SerialName("client_op_id") val clientOpId: String,
    @SerialName("pole_id") val poleId: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("lux_value") val luxValue: Double,
    @SerialName("data_source") val dataSource: String,
    @SerialName("meter_model") val meterModel: String? = null,
    @SerialName("photo_id") val photoId: String? = null,
)
