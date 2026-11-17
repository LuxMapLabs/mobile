package com.luxmap.feature.map.data

import com.luxmap.feature.map.data.dto.CommuneFeatureDto

data class CommuneOutline(
    val communeId: String,
    val name: String,
    val polygons: List<List<List<Pair<Double, Double>>>>,
)

fun CommuneFeatureDto.toCommuneOutline(): CommuneOutline =
    CommuneOutline(
        communeId = properties.communeId,
        name = properties.name,
        polygons = geometry.coordinates.map { polygon -> polygon.map { ring -> ring.map { it[0] to it[1] } } },
    )
