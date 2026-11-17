package com.luxmap.feature.map.data

import com.luxmap.feature.map.data.dto.CabinetFeatureDto
import com.luxmap.feature.map.data.dto.CabinetFeederDto

enum class FeederMode { ON, OFF, AUTO, UNKNOWN }

enum class FeederPower { ON, OFF, UNKNOWN }

data class CabinetFeeder(
    val feederId: String,
    val relayNo: Int?,
    val mode: FeederMode,
    val power: FeederPower,
)

data class CabinetMarker(
    val cabinetId: String,
    val name: String,
    val communeId: String,
    val lat: Double,
    val lng: Double,
    val feeders: List<CabinetFeeder>,
    val iotNodeCount: Int,
) {
    val hasPowerOff: Boolean get() = feeders.any { it.power == FeederPower.OFF }
}

fun CabinetFeatureDto.toCabinetMarker(): CabinetMarker =
    CabinetMarker(
        cabinetId = properties.cabinetId,
        name = properties.cabinetName,
        communeId = properties.communeId,
        lat = geometry.coordinates[1],
        lng = geometry.coordinates[0],
        feeders = properties.feeders.map { it.toCabinetFeeder() },
        iotNodeCount = properties.iotNodeIds.size,
    )

private fun CabinetFeederDto.toCabinetFeeder(): CabinetFeeder =
    CabinetFeeder(
        feederId = feederId,
        relayNo = relayNo,
        mode =
            when (controlMode) {
                "on" -> FeederMode.ON
                "off" -> FeederMode.OFF
                "auto" -> FeederMode.AUTO
                else -> FeederMode.UNKNOWN
            },
        power =
            when (powerState) {
                "on" -> FeederPower.ON
                "off" -> FeederPower.OFF
                else -> FeederPower.UNKNOWN
            },
    )
