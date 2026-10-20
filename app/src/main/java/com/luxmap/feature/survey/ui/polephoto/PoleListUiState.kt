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
        // Local file path (LocalPolePhotoEntity.filePath) - the photo was captured on this
        // device, so it's shown straight from disk, no network/auth needed.
        val filePath: String,
        // Null means the AI model itself failed to run (ai_status = "unavailable") - the Field
        // Engineer can still review/classify manually in that case (business rule D-2, ticket
        // PHOTO-REVIEW), so this is no longer routed to the Empty state below.
        val cvState: String?,
        val confidence: Double?,
        val modelVersion: String?,
        val analyzedAt: Instant?,
        // The Field Engineer's correction/agreement on cvState (ticket PHOTO-REVIEW, 2026-10-20).
        // Null until reviewed - never defaults to cvState. See contract-drift.md "PATCH .../review".
        val finalCvState: String? = null,
        val reviewNote: String? = null,
        val reviewedBy: String? = null,
        val reviewedAt: Instant? = null,
        val decision: String?,
        val decisionNote: String?,
        val decidedBy: String?,
        val decidedAt: Instant?,
        // True while showing a cached Room value and a network refresh hasn't confirmed it yet
        // (offline-first: shown immediately instead of a Loading spinner when a cache exists).
        val isStale: Boolean = false,
        // True while a review submission is in flight - disables the submit button so a second
        // tap can't fire a duplicate PATCH before the first one's response comes back.
        val isSubmittingReview: Boolean = false,
        // Set when a review submission fails (e.g. 409 POLE_PHOTO_ALREADY_DECIDED), cleared on
        // the next successful fetch/submit - shown inline under the review editor.
        val reviewError: String? = null,
    ) : PolePhotoResultUiState

    // Defensive-only fallback, kept for the mandatory 4-state UiState pattern (CLAUDE.md) - the
    // server contract guarantees ai_status is always "available" or "unavailable" once fetched
    // (never a literal null), so PoleListViewModel.toResultUiState never actually produces this
    // for a real response. ai_status = "unavailable" now maps into Success (cvState = null)
    // instead, since the Field Engineer must still be able to review it (business rule D-2).
    data object Empty : PolePhotoResultUiState

    data class Error(val message: String) : PolePhotoResultUiState
}
