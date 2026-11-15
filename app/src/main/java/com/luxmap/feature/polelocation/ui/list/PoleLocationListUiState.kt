package com.luxmap.feature.polelocation.ui.list

import com.luxmap.feature.polelocation.data.PoleMissingLocationItem

sealed interface PoleLocationListUiState {
    data object Loading : PoleLocationListUiState

    data class Success(val items: List<PoleMissingLocationItem>) : PoleLocationListUiState

    data object Empty : PoleLocationListUiState

    data class Error(val message: String) : PoleLocationListUiState
}
