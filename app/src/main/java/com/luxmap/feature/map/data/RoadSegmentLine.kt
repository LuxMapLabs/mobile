package com.luxmap.feature.map.data

import com.luxmap.feature.map.data.dto.RoadSegmentFeatureDto

// Domain model rút gọn từ RoadSegmentFeatureDto, chỉ giữ field UI cần cho F12 (vẽ đường "tuyến
// đã khảo sát" trên bản đồ) — KHÔNG giữ controllerNodeId/hasActiveSegmentFault, hai field đó
// phục vụ phân tích điện lưới ở Web GIS (vai trò Kỹ sư bảo trì), không thuộc phạm vi F12 mobile.
data class RoadSegmentLine(
    val segmentId: String,
    val name: String,
    // [lng, lat] theo từng điểm, đúng thứ tự GeoJSON
    val coordinates: List<Pair<Double, Double>>,
)

fun RoadSegmentFeatureDto.toRoadSegmentLine(): RoadSegmentLine =
    RoadSegmentLine(
        segmentId = properties.segmentId,
        name = properties.segmentName,
        coordinates = geometry.coordinates.map { (lng, lat) -> lng to lat },
    )
