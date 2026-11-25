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
    @SerialName("protocol") val protocol: LuxProtocolDto? = null,
)

/** LUX-BASELINE (Contract v1.24): the stationary protocol of a reading; `lux_value` must be the median of [samples]. */
@Serializable
data class LuxProtocolDto(
    @SerialName("samples") val samples: List<Double>,
    @SerialName("sensor_height_m") val sensorHeightM: Double,
    @SerialName("module_id") val moduleId: String,
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
