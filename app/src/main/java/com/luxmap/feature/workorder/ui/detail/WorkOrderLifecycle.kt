package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.LifecycleStep
import com.luxmap.core.ui.components.LifecycleStepState

private const val STEP_LABEL_OPEN = "Mở"
private const val STEP_LABEL_ASSIGNED = "Đã giao"
private const val STEP_LABEL_DONE = "Hoàn tất"
private const val STEP_LABEL_VERIFIED = "Đã nghiệm thu"

fun workOrderLifecycleSteps(
    woStatus: WorkOrderStatus,
    taskKind: WorkOrderTaskKind,
): List<LifecycleStep> {
    check(woStatus != WorkOrderStatus.CANCELLED) { "A cancelled work order has no lifecycle steps to show" }
    val labels = listOf(STEP_LABEL_OPEN, STEP_LABEL_ASSIGNED, taskKind.label(), STEP_LABEL_DONE, STEP_LABEL_VERIFIED)
    val currentIndex =
        when (woStatus) {
            WorkOrderStatus.OPEN -> 0
            WorkOrderStatus.ASSIGNED -> 1
            WorkOrderStatus.IN_PROGRESS -> 2
            WorkOrderStatus.DONE -> 3
            WorkOrderStatus.VERIFIED -> 4
            WorkOrderStatus.CANCELLED -> error("unreachable, checked above")
        }
    return labels.mapIndexed { index, label ->
        val state =
            when {
                woStatus == WorkOrderStatus.VERIFIED -> LifecycleStepState.DONE
                index < currentIndex -> LifecycleStepState.DONE
                index == currentIndex -> LifecycleStepState.CURRENT
                else -> LifecycleStepState.FUTURE
            }
        LifecycleStep(label = label, state = state)
    }
}
