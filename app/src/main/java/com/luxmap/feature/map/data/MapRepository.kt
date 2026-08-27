package com.luxmap.feature.map.data

import kotlinx.coroutines.flow.Flow

// Chữ ký để trống chỗ cho tham số bbox khi FM-15 nối GET /api/v1/poles?bbox= thật —
// không thêm tham số thừa ở bước prototype này (xem plan FM-06).
interface MapRepository {
    fun observePoles(): Flow<List<PoleMarker>>
}
