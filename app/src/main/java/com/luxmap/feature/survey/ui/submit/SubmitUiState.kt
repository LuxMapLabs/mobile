package com.luxmap.feature.survey.ui.submit

sealed interface SubmitUiState {
    data object Idle : SubmitUiState

    data class Uploading(val bytesSent: Long, val totalBytes: Long, val isOffline: Boolean = false) : SubmitUiState

    data object Done : SubmitUiState

    data class Failed(val message: String) : SubmitUiState

    data class Conflict(val message: String) : SubmitUiState

    data object NotPackagedYet : SubmitUiState
}
