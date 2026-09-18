package com.luxmap.feature.map.data

import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.dto.PoleFeatureDto

// Domain model rút gọn từ PoleFeatureDto, chỉ giữ field UI cần cho FM-06 (marker + bottom
// sheet xem nhanh) và FM-36 (segmentId, để biết cột này thuộc tuyến nào cho bottom sheet tóm
// tắt tuyến). Dùng thẳng AssetCondition có sẵn từ FM-02 (core/theme/Color.kt) thay vì tự tạo
// enum trạng thái mới — LuxMap chỉ có một bộ enum tình trạng tài sản dùng chung.
data class PoleMarker(
    val poleId: String,
    val segmentId: String,
    val lat: Double,
    val lng: Double,
    val fixtureStatus: AssetCondition,
    val powerSource: String,
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
