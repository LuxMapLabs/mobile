package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.ui.components.LifecycleStepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkOrderLifecycleTest {
    @Test
    fun open_marks_only_the_first_step_current() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.OPEN, WorkOrderTaskKind.INSPECTION)
        assertEquals(LifecycleStepState.CURRENT, steps[0].state)
        assertEquals(LifecycleStepState.FUTURE, steps[1].state)
        assertEquals(LifecycleStepState.FUTURE, steps[4].state)
    }

    @Test
    fun in_progress_marks_the_task_kind_step_current_with_its_label() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.IN_PROGRESS, WorkOrderTaskKind.REPAIR)
        assertEquals(LifecycleStepState.DONE, steps[0].state)
        assertEquals(LifecycleStepState.DONE, steps[1].state)
        assertEquals(LifecycleStepState.CURRENT, steps[2].state)
        assertEquals("Sửa chữa", steps[2].label)
        assertEquals(LifecycleStepState.FUTURE, steps[3].state)
    }

    @Test
    fun verified_marks_every_step_done() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.VERIFIED, WorkOrderTaskKind.INSPECTION)
        steps.forEach { assertEquals(LifecycleStepState.DONE, it.state) }
    }

    @Test
    fun cancelled_is_rejected() {
        assertThrows(IllegalStateException::class.java) {
            workOrderLifecycleSteps(WorkOrderStatus.CANCELLED, WorkOrderTaskKind.INSPECTION)
        }
    }
}
