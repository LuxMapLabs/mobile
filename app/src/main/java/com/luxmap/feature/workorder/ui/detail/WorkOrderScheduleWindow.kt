package com.luxmap.feature.workorder.ui.detail

import java.time.LocalDate
import java.time.format.DateTimeParseException

// Mobile-only guard (CLAUDE.md, "Cua so hanh dong Bat dau/Yeu cau huy theo lich", updated
// 2026-11-09) - start/request_cancellation only unlock from 1 day before scheduled_date
// through scheduled_date itself, then close again once scheduled_date has passed. A work
// order is flagged overdue to complete once scheduled_date has passed, which also locks the
// completion form/submit button for an in-progress one. This is on top of the server's
// allowed_actions, not instead of it.
data class WorkOrderScheduleWindow(
    val canActOnSchedule: Boolean,
    val isOverdueToComplete: Boolean,
    // Only set when canActOnSchedule is false and the window has not opened yet, so the UI
    // can show "opens on <date>" - not set once it has closed again from being overdue.
    val unlocksOn: LocalDate? = null,
)

fun workOrderScheduleWindow(
    scheduledDate: String?,
    today: LocalDate,
): WorkOrderScheduleWindow {
    val scheduled =
        try {
            scheduledDate?.let { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            null
        }
    // No scheduled_date (or an unparseable one) means this rule does not apply - fall back to
    // the old behavior, fully driven by allowed_actions.
    if (scheduled == null) {
        return WorkOrderScheduleWindow(canActOnSchedule = true, isOverdueToComplete = false)
    }
    val unlocksOn = scheduled.minusDays(1)
    val isOverdueToComplete = today.isAfter(scheduled)
    return WorkOrderScheduleWindow(
        canActOnSchedule = !today.isBefore(unlocksOn) && !isOverdueToComplete,
        isOverdueToComplete = isOverdueToComplete,
        unlocksOn = if (today.isBefore(unlocksOn)) unlocksOn else null,
    )
}
