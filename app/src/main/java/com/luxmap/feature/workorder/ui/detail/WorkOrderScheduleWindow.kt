package com.luxmap.feature.workorder.ui.detail

import java.time.LocalDate
import java.time.format.DateTimeParseException

// Mobile-only guard (CLAUDE.md, "Cua so hanh dong Bat dau/Yeu cau huy theo lich", updated
// 2026-11-09) - start/request_cancellation unlock from 1 day before scheduled_date. Submitting
// only opens on scheduled_date itself. Once scheduled_date has passed the work order is only
// flagged late (isPastSchedule): it stays startable and submittable, with a warning, because a
// night survey routinely runs past midnight and a locked order left the engineer with no way
// forward (UX review, 2026-11-27). The server does not enforce the schedule either. This is on
// top of the server's allowed_actions, not instead of it.
data class WorkOrderScheduleWindow(
    val canActOnSchedule: Boolean,
    val isPastSchedule: Boolean,
    // Only set when canActOnSchedule is false (the window has not opened yet), so the UI can
    // show "opens on <date>".
    val unlocksOn: LocalDate? = null,
    val submitOpensOn: LocalDate? = null,
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
        return WorkOrderScheduleWindow(canActOnSchedule = true, isPastSchedule = false)
    }
    val unlocksOn = scheduled.minusDays(1)
    return WorkOrderScheduleWindow(
        canActOnSchedule = !today.isBefore(unlocksOn),
        isPastSchedule = today.isAfter(scheduled),
        unlocksOn = if (today.isBefore(unlocksOn)) unlocksOn else null,
        submitOpensOn = if (today.isBefore(scheduled)) scheduled else null,
    )
}
