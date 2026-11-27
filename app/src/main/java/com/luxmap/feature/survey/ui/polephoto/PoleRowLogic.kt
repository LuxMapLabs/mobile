package com.luxmap.feature.survey.ui.polephoto

import com.luxmap.core.theme.SyncStatus

enum class PoleProgress {
    SYNC_FAILED,
    SYNC_CONFLICT,
    SYNCING,
    QUEUED_OFFLINE,
    QUEUED_ONLINE,
    REVIEW_SAVED,
    AWAITING_DECISION,
    UPLOADED,
}

sealed interface PoleRowAction {
    data class OpenAiResult(val serverPhotoId: String) : PoleRowAction

    data object OpenReportSheet : PoleRowAction

    data object None : PoleRowAction
}

fun poleProgressBadge(row: PoleListRowState): PoleProgress? =
    when (row.syncStatus) {
        null -> null
        SyncStatus.FAILED -> PoleProgress.SYNC_FAILED
        SyncStatus.CONFLICT -> PoleProgress.SYNC_CONFLICT
        SyncStatus.SYNCING -> PoleProgress.SYNCING
        SyncStatus.QUEUED_OFFLINE -> PoleProgress.QUEUED_OFFLINE
        SyncStatus.QUEUED_ONLINE -> PoleProgress.QUEUED_ONLINE
        SyncStatus.DONE ->
            when {
                row.hasPendingReview -> PoleProgress.REVIEW_SAVED
                row.hasPendingDecision -> PoleProgress.AWAITING_DECISION
                else -> PoleProgress.UPLOADED
            }
    }

fun poleRowAction(
    row: PoleListRowState,
    isCompleted: Boolean,
): PoleRowAction {
    val photoId = row.serverPhotoId
    return when {
        // A photo the engineer labelled at capture is not reviewed against the AI per pole; the
        // AI only shows up on the end-of-route check. Videos keep the AI result review.
        row.observation == null && row.syncStatus == SyncStatus.DONE && photoId != null ->
            PoleRowAction.OpenAiResult(photoId)
        isCompleted -> PoleRowAction.None
        else -> PoleRowAction.OpenReportSheet
    }
}

enum class PoleAiVerdict { ON, OFF, INCONCLUSIVE }

fun poleAiVerdict(
    aiStatus: String?,
    cvState: String?,
    finalCvState: String?,
    draftFinalCvState: String?,
): PoleAiVerdict? {
    if (aiStatus == null) return null
    return when (draftFinalCvState ?: finalCvState ?: cvState) {
        "on" -> PoleAiVerdict.ON
        "off" -> PoleAiVerdict.OFF
        else -> PoleAiVerdict.INCONCLUSIVE
    }
}
