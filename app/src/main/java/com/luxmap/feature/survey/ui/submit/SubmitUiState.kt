package com.luxmap.feature.survey.ui.submit

sealed interface SubmitUiState {
    data object Idle : SubmitUiState

    data class Uploading(val bytesSent: Long, val totalBytes: Long) : SubmitUiState

    data object Done : SubmitUiState

    data class Error(val message: String) : SubmitUiState
}
