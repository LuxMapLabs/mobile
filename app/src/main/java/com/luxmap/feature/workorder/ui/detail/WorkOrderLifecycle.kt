package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.ui.components.LifecycleStep
import com.luxmap.core.ui.components.LifecycleStepState

// Case-level journey (CLAUDE.md "Luồng Field Engineer đã chốt"), not this one WorkOrder's own
// wo_status machine - matches the real screen (LuxMap App v3.dc.html, "task" screen): a lighting
// case moves Khảo sát -> Duyệt #1 -> Kiểm tra -> Duyệt #2 -> Sửa chữa across up to 3 chained
// WorkOrders (task_kind survey/inspection/repair, linked by case_id). This screen only ever loads
// the ONE WorkOrder being viewed, not the whole case_id chain, so the current step is a direct
// task_kind -> index mapping, the same one the real screen uses, instead of fetching history.
private const val STEP_SURVEY = "Khảo sát"
private const val STEP_REVIEW_1 = "Duyệt #1"
private const val STEP_INSPECT = "Kiểm tra"
private const val STEP_REVIEW_2 = "Duyệt #2"
private const val STEP_REPAIR = "Sửa chữa"

fun workOrderLifecycleSteps(
    woStatus: WorkOrderStatus,
    taskKind: WorkOrderTaskKind,
): List<LifecycleStep> {
    check(woStatus != WorkOrderStatus.CANCELLED) { "A cancelled work order has no lifecycle steps to show" }
    val labels = listOf(STEP_SURVEY, STEP_REVIEW_1, STEP_INSPECT, STEP_REVIEW_2, STEP_REPAIR)
    val currentIndex =
        when (taskKind) {
            WorkOrderTaskKind.SURVEY -> 0
            WorkOrderTaskKind.INSPECTION -> 2
            WorkOrderTaskKind.REPAIR -> 4
        }
    // This WorkOrder's own done/verified status only closes its OWN step in the case, it says
    // nothing about the later steps (a verified inspection does not mean the repair happened yet).
    val currentStepDone = woStatus == WorkOrderStatus.DONE || woStatus == WorkOrderStatus.VERIFIED
    return labels.mapIndexed { index, label ->
        val state =
            when {
                index < currentIndex -> LifecycleStepState.DONE
                index == currentIndex -> if (currentStepDone) LifecycleStepState.DONE else LifecycleStepState.CURRENT
                else -> LifecycleStepState.FUTURE
            }
        LifecycleStep(label = label, state = state)
    }
}
