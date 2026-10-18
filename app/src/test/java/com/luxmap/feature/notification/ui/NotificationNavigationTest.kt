package com.luxmap.feature.notification.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun row(entityType: String) =
    NotificationListRowState(
        "NTF-1",
        "work_order_assigned",
        "title",
        "body",
        entityType,
        "WO-1001",
        "2026-10-18T08:00:00Z",
        isRead = false,
    )

class NotificationNavigationTest {
    @Test
    fun `work_order rows resolve to their entity_id as the work order to open`() {
        assertEquals("WO-1001", notificationNavigationTarget(row("work_order")))
    }

    @Test
    fun `survey_sweep rows do not navigate - no real destination exists yet`() {
        assertNull(notificationNavigationTarget(row("survey_sweep")))
    }

    @Test
    fun `fault rows do not navigate`() {
        assertNull(notificationNavigationTarget(row("fault")))
    }

    @Test
    fun `citizen_report rows do not navigate`() {
        assertNull(notificationNavigationTarget(row("citizen_report")))
    }

    @Test
    fun `an unrecognized entity_type does not navigate`() {
        assertNull(notificationNavigationTarget(row("some_future_entity")))
    }
}
