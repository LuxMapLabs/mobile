package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.GisMapDataset

// Đủ 4 trạng thái bắt buộc theo CLAUDE.md (đang tải / có dữ liệu / rỗng / lỗi).
sealed interface MapUiState {
    data object Loading : MapUiState

    data class Success(
        // Full dataset — source for the KPI chip counts (FM-36), which always show the true
        // total regardless of the active status filter, and for search (search must still find
        // a pole the current filter is hiding).
        val dataset: GisMapDataset,
        // Subset after the status filter (FM-36 note: "Lọc tại ViewModel, Map chỉ nhận danh sách
        // đã lọc") — this is what the map actually renders.
        val filteredDataset: GisMapDataset,
        // Empty chỉ dựa vào dataset.poles, xem MapViewModel — segments không có route riêng nên
        // không tính vào điều kiện Empty.
        val isStale: Boolean = false,
    ) : MapUiState

    data object Empty : MapUiState

    data class Error(val message: String) : MapUiState
}
