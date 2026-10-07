package com.luxmap.feature.workorder.ui.completion

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity

sealed interface WorkOrderCompletionUiState {
    data object Loading : WorkOrderCompletionUiState

    data class Success(
        val detail: WorkOrderDetail,
        val localEvidence: LocalWorkOrderEvidenceEntity?,
        val localCompletion: LocalWorkOrderCompletionEntity?,
        val isOnline: Boolean,
        val reportNote: String,
        val materialsUsed: String,
        val faultOutcomes: Map<String, String>,
        val isSubmitting: Boolean = false,
        val submitError: String? = null,
    ) : WorkOrderCompletionUiState

    data object Empty : WorkOrderCompletionUiState

    data class Error(val message: String) : WorkOrderCompletionUiState
}

fun WorkOrderCompletionUiState.Success.canSubmit(): Boolean {
    val noteValid = reportNote.trim().length >= 10
    return when (detail.taskKind) {
        "repair" -> noteValid && localEvidence != null
        "inspection" -> noteValid && detail.faults.all { faultOutcomes.containsKey(it.faultId) }
        else -> false
    }
}

fun WorkOrderCompletionUiState.Success.syncStatus(): SyncStatus? =
    when (localCompletion?.submitStatus) {
        null -> null
        "pending" -> if (isOnline) SyncStatus.QUEUED_ONLINE else SyncStatus.QUEUED_OFFLINE
        "synced" -> SyncStatus.DONE
        "failed" -> SyncStatus.FAILED
        "conflict" -> SyncStatus.CONFLICT
        else -> null
    }
