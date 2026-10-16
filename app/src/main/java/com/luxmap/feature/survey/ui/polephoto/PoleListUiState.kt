package com.luxmap.feature.survey.ui.polephoto

import com.luxmap.core.theme.SyncStatus

data class PoleListRowState(
    val poleId: String,
    val position: Int,
    val fixtureStatus: String,
    val syncStatus: SyncStatus?,
)

sealed interface PoleListUiState {
    data object Loading : PoleListUiState

    data class Success(val poles: List<PoleListRowState>) : PoleListUiState

    data object Empty : PoleListUiState

    data class Error(val message: String) : PoleListUiState
}
