package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun detail(woStatus: String) =
    WorkOrderDetail(
        workOrderId = "WO-1",
        title = "Cập nhật GPS cột",
        woStatus = woStatus,
        taskKind = "pole_gps_update",
        dueDate = null,
        scheduledDate = null,
        note = null,
        reviewNote = null,
        allowedActions = emptyList(),
        cancellationRequestStatus = "none",
        cancellationRejectNote = null,
        faults = emptyList(),
    )

private fun synced() =
    LocalWorkOrderCompletionEntity(
        workOrderId = "WO-1",
        reportNote = "đã đo",
        materialsUsed = null,
        faultOutcomesJson = null,
        clientOpId = "COMP-1",
        submitStatus = "synced",
    )

class WorkOrderClosedStateTest {
    @Test
    fun `a verified or cancelled order is closed, every other status is not`() {
        assertTrue(detail("verified").isClosed())
        assertTrue(detail("cancelled").isClosed())
        listOf("open", "assigned", "in_progress", "done").forEach { assertFalse(detail(it).isClosed()) }
    }

    @Test
    fun `a leftover local completion no longer counts once the server closed the order`() {
        val verified = WorkOrderDetailUiState.Success(detail = detail("verified"), localCompletion = synced())

        assertFalse(verified.hasActiveLocalCompletion())
        assertNull(verified.syncStatus())
    }

    @Test
    fun `a local completion still counts while the order awaits review`() {
        val done = WorkOrderDetailUiState.Success(detail = detail("done"), localCompletion = synced())

        assertTrue(done.hasActiveLocalCompletion())
        assertEquals(SyncStatus.DONE, done.syncStatus())
    }
}
