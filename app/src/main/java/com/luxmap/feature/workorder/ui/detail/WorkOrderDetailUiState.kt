package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity

sealed interface WorkOrderDetailUiState {
    data object Loading : WorkOrderDetailUiState

    data class Success(
        val detail: WorkOrderDetail,
        val localEvidence: LocalWorkOrderEvidenceEntity? = null,
        val localCompletion: LocalWorkOrderCompletionEntity? = null,
        val isOnline: Boolean = true,
        val reportNote: String = detail.reportNote.orEmpty(),
        val materialsUsed: String = detail.materialsUsed.orEmpty(),
        val faultOutcomes: Map<String, String> = emptyMap(),
        val isStarting: Boolean = false,
        val startError: String? = null,
        val isSubmitting: Boolean = false,
        val submitError: String? = null,
        val isRequestingCancellation: Boolean = false,
        val cancellationError: String? = null,
        val justSubmitted: Boolean = false,
    ) : WorkOrderDetailUiState

    data object Empty : WorkOrderDetailUiState

    data class Error(val message: String) : WorkOrderDetailUiState
}

fun WorkOrderDetailUiState.Success.canSubmit(): Boolean {
    val noteValid = reportNote.trim().length >= 10
    return when (detail.taskKind) {
        "repair" -> noteValid && localEvidence != null
        "inspection" -> noteValid && detail.faults.all { faultOutcomes.containsKey(it.faultId) }
        else -> false
    }
}

fun WorkOrderDetailUiState.Success.syncStatus(): SyncStatus? =
    when (localCompletion?.submitStatus) {
        null -> null
        "pending" -> if (isOnline) SyncStatus.QUEUED_ONLINE else SyncStatus.QUEUED_OFFLINE
        "synced" -> SyncStatus.DONE
        "failed" -> SyncStatus.FAILED
        "conflict" -> SyncStatus.CONFLICT
        else -> null
    }
