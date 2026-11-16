package com.luxmap.feature.home.ui

import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.workorder.data.CompletionSyncState
import java.time.Instant

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val data: HomeData,
        val isStale: Boolean = false,
        val lastSyncedAt: Instant? = null,
        val completionSyncStates: Map<String, CompletionSyncState> = emptyMap(),
    ) : HomeUiState

    data object Empty : HomeUiState

    data class Error(val message: String, val isNetworkError: Boolean = false) : HomeUiState
}
