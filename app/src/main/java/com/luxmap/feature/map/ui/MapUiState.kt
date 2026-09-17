package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.GisMapDataset

// Đủ 4 trạng thái bắt buộc theo CLAUDE.md (đang tải / có dữ liệu / rỗng / lỗi).
sealed interface MapUiState {
    data object Loading : MapUiState

    data class Success(
        val dataset: GisMapDataset,
        // Empty chỉ dựa vào dataset.poles, xem MapViewModel — segments không có route riêng nên
        // không tính vào điều kiện Empty.
        val isStale: Boolean = false,
    ) : MapUiState

    data object Empty : MapUiState

    data class Error(val message: String) : MapUiState
}
