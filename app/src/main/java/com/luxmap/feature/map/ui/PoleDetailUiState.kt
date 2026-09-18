package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.PoleDetail

// Đủ 4 trạng thái bắt buộc theo CLAUDE.md (đang tải / có dữ liệu / rỗng / lỗi).
sealed interface PoleDetailUiState {
    data object Loading : PoleDetailUiState

    data class Success(
        val detail: PoleDetail,
        val isStale: Boolean = false,
    ) : PoleDetailUiState

    // Backend trả 404 khi pole_id không tồn tại/ngoài phạm vi (Contract v1.1) — khác Error vì
    // đây không phải lỗi mạng, cần thông báo riêng.
    data object Empty : PoleDetailUiState

    data class Error(val message: String) : PoleDetailUiState
}
