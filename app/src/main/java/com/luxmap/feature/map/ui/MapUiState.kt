package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.PoleMarker

// Đủ 4 trạng thái bắt buộc theo CLAUDE.md (đang tải / có dữ liệu / rỗng / lỗi).
sealed interface MapUiState {
    data object Loading : MapUiState

    data class Success(
        val poles: List<PoleMarker>,
        val isStale: Boolean = false,
    ) : MapUiState

    data object Empty : MapUiState

    data class Error(val message: String) : MapUiState
}
