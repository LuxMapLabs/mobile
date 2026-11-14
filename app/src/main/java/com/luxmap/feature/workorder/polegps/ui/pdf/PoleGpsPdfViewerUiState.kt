package com.luxmap.feature.workorder.polegps.ui.pdf

sealed interface PoleGpsPdfViewerUiState {
    data object Loading : PoleGpsPdfViewerUiState

    data class Ready(
        val fileName: String,
        val filePath: String,
    ) : PoleGpsPdfViewerUiState

    data object Empty : PoleGpsPdfViewerUiState

    data class Error(
        val message: String,
        val canRetry: Boolean,
    ) : PoleGpsPdfViewerUiState
}
