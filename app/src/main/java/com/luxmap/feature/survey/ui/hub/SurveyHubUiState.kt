package com.luxmap.feature.survey.ui.hub

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.AssignedSurveyRoute

// Local-only placeholder models, not real backend entities - the "Bị từ chối"/"Chờ xác nhận AI"
// cards have no confirmed sweep-status schema yet (see docs/contract-drift.md and the redesign
// roadmap's decision #2). Rewire to real entities once WP2/WP5 confirm the schema.
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
    ) : SurveyHubUiState

    data object Empty : SurveyHubUiState

    data class Error(val message: String) : SurveyHubUiState
}
