package com.luxmap.feature.workorder.polegps.ui.measure

import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.domain.FixQuality

sealed interface PoleGpsMeasureUiState {
    data object Loading : PoleGpsMeasureUiState

    data class Success(
        val poleLabel: String,
        val previous: PoleReference?,
        val liveFix: GpsFix?,
        val quality: FixQuality,
        val canSave: Boolean,
        val saved: Boolean,
        val stored: PoleMeasurement?,
    ) : PoleGpsMeasureUiState

    data object Empty : PoleGpsMeasureUiState

    data class Error(
        val message: String,
    ) : PoleGpsMeasureUiState
}
