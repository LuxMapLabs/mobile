package com.luxmap.feature.map.data

import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.dto.PoleFeatureDto

// Domain model rút gọn từ PoleFeatureDto, chỉ giữ field UI cần cho FM-06 (marker + bottom
// sheet xem nhanh) và FM-36 (segmentId, để biết cột này thuộc tuyến nào cho bottom sheet tóm
// tắt tuyến). segmentId/powerSource are nullable — a pole can have no segment or no lamp
// installed yet, matching the real backend (not every pole in a fake dataset has one).
data class PoleMarker(
    val poleId: String,
    val segmentId: String?,
    val lat: Double,
    val lng: Double,
    val fixtureStatus: AssetCondition,
    val powerSource: String?,
    val hasIotNode: Boolean,
    val nearSensitivePoi: Boolean,
)

fun PoleFeatureDto.toPoleMarker(): PoleMarker {
    val (lng, lat) = geometry.coordinates
    return PoleMarker(
        poleId = properties.poleId,
        segmentId = properties.segmentId,
        lat = lat,
        lng = lng,
        fixtureStatus = properties.fixtureStatus.toAssetCondition(),
        powerSource = properties.powerSource,
        hasIotNode = properties.hasIotNode,
        nearSensitivePoi = properties.nearSensitivePoi,
    )
}

private fun String.toAssetCondition(): AssetCondition =
    when (this) {
        "normal" -> AssetCondition.NORMAL
        "dim" -> AssetCondition.DIM
        "out" -> AssetCondition.OUT
        else -> AssetCondition.UNKNOWN
    }
