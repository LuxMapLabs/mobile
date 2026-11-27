package com.luxmap.feature.survey.ui.polephoto

import com.luxmap.core.theme.SyncStatus
import java.time.Instant

fun poleNumberLabel(position: Int?): String = position?.let { "Cột $it" } ?: "Chưa có GPS"

data class PoleLuxInfo(
    val luxValue: Double,
    val isSyncFailed: Boolean,
)

data class PoleListRowState(
    val poleId: String,
    val position: Int?,
    val fixtureStatus: String,
    // The real backend photo_id, only known once UploadPolePhotoSyncHandler captures it from
    // the upload response - not the local row's own id. Null until then.
    val serverPhotoId: String?,
    val syncStatus: SyncStatus?,
    // True when this pole's latest photo has a draft review not yet sent (Task 3's
    // enqueuePendingReviews is what sends it, on "Hoàn tất").
    val hasPendingReview: Boolean = false,
    val hasPendingDecision: Boolean = false,
    val aiVerdict: PoleAiVerdict? = null,
    val isConfirmed: Boolean = false,
    val identification: com.luxmap.feature.survey.data.PoleListItem? = null,
    val lux: PoleLuxInfo? = null,
    // The engineer's own label picked at capture time (latest photo), or null for videos and
    // photos taken before this existed - those still go through the AI result review.
    val observation: Observation? = null,
    val skipReason: String? = null,
    val filePath: String? = null,
    val aiStatus: String? = null,
    val cvState: String? = null,
    val confidence: Double? = null,
) {
    // Every route pole needs an answer before "Hoàn tất": a labelled photo, a skip reason, or a
    // confirmed AI result for a video.
    val isCovered: Boolean get() = skipReason != null || observation != null || isConfirmed
}

// A pole on the end-of-route check: the AI either read the photo differently from the engineer
// or could not decide. Never changes the engineer's label by itself.
data class AiMismatch(
    val poleId: String,
    val position: Int?,
    val filePath: String,
    val observation: Observation,
    val cvState: String?,
    val confidence: Double?,
)

fun PoleListRowState.aiMismatch(): AiMismatch? {
    val seen = observation ?: return null
    val path = filePath ?: return null
    if (aiStatus == null || (isConclusive(cvState) && cvState == seen.wireValue)) return null
    return AiMismatch(poleId, position, path, seen, cvState, confidence)
}

sealed interface PoleListUiState {
    data object Loading : PoleListUiState

    data class Success(
        val poles: List<PoleListRowState>,
        // True when showing the cached pole list because the live fetch just failed.
        val isStale: Boolean = false,
        // True once a local_work_order_completion row exists for this work order (regardless of
        // its own submitStatus - sync happens in the background). Locks "Hoan tat" and reporting
        // new poles, same as the mock's state.done - found missing on a real device, 2026-10-29:
        // tapping "Hoan tat" gave no feedback at all and the screen looked completely unchanged.
        val isCompleted: Boolean = false,
    ) : PoleListUiState {
        val remainingCount: Int get() = poles.count { !it.isCovered }
        val firstRemainingPoleId: String? get() = poles.firstOrNull { !it.isCovered }?.poleId
        val canComplete: Boolean get() = !isCompleted && poles.isNotEmpty() && remainingCount == 0
        val aiMismatches: List<AiMismatch> get() = poles.mapNotNull { it.aiMismatch() }

        // Labelled photos the AI has not read yet (still queued, or offline).
        val awaitingAiCount: Int get() = poles.count { it.observation != null && it.aiStatus == null }
    }

    data object Empty : PoleListUiState

    data class Error(val message: String, val isNetworkError: Boolean = false) : PoleListUiState
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
        // The Field Engineer's not-yet-sent draft correction (ReviewCard's local save) - distinct
        // from finalCvState/reviewNote above, which only hold a server-confirmed review. Null
        // once sent (SubmitPolePhotoReviewSyncHandler clears it) or if never reviewed.
        val draftFinalCvState: String? = null,
        val draftReviewNote: String? = null,
        val decision: String?,
        val decisionNote: String?,
        val decidedBy: String?,
        val decidedAt: Instant?,
        // True while showing a cached Room value and a network refresh hasn't confirmed it yet
        // (offline-first: shown immediately instead of a Loading spinner when a cache exists).
        val isStale: Boolean = false,
        val mediaType: String = "photo",
        val videoUrl: String? = null,
        val luxValue: Double? = null,
    ) : PolePhotoResultUiState

    // Defensive-only fallback, kept for the mandatory 4-state UiState pattern (CLAUDE.md) - the
    // server contract guarantees ai_status is always "available" or "unavailable" once fetched
    // (never a literal null), so PoleListViewModel.toResultUiState never actually produces this
    // for a real response. ai_status = "unavailable" now maps into Success (cvState = null)
    // instead, since the Field Engineer must still be able to review it (business rule D-2).
    data object Empty : PolePhotoResultUiState

    data class Error(val message: String, val isNetworkError: Boolean = false) : PolePhotoResultUiState
}
