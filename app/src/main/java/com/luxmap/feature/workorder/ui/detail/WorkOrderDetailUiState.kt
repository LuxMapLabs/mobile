package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.polegps.data.MeasurementSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.data.PoleReference

sealed interface WorkOrderDetailUiState {
    data object Loading : WorkOrderDetailUiState

    data class Success(
        val detail: WorkOrderDetail,
        val localEvidence: List<LocalWorkOrderEvidenceEntity> = emptyList(),
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
        val hasDraft: Boolean = false,
        val draftError: String? = null,
        val orderPoles: OrderPolesUiState = OrderPolesUiState.Loading,
        val poleReferences: List<PoleReference> = emptyList(),
        val poleMeasurements: List<PoleMeasurement> = emptyList(),
        val poleAttachments: List<PoleGpsAttachment> = emptyList(),
    ) : WorkOrderDetailUiState

    data object Empty : WorkOrderDetailUiState

    data class Error(val message: String, val isNetworkError: Boolean = false) : WorkOrderDetailUiState
}

fun WorkOrderDetailUiState.Success.canSubmit(): Boolean {
    val noteValid = reportNote.trim().length >= 10
    return when (detail.taskKind) {
        "repair" -> noteValid && localEvidence.any { it.kind == "after" }
        "inspection" ->
            noteValid && hasInspectionPhoto() && detail.faults.all { faultOutcomes.containsKey(it.faultId) }
        "pole_gps_update" ->
            noteValid &&
                poleMeasurements.any {
                    it.source == MeasurementSource.DEVICE
                } &&
                polesMissingPhoto().isEmpty()
        else -> false
    }
}

private val unusablePolePhotoStatuses = setOf("replaceable_error", "failed", "conflict")

fun WorkOrderDetailUiState.Success.polesMissingPhoto(): List<String> =
    poleMeasurements
        .filter { it.poleId in detail.poleIds && !hasPolePhoto(it) }
        .map { it.poleId }
        .distinct()

private fun WorkOrderDetailUiState.Success.hasPolePhoto(measurement: PoleMeasurement): Boolean {
    if (measurement.photoMissingOnServer) return false
    if (measurement.source == MeasurementSource.DEVICE && measurement.photoFilePath != null) return true
    return localEvidence.any {
        it.kind == "observation" && it.poleId == measurement.poleId &&
            it.uploadStatus !in unusablePolePhotoStatuses
    }
}

internal fun WorkOrderDetailUiState.Success.hasInspectionPhoto(): Boolean =
    localEvidence.any { it.kind == "observation" }

internal fun WorkOrderDetailUiState.Success.hasActiveLocalCompletion(): Boolean =
    localCompletion?.let { it.submitStatus != "draft" && !isReturnedForRework(this) } == true

fun WorkOrderDetailUiState.Success.syncStatus(): SyncStatus? =
    when (localCompletion?.submitStatus) {
        null -> null
        "pending" -> if (isOnline) SyncStatus.QUEUED_ONLINE else SyncStatus.QUEUED_OFFLINE
        "synced" -> SyncStatus.DONE
        "failed" -> SyncStatus.FAILED
        "conflict" -> SyncStatus.CONFLICT
        else -> null
    }
