package com.luxmap.feature.home.ui

import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-11-16")

private fun item(
    id: String,
    kind: String = "repair",
    status: String = "assigned",
    due: String? = null,
    score: Double? = null,
) = WorkOrderSummaryItem(
    workOrderId = id,
    woStatus = status,
    dueDate = due,
    priorityScore = score,
    taskKind = kind,
    communeId = "COM-001",
    title = "Lệnh $id",
    scheduledDate = null,
    faultCount = 0,
)

private fun data(vararg items: WorkOrderSummaryItem) =
    HomeData(
        metrics = HomeMetrics(assignedCount = 99, inProgressCount = 99, overdueCount = 99, plannedSweepCount = null),
        clusters = listOf(WorkOrderCluster(clusterLabel = "COM-001", items = items.toList())),
    )

class HomeTaskListTest {
    @Test
    fun `default tab waits for loaded data and selects the tab with work`() {
        assertEquals(null, initialHomeTab(HomeUiState.Loading, null))
        assertEquals(null, initialHomeTab(HomeUiState.Error("offline"), null))
        assertEquals(HomeTaskTab.INSPECTION, initialHomeTab(HomeUiState.Success(data(item("I", "inspection"))), null))
        assertEquals(HomeTaskTab.REPAIR, initialHomeTab(HomeUiState.Success(data(item("R"))), null))
        assertEquals(
            HomeTaskTab.REPAIR,
            initialHomeTab(HomeUiState.Success(data(item("R"), item("I", "inspection"))), null),
        )
        assertEquals(HomeTaskTab.REPAIR, initialHomeTab(HomeUiState.Success(data()), null))
        assertEquals(HomeTaskTab.REPAIR, initialHomeTab(HomeUiState.Empty, null))
    }

    @Test
    fun `user tab choice survives reloads and updated tasks`() {
        val selected = HomeTaskTab.INSPECTION
        assertEquals(selected, initialHomeTab(HomeUiState.Loading, selected))
        assertEquals(selected, initialHomeTab(HomeUiState.Empty, selected))
        assertEquals(selected, initialHomeTab(HomeUiState.Success(data(item("R"))), selected))
        assertEquals(
            HomeTaskTab.REPAIR,
            initialHomeTab(HomeUiState.Success(data(item("I", "inspection"))), HomeTaskTab.REPAIR),
        )
    }

    @Test
    fun `visible tasks keep only repair and inspection, dropping survey, pole gps and unknown kinds`() {
        val visible =
            data(
                item("R", kind = "repair"),
                item("I", kind = "inspection"),
                item("S", kind = "survey"),
                item("G", kind = "pole_gps_update"),
                item("X", kind = "audit"),
            ).visibleTasks()
        assertEquals(listOf("R", "I"), visible.map { it.workOrderId })
    }

    @Test
    fun `summary counts only the visible tasks, not HomeMetrics`() {
        val visible =
            data(
                item("R1", status = "in_progress", due = "2026-11-15"),
                item("R2", status = "assigned", due = "2026-11-20"),
                item("I1", kind = "inspection", status = "in_progress"),
                item("S1", kind = "survey", status = "in_progress", due = "2026-11-01"),
            ).visibleTasks()
        assertEquals(HomeSummary(total = 3, overdue = 1, inProgress = 2), visible.homeSummary(TODAY))
    }

    @Test
    fun `count per tab`() {
        val visible = data(item("R1"), item("R2"), item("I1", kind = "inspection")).visibleTasks()
        assertEquals(2, visible.countFor(HomeTaskTab.REPAIR))
        assertEquals(1, visible.countFor(HomeTaskTab.INSPECTION))
    }

    @Test
    fun `sorted for a tab puts overdue first, then due today, then higher priority score`() {
        val visible =
            data(
                item("LOW", score = 10.0, due = "2026-11-30"),
                item("HIGH", score = 90.0, due = "2026-11-30"),
                item("TODAY", score = 1.0, due = "2026-11-16"),
                item("OVERDUE", score = 0.0, due = "2026-11-10"),
                item("NO_SCORE", score = null),
                item("INSPECTION", kind = "inspection", due = "2026-11-01"),
            ).visibleTasks()
        assertEquals(
            listOf("OVERDUE", "TODAY", "HIGH", "LOW", "NO_SCORE"),
            visible.sortedFor(HomeTaskTab.REPAIR, TODAY).map { it.workOrderId },
        )
    }
}
