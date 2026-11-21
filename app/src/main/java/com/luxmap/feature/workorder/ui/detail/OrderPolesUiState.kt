package com.luxmap.feature.workorder.ui.detail

import com.luxmap.feature.survey.data.PoleListItem

sealed interface OrderPolesUiState {
    data object Loading : OrderPolesUiState

    data class Success(val poles: List<PoleListItem>, val isStale: Boolean) : OrderPolesUiState

    data object Empty : OrderPolesUiState

    data class Error(val message: String) : OrderPolesUiState
}
