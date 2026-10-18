package com.luxmap.feature.survey.ui.polephoto

import com.luxmap.core.theme.SyncStatus
import java.time.Instant

data class PoleListRowState(
    val poleId: String,
    val position: Int,
    val fixtureStatus: String,
    // The real backend photo_id, only known once UploadPolePhotoSyncHandler captures it from
    // the upload response - not the local row's own id. Null until then.
    val serverPhotoId: String?,
    val syncStatus: SyncStatus?,
)

sealed interface PoleListUiState {
    data object Loading : PoleListUiState

    data class Success(
        val poles: List<PoleListRowState>,
        // True when showing the cached pole list because the live fetch just failed.
        val isStale: Boolean = false,
    ) : PoleListUiState

    data object Empty : PoleListUiState

    data class Error(val message: String) : PoleListUiState
}

// State for the on-demand "Xem kết quả AI" bottom sheet - separate from PoleListUiState
// because it covers a single photo, not the whole list.
sealed interface PolePhotoResultUiState {
    data object Loading : PolePhotoResultUiState

    data class Success(
        val cvState: String,
        val confidence: Double?,
        val modelVersion: String?,
        val analyzedAt: Instant?,
        val decision: String?,
        val decisionNote: String?,
        val decidedBy: String?,
        val decidedAt: Instant?,
        // True while showing a cached Room value and a network refresh hasn't confirmed it yet
        // (offline-first: shown immediately instead of a Loading spinner when a cache exists).
        val isStale: Boolean = false,
    ) : PolePhotoResultUiState

    // ai_status = "unavailable" - the AI model itself failed, not a network/fetch error.
    data object Empty : PolePhotoResultUiState

    data class Error(val message: String) : PolePhotoResultUiState
}
