package com.luxmap.feature.survey.ui.hub

import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-11-17")

private fun order(
    id: String,
    status: String = "assigned",
    scheduled: String? = null,
) = AssignedSurveyRoute(
    workOrderId = id,
    title = "Lệnh $id",
    communeId = "COM-001",
    woStatus = status,
    dueDate = null,
    scheduledDate = scheduled,
)

class SurveyHubListTest {
    @Test
    fun `groups by status mixing both kinds, routes before pole gps, empty groups dropped`() {
        val groups =
            surveyHubGroups(
                routes = listOf(order("R1", "in_progress"), order("R2")),
                poleGpsOrders = listOf(order("G1", "in_progress")),
                syncStates = emptyMap(),
                completedIds = emptySet(),
            )
        assertEquals(listOf(HubSection.IN_PROGRESS, HubSection.TODO), groups.map { it.section })
        assertEquals(listOf("R1", "G1"), groups[0].items.map { it.order.workOrderId })
        assertEquals(listOf(HubItemKind.SURVEY_ROUTE, HubItemKind.POLE_GPS), groups[0].items.map { it.kind })
        assertEquals(listOf("R2"), groups[1].items.map { it.order.workOrderId })
    }

    @Test
    fun `a submitted but unsynced order moves to the pending sync group, a synced one is hidden`() {
        val groups =
            surveyHubGroups(
                routes = listOf(order("R1", "in_progress"), order("R2", "in_progress")),
                poleGpsOrders = emptyList(),
                syncStates = mapOf("R1" to CompletionSyncState.FAILED),
                completedIds = setOf("R1", "R2"),
            )
        assertEquals(listOf(HubSection.PENDING_SYNC), groups.map { it.section })
        assertEquals(CompletionSyncState.FAILED, groups.single().items.single().syncState)
    }

    @Test
    fun `an id present in both lists is shown once as a survey route`() {
        val groups =
            surveyHubGroups(
                routes = listOf(order("X")),
                poleGpsOrders = listOf(order("X")),
                syncStates = emptyMap(),
                completedIds = emptySet(),
            )
        assertEquals(1, groups.single().items.size)
        assertEquals(HubItemKind.SURVEY_ROUTE, groups.single().items.single().kind)
    }

    @Test
    fun `start gate follows the F09 schedule window`() {
        assertEquals(StartGate.NotYet(LocalDate.parse("2026-11-19")), startGate("2026-11-20", TODAY))
        assertEquals(StartGate.Open, startGate("2026-11-18", TODAY))
        assertEquals(StartGate.Open, startGate("2026-11-17", TODAY))
        assertEquals(StartGate.Overdue, startGate("2026-11-16", TODAY))
        assertEquals(StartGate.Open, startGate(null, TODAY))
        assertEquals(StartGate.Open, startGate("not-a-date", TODAY))
    }

    @Test
    fun `gate hints`() {
        assertEquals("Mở vào 19/11/2026", StartGate.NotYet(LocalDate.parse("2026-11-19")).hint())
        assertEquals("Đã khoá do quá hạn", StartGate.Overdue.hint())
        assertNull(StartGate.Open.hint())
    }

    @Test
    fun `schedule value`() {
        assertEquals("17/11/2026 · Hôm nay", scheduleValue("2026-11-17", TODAY))
        assertEquals("20/11/2026", scheduleValue("2026-11-20", TODAY))
        assertEquals("Chưa có lịch", scheduleValue(null, TODAY))
        assertEquals("Chưa có lịch", scheduleValue("bad", TODAY))
    }
}
