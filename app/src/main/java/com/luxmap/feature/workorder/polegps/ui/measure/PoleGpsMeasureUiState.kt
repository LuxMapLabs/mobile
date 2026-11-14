package com.luxmap.feature.workorder.polegps.ui.measure

import com.luxmap.feature.workorder.polegps.data.PoleReference

sealed interface PoleGpsMeasureUiState {
    data object Loading : PoleGpsMeasureUiState

    data class Success(
        val poleLabel: String,
        val previous: PoleReference?,
    ) : PoleGpsMeasureUiState

    data object Empty : PoleGpsMeasureUiState

    data class Error(
        val message: String,
    ) : PoleGpsMeasureUiState
}
