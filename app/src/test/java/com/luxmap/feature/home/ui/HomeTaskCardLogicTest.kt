package com.luxmap.feature.home.ui

import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-11-16")

private fun item(
    due: String? = null,
    scheduled: String? = null,
    status: String = "assigned",
) = WorkOrderSummaryItem(
    workOrderId = "WO-1",
    woStatus = status,
    dueDate = due,
    priorityScore = null,
    taskKind = "repair",
    communeId = "COM-001",
    title = "Sửa đèn",
    scheduledDate = scheduled,
    faultCount = 0,
)

class HomeTaskCardLogicTest {
    @Test
    fun `a sync state wins over overdue`() {
        assertEquals(
            HomeTaskBadge.Sync(CompletionSyncState.FAILED),
            homeTaskBadge(item(due = "2026-11-01"), CompletionSyncState.FAILED, TODAY),
        )
    }

    @Test
    fun `overdue wins over the work order status`() {
        assertEquals(HomeTaskBadge.Overdue, homeTaskBadge(item(due = "2026-11-15"), null, TODAY))
    }

    @Test
    fun `no sync state and not overdue shows the wo_status`() {
        assertEquals(HomeTaskBadge.Status("in_progress"), homeTaskBadge(item(status = "in_progress"), null, TODAY))
    }

    @Test
    fun `both dates present`() {
        val line = homeDateLine(item(due = "2026-11-20", scheduled = "2026-11-17"), TODAY)
        assertEquals("Làm: 17/11/2026", line.scheduled)
        assertEquals("Hạn 20/11/2026", line.due)
        assertFalse(line.isOverdue)
    }

    @Test
    fun `due today is flagged`() {
        assertTrue(homeDateLine(item(due = "2026-11-16"), TODAY).isDueToday)
    }

    @Test
    fun `missing or malformed dates are hidden, not shown as placeholders`() {
        val line = homeDateLine(item(due = "2026-13-40", scheduled = ""), TODAY)
        assertNull(line.scheduled)
        assertNull(line.due)
    }
}
