package com.luxmap.feature.notification.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTypeTest {
    @Test
    fun `maps every real backend wire value to its enum constant`() {
        val expected =
            mapOf(
                "work_order_assigned" to NotificationType.WORK_ORDER_ASSIGNED,
                "work_order_unassigned" to NotificationType.WORK_ORDER_UNASSIGNED,
                "work_order_rescheduled" to NotificationType.WORK_ORDER_RESCHEDULED,
                "work_order_returned" to NotificationType.WORK_ORDER_RETURNED,
                "work_order_cancelled" to NotificationType.WORK_ORDER_CANCELLED,
                "work_order_completed" to NotificationType.WORK_ORDER_COMPLETED,
                "work_order_verified" to NotificationType.WORK_ORDER_VERIFIED,
                "work_order_overdue" to NotificationType.WORK_ORDER_OVERDUE,
                "survey_returned" to NotificationType.SURVEY_RETURNED,
                "survey_ready_for_review" to NotificationType.SURVEY_READY_FOR_REVIEW,
                "survey_processing_failed" to NotificationType.SURVEY_PROCESSING_FAILED,
                "fault_reported" to NotificationType.FAULT_REPORTED,
                "citizen_report_received" to NotificationType.CITIZEN_REPORT_RECEIVED,
            )
        expected.forEach { (wire, type) -> assertEquals(type, notificationTypeFromWire(wire)) }
    }

    @Test
    fun `falls back to UNKNOWN for a value the app does not recognize yet`() {
        assertEquals(NotificationType.UNKNOWN, notificationTypeFromWire("some_future_type"))
    }
}
