package com.luxmap.feature.map.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CabinetFeatureCollectionDto(
    val type: String,
    val features: List<CabinetFeatureDto>,
)

@Serializable
data class CabinetFeatureDto(
    val type: String,
    val geometry: PoleGeometryDto,
    val properties: CabinetPropertiesDto,
)

@Serializable
data class CabinetPropertiesDto(
    @SerialName("cabinet_id") val cabinetId: String,
    @SerialName("cabinet_name") val cabinetName: String,
    @SerialName("commune_id") val communeId: String,
    @SerialName("feeder_ids") val feederIds: List<String> = emptyList(),
    val feeders: List<CabinetFeederDto> = emptyList(),
    @SerialName("iot_node_ids") val iotNodeIds: List<String> = emptyList(),
)

@Serializable
data class CabinetFeederDto(
    @SerialName("feeder_id") val feederId: String,
    @SerialName("relay_no") val relayNo: Int? = null,
    @SerialName("control_mode") val controlMode: String? = null,
    @SerialName("mode_reported_at") val modeReportedAt: String? = null,
    @SerialName("power_state") val powerState: String? = null,
)
