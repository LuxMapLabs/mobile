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

// An inspection order being completed moves its submit CTA out of the scrolling content into a
// bottom-pinned bar (so the per-fault outcome progress stays visible next to the button) - repair
// and survey orders keep their CTA inline, unaffected.
fun WorkOrderDetailUiState.Success.showsInspectionBottomBar(): Boolean =
    detail.taskKind == "inspection" && "complete" in detail.allowedActions

fun WorkOrderDetailUiState.Success.syncStatus(): SyncStatus? =
    when (localCompletion?.submitStatus) {
        null -> null
        "pending" -> if (isOnline) SyncStatus.QUEUED_ONLINE else SyncStatus.QUEUED_OFFLINE
        "synced" -> SyncStatus.DONE
        "failed" -> SyncStatus.FAILED
        "conflict" -> SyncStatus.CONFLICT
        else -> null
    }
