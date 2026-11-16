package com.luxmap.feature.workorder.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkOrderScheduleWindowTest {
    @Test
    fun schedule_window_is_closed_more_than_a_day_before_scheduled_date() {
        val window = workOrderScheduleWindow("2026-11-10", today = LocalDate.parse("2026-11-08"))
        assertFalse(window.canActOnSchedule)
        assertFalse(window.isOverdueToComplete)
        assertEquals(LocalDate.parse("2026-11-09"), window.unlocksOn)
    }

    @Test
    fun schedule_window_opens_exactly_one_day_before_scheduled_date() {
        val window = workOrderScheduleWindow("2026-11-10", today = LocalDate.parse("2026-11-09"))
        assertTrue(window.canActOnSchedule)
        assertFalse(window.isOverdueToComplete)
    }

    @Test
    fun schedule_window_stays_open_on_the_scheduled_date_itself() {
        val window = workOrderScheduleWindow("2026-11-10", today = LocalDate.parse("2026-11-10"))
        assertTrue(window.canActOnSchedule)
        assertFalse(window.isOverdueToComplete)
    }

    @Test
    fun schedule_window_closes_again_and_flags_overdue_after_the_scheduled_date() {
        val window = workOrderScheduleWindow("2026-11-10", today = LocalDate.parse("2026-11-11"))
        assertFalse(window.canActOnSchedule)
        assertTrue(window.isOverdueToComplete)
        assertEquals(null, window.unlocksOn)
    }

    @Test
    fun schedule_window_does_not_apply_when_there_is_no_scheduled_date() {
        val window = workOrderScheduleWindow(null, today = LocalDate.parse("2026-11-11"))
        assertTrue(window.canActOnSchedule)
        assertFalse(window.isOverdueToComplete)
    }
}
