package com.luxmap.feature.notification.data

enum class NotificationType {
    WORK_ORDER_ASSIGNED,
    WORK_ORDER_UNASSIGNED,
    WORK_ORDER_RESCHEDULED,
    WORK_ORDER_RETURNED,
    WORK_ORDER_CANCELLED,
    WORK_ORDER_COMPLETED,
    WORK_ORDER_VERIFIED,
    WORK_ORDER_OVERDUE,
    SURVEY_RETURNED,
    SURVEY_READY_FOR_REVIEW,
    SURVEY_PROCESSING_FAILED,
    FAULT_REPORTED,
    CITIZEN_REPORT_RECEIVED,
    UNKNOWN,
}

// type arrives from the backend as a snake_case wire string - an unknown value (a type added on
// the backend before the app knows about it) falls back to UNKNOWN's generic icon instead of
// crashing the screen. Checked directly against NotificationType enum in
// luxmap_backend/src/LuxMap.Modules.Notifications/Entities/Notification.cs (read 2026-10-18).
fun notificationTypeFromWire(value: String): NotificationType =
    when (value) {
        "work_order_assigned" -> NotificationType.WORK_ORDER_ASSIGNED
        "work_order_unassigned" -> NotificationType.WORK_ORDER_UNASSIGNED
        "work_order_rescheduled" -> NotificationType.WORK_ORDER_RESCHEDULED
        "work_order_returned" -> NotificationType.WORK_ORDER_RETURNED
        "work_order_cancelled" -> NotificationType.WORK_ORDER_CANCELLED
        "work_order_completed" -> NotificationType.WORK_ORDER_COMPLETED
        "work_order_verified" -> NotificationType.WORK_ORDER_VERIFIED
        "work_order_overdue" -> NotificationType.WORK_ORDER_OVERDUE
        "survey_returned" -> NotificationType.SURVEY_RETURNED
        "survey_ready_for_review" -> NotificationType.SURVEY_READY_FOR_REVIEW
        "survey_processing_failed" -> NotificationType.SURVEY_PROCESSING_FAILED
        "fault_reported" -> NotificationType.FAULT_REPORTED
        "citizen_report_received" -> NotificationType.CITIZEN_REPORT_RECEIVED
        else -> NotificationType.UNKNOWN
    }
