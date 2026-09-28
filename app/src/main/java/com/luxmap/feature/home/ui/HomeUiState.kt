package com.luxmap.feature.home.ui

import com.luxmap.feature.home.data.HomeData
import java.time.Instant

// Đủ 4 trạng thái bắt buộc theo CLAUDE.md (đang tải / có dữ liệu / rỗng / lỗi).
sealed interface HomeUiState {
    data object Loading : HomeUiState

    // lastSyncedAt là Instant thật (không phải field JSON) — do ViewModel tự gán lúc lấy dữ liệu
    // thành công, không parse từ server, nên khác quy ước String ở WorkOrderSummaryItem.slaDueAt.
    data class Success(
        val data: HomeData,
        val isStale: Boolean = false,
        val lastSyncedAt: Instant? = null,
    ) : HomeUiState

    data object Empty : HomeUiState

    data class Error(val message: String) : HomeUiState
}
