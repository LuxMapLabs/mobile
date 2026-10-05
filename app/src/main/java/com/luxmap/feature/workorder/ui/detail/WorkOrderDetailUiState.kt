package com.luxmap.feature.workorder.ui.detail

import com.luxmap.feature.workorder.data.WorkOrderDetail

sealed interface WorkOrderDetailUiState {
    data object Loading : WorkOrderDetailUiState

    data class Success(
        val detail: WorkOrderDetail,
        val isStarting: Boolean = false,
        val startError: String? = null,
    ) : WorkOrderDetailUiState

    data object Empty : WorkOrderDetailUiState

    data class Error(val message: String) : WorkOrderDetailUiState
}
