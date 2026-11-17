package com.luxmap.feature.map.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CabinetTopologyDto(
    @SerialName("unlocated_pole_count") val unlocatedPoleCount: Int = 0,
    @SerialName("unrouted_pole_count") val unroutedPoleCount: Int = 0,
    val features: List<TopologyEdgeFeatureDto> = emptyList(),
)

@Serializable
data class TopologyEdgeFeatureDto(
    val geometry: LineGeometryDto,
    val properties: TopologyEdgePropertiesDto,
)

@Serializable
data class LineGeometryDto(
    val coordinates: List<List<Double>>,
)

@Serializable
data class TopologyEdgePropertiesDto(
    @SerialName("feeder_id") val feederId: String,
    @SerialName("to_pole_id") val toPoleId: String,
)
