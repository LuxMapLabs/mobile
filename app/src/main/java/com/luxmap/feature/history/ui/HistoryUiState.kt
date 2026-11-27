package com.luxmap.feature.history.ui

import com.luxmap.feature.history.data.HistoryItem
import java.time.Instant

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Success(
        val items: List<HistoryItem>,
        val isStale: Boolean = false,
        val lastSyncedAt: Instant? = null,
    ) : HistoryUiState

    data object Empty : HistoryUiState

    data class Error(val message: String, val isNetworkError: Boolean = false) : HistoryUiState
}
