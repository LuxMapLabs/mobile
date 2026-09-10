package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.PoleMarker
import com.luxmap.feature.map.data.RoadSegmentLine

// Đủ 4 trạng thái bắt buộc theo CLAUDE.md (đang tải / có dữ liệu / rỗng / lỗi).
sealed interface MapUiState {
    data object Loading : MapUiState

    data class Success(
        val poles: List<PoleMarker>,
        // Lớp "tuyến đã khảo sát" (F12) — lớp phụ, không tính vào điều kiện Empty
        // (Empty chỉ dựa vào poles, xem MapViewModel).
        val roadSegments: List<RoadSegmentLine> = emptyList(),
        val isStale: Boolean = false,
    ) : MapUiState

    data object Empty : MapUiState

    data class Error(val message: String) : MapUiState
}
