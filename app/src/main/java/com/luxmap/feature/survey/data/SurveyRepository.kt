package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow

// Trimmed to exactly what GET /work-orders (task_kind=survey) returns - no per-segment length or
// assigned-by name, those were placeholder fields from a nonexistent /survey-sweeps/planned
// endpoint. woStatus is the raw wire value (WorkOrderStatus wire enum), parsed at the UI layer
// like WorkOrderSummaryItem does.
data class AssignedSurveyRoute(
    val workOrderId: String,
    val title: String,
    val communeId: String,
    val woStatus: String,
    val dueDate: String?,
    val scheduledDate: String?,
)

data class AssignedRoutesResult(
    val routes: List<AssignedSurveyRoute>,
    // True when this came from the local cache after a failed fetch, not the live backend.
    val isStale: Boolean,
)

// Field Engineer never creates/edits a route on mobile (spec §10) — this only observes what
// "Kỹ sư bảo trì" assigned: a WorkOrder with task_kind=survey, same GET /api/v1/work-orders
// endpoint Home (F02) already uses, filtered to task_kind=survey (confirmed against
// luxmap_backend source 2026-10-14 - there is no separate survey-sweeps endpoint).
interface SurveyRepository {
    fun observeAssignedRoutes(): Flow<AssignedRoutesResult>

    // GPS list cache is scoped to the verified session user.
    fun observeAssignedPoleGpsOrders(): Flow<AssignedRoutesResult>

    // Same generic POST /work-orders/{id}/start WorkOrderDetailScreen (F09) already uses - the
    // Hub needs its own way to call it, since Home deliberately hides survey work orders (see
    // HomeScreen.kt), so F09 is not reachable for them except through a notification tap.
    suspend fun startRoute(workOrderId: String): Result<Unit>

    // Same generic POST /work-orders/{id}/request-cancellation WorkOrderDetailScreen (F09)
    // already uses (CLAUDE.md WO-CANCEL-REQUEST) - the rule is wo_status == assigned regardless
    // of task_kind, so this works the same way for a survey route.
    suspend fun requestCancellation(
        workOrderId: String,
        reason: String,
    ): Result<Unit>
}
