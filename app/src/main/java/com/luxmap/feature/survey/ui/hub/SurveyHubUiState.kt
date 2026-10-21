package com.luxmap.feature.survey.ui.hub

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.AssignedSurveyRoute

data class LocalRejectedSweep(
    val surveySweepId: String,
    val rejectionReason: String,
    val rejectedByName: String,
)

data class LocalPendingAiConfirmation(
    val surveySweepId: String,
    val normalCount: Int,
    val dimCount: Int,
    val outCount: Int,
)

data class UploadingSurveySession(
    val sessionId: String,
    val surveySweepId: String,
    val syncStatus: SyncStatus,
)

sealed interface SurveyHubUiState {
    data object Loading : SurveyHubUiState

    data class Success(
        val assignedRoutes: List<AssignedSurveyRoute>,
        val uploadingSessions: List<UploadingSurveySession> = emptyList(),
        val rejectedSweeps: List<LocalRejectedSweep> = emptyList(),
        val pendingAiConfirmation: List<LocalPendingAiConfirmation> = emptyList(),
        // True when assignedRoutes came from the local cache after a failed fetch.
        val isStale: Boolean = false,
        // The route currently calling POST /work-orders/{id}/start - disables its "Bat dau
        // tuyen" button while in flight, null the rest of the time.
        val startingWorkOrderId: String? = null,
        val startError: String? = null,
    ) : SurveyHubUiState

    data object Empty : SurveyHubUiState

    data class Error(val message: String) : SurveyHubUiState
}
