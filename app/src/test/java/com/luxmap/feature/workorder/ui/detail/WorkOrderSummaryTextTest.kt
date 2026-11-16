package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.WorkOrderTaskKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-11-16")

class WorkOrderSummaryTextTest {
    @Test
    fun `header title names the work order kind`() {
        assertEquals("Lệnh sửa chữa", workOrderHeaderTitle(WorkOrderTaskKind.REPAIR))
        assertEquals("Lệnh kiểm tra", workOrderHeaderTitle(WorkOrderTaskKind.INSPECTION))
        assertEquals("Lệnh khảo sát", workOrderHeaderTitle(WorkOrderTaskKind.SURVEY))
        assertEquals("Lệnh đo GPS cột", workOrderHeaderTitle(WorkOrderTaskKind.POLE_GPS_UPDATE))
    }

    @Test
    fun `schedule label per kind`() {
        assertEquals("Lịch sửa chữa", workOrderScheduleLabel(WorkOrderTaskKind.REPAIR))
        assertEquals("Lịch đo GPS", workOrderScheduleLabel(WorkOrderTaskKind.POLE_GPS_UPDATE))
    }

    @Test
    fun `overdue due date has its own status line`() {
        val due = dueSummary("2026-11-15", TODAY)
        assertEquals("15/11/2026", due.dateText)
        assertEquals("Quá hạn 1 ngày", due.statusText)
        assertTrue(due.isOverdue)
    }

    @Test
    fun `due today`() {
        val due = dueSummary("2026-11-16", TODAY)
        assertEquals("Hạn hôm nay", due.statusText)
        assertTrue(due.isToday)
    }

    @Test
    fun `future due date has no status line`() {
        val due = dueSummary("2026-11-20", TODAY)
        assertEquals("20/11/2026", due.dateText)
        assertNull(due.statusText)
        assertFalse(due.isOverdue)
    }

    @Test
    fun `missing or malformed due date`() {
        val missing = DueSummary("Chưa có hạn", null, isOverdue = false, isToday = false)
        assertEquals(missing, dueSummary(null, TODAY))
        assertEquals(missing, dueSummary("2026-13-40", TODAY))
    }

    @Test
    fun `an overdue due date is an alert only while the schedule has passed and the form is locked`() {
        val overdue = dueSummary("2026-11-13", TODAY)
        assertEquals(DueTone.ALERT, dueTone(overdue, isScheduleOverdue = true))
        assertEquals(DueTone.NEUTRAL, dueTone(overdue, isScheduleOverdue = false))
    }

    @Test
    fun `due today is a warning and a future due date is neutral`() {
        assertEquals(DueTone.WARNING, dueTone(dueSummary("2026-11-16", TODAY), isScheduleOverdue = false))
        assertEquals(DueTone.NEUTRAL, dueTone(dueSummary("2026-11-20", TODAY), isScheduleOverdue = true))
    }
}
