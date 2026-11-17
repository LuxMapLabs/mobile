package com.luxmap.feature.map.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommuneFeatureCollectionDto(
    val features: List<CommuneFeatureDto> = emptyList(),
)

@Serializable
data class CommuneFeatureDto(
    val geometry: MultiPolygonGeometryDto,
    val properties: CommunePropertiesDto,
)

@Serializable
data class MultiPolygonGeometryDto(
    val coordinates: List<List<List<List<Double>>>>,
)

@Serializable
data class CommunePropertiesDto(
    @SerialName("commune_id") val communeId: String,
    val name: String,
)
