package com.luxmap.feature.map.data

import kotlinx.coroutines.flow.Flow

// Chữ ký để trống chỗ cho tham số bbox khi FM-15 nối GET /api/v1/poles?bbox= thật —
// không thêm tham số thừa ở bước prototype này (xem plan FM-06).
interface MapRepository {
    fun observePoles(): Flow<List<PoleMarker>>

    // Lớp "tuyến đã khảo sát" (RoadSegment) của F12 — tách riêng khỏi observePoles() vì
    // là 2 nguồn dữ liệu độc lập, mỗi method 1 trách nhiệm.
    fun observeRoadSegments(): Flow<List<RoadSegmentLine>>
}
