package com.luxmap.feature.notification.ui

// Only entity_type = work_order has a real destination today - see CLAUDE.md's F14 "Khoảng
// trống đã biết" entry and the design spec section 6 for why survey_sweep/fault/citizen_report
// are deliberately not navigable in v1 (and why an unrecognized future entity_type must not
// navigate either). Pulled out of the Composable Route so this one-line decision has its own
// test instead of being verified only by a manual device check.
fun notificationNavigationTarget(row: NotificationListRowState): String? =
    if (row.entityType == "work_order") row.entityId else null
