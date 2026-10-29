package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.ui.components.LifecycleStepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkOrderLifecycleTest {
    @Test
    fun survey_task_is_at_the_first_step() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.ASSIGNED, WorkOrderTaskKind.SURVEY)
        assertEquals(LifecycleStepState.CURRENT, steps[0].state)
        assertEquals(LifecycleStepState.FUTURE, steps[1].state)
        assertEquals(LifecycleStepState.FUTURE, steps[4].state)
    }

    @Test
    fun inspection_task_is_at_the_kiem_tra_step_with_the_earlier_case_steps_done() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.IN_PROGRESS, WorkOrderTaskKind.INSPECTION)
        assertEquals(LifecycleStepState.DONE, steps[0].state)
        assertEquals(LifecycleStepState.DONE, steps[1].state)
        assertEquals(LifecycleStepState.CURRENT, steps[2].state)
        assertEquals("Kiểm tra", steps[2].label)
        assertEquals(LifecycleStepState.FUTURE, steps[3].state)
        assertEquals(LifecycleStepState.FUTURE, steps[4].state)
    }

    @Test
    fun repair_task_is_at_the_sua_chua_step_with_every_earlier_case_step_done() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.ASSIGNED, WorkOrderTaskKind.REPAIR)
        assertEquals(LifecycleStepState.DONE, steps[0].state)
        assertEquals(LifecycleStepState.DONE, steps[1].state)
        assertEquals(LifecycleStepState.DONE, steps[2].state)
        assertEquals(LifecycleStepState.DONE, steps[3].state)
        assertEquals(LifecycleStepState.CURRENT, steps[4].state)
        assertEquals("Sửa chữa", steps[4].label)
    }

    @Test
    fun done_marks_only_this_work_orders_own_step_done_not_the_rest_of_the_case() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.DONE, WorkOrderTaskKind.INSPECTION)
        assertEquals(LifecycleStepState.DONE, steps[2].state)
        assertEquals(LifecycleStepState.FUTURE, steps[3].state)
        assertEquals(LifecycleStepState.FUTURE, steps[4].state)
    }

    @Test
    fun verified_marks_only_this_work_orders_own_step_done_not_the_rest_of_the_case() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.VERIFIED, WorkOrderTaskKind.INSPECTION)
        assertEquals(LifecycleStepState.DONE, steps[2].state)
        assertEquals(LifecycleStepState.FUTURE, steps[3].state)
        assertEquals(LifecycleStepState.FUTURE, steps[4].state)
    }

    @Test
    fun cancelled_is_rejected() {
        assertThrows(IllegalStateException::class.java) {
            workOrderLifecycleSteps(WorkOrderStatus.CANCELLED, WorkOrderTaskKind.INSPECTION)
        }
    }

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
    fun schedule_window_stays_open_but_flags_overdue_after_the_scheduled_date() {
        val window = workOrderScheduleWindow("2026-11-10", today = LocalDate.parse("2026-11-11"))
        assertTrue(window.canActOnSchedule)
        assertTrue(window.isOverdueToComplete)
    }

    @Test
    fun schedule_window_does_not_apply_when_there_is_no_scheduled_date() {
        val window = workOrderScheduleWindow(null, today = LocalDate.parse("2026-11-11"))
        assertTrue(window.canActOnSchedule)
        assertFalse(window.isOverdueToComplete)
    }
}
