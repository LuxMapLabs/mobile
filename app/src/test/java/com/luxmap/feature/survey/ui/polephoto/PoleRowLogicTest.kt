package com.luxmap.feature.survey.ui.polephoto

import com.luxmap.core.theme.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoleRowLogicTest {
    private fun row(
        sync: SyncStatus? = null,
        photoId: String? = null,
        review: Boolean = false,
        decision: Boolean = false,
    ) = PoleListRowState(
        poleId = "P-1",
        position = 1,
        fixtureStatus = "normal",
        serverPhotoId = photoId,
        syncStatus = sync,
        hasPendingReview = review,
        hasPendingDecision = decision,
    )

    @Test
    fun `not captured has no progress badge`() {
        assertNull(poleProgressBadge(row()))
    }

    @Test
    fun `sync problems win over review and decision`() {
        assertEquals(
            PoleProgress.SYNC_FAILED,
            poleProgressBadge(row(SyncStatus.FAILED, review = true, decision = true)),
        )
        assertEquals(PoleProgress.SYNC_CONFLICT, poleProgressBadge(row(SyncStatus.CONFLICT, review = true)))
    }

    @Test
    fun `in-flight sync wins over review`() {
        assertEquals(PoleProgress.SYNCING, poleProgressBadge(row(SyncStatus.SYNCING, review = true)))
        assertEquals(PoleProgress.QUEUED_OFFLINE, poleProgressBadge(row(SyncStatus.QUEUED_OFFLINE)))
        assertEquals(PoleProgress.QUEUED_ONLINE, poleProgressBadge(row(SyncStatus.QUEUED_ONLINE, decision = true)))
    }

    @Test
    fun `uploaded rows show review, then decision, then uploaded`() {
        assertEquals(
            PoleProgress.REVIEW_SAVED,
            poleProgressBadge(row(SyncStatus.DONE, "PH-1", review = true, decision = true)),
        )
        assertEquals(PoleProgress.AWAITING_DECISION, poleProgressBadge(row(SyncStatus.DONE, "PH-1", decision = true)))
        assertEquals(PoleProgress.UPLOADED, poleProgressBadge(row(SyncStatus.DONE, "PH-1")))
    }

    @Test
    fun `a row with an ai result opens it even after completion`() {
        val done = row(SyncStatus.DONE, "PH-1")
        assertEquals(PoleRowAction.OpenAiResult("PH-1"), poleRowAction(done, isCompleted = false))
        assertEquals(PoleRowAction.OpenAiResult("PH-1"), poleRowAction(done, isCompleted = true))
    }

    @Test
    fun `rows without an ai result open the report sheet until the survey is completed`() {
        assertEquals(PoleRowAction.OpenReportSheet, poleRowAction(row(), isCompleted = false))
        assertEquals(PoleRowAction.OpenReportSheet, poleRowAction(row(SyncStatus.QUEUED_ONLINE), isCompleted = false))
        assertEquals(PoleRowAction.OpenReportSheet, poleRowAction(row(SyncStatus.DONE, null), isCompleted = false))
        assertEquals(PoleRowAction.None, poleRowAction(row(), isCompleted = true))
    }
}
