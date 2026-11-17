package com.luxmap.feature.map.data

import com.luxmap.feature.map.data.dto.CabinetTopologyDto

data class TopologyEdge(
    val feederId: String,
    val toPoleId: String,
    val coordinates: List<Pair<Double, Double>>,
)

data class CabinetTopology(
    val edges: List<TopologyEdge>,
    val unlocatedPoleCount: Int,
    val unroutedPoleCount: Int,
) {
    val poleIds: Set<String> get() = edges.mapTo(mutableSetOf()) { it.toPoleId }
}

fun CabinetTopologyDto.toCabinetTopology(): CabinetTopology =
    CabinetTopology(
        edges =
            features.map { feature ->
                TopologyEdge(
                    feederId = feature.properties.feederId,
                    toPoleId = feature.properties.toPoleId,
                    coordinates = feature.geometry.coordinates.map { it[0] to it[1] },
                )
            },
        unlocatedPoleCount = unlocatedPoleCount,
        unroutedPoleCount = unroutedPoleCount,
    )
