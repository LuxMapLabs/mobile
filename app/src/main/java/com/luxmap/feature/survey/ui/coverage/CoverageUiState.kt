package com.luxmap.feature.survey.ui.coverage

// 4 required states (CLAUDE.md architecture section) for the F05-reinterpreted video review
// screen. playerErrorMessage on Success is an inline warning flag, not a 5th state - same
// pattern CaptureUiState.Recording uses for gpsSignalLost/bleGapDetected.
sealed interface CoverageUiState {
    data object Loading : CoverageUiState

    data class Success(
        val segmentFilePaths: List<String>,
        val playerErrorMessage: String? = null,
    ) : CoverageUiState

    data object Empty : CoverageUiState

    data class Error(val message: String) : CoverageUiState
}
