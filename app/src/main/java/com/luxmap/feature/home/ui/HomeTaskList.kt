package com.luxmap.feature.home.ui

import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import java.time.LocalDate

enum class HomeTaskTab(val wireKind: String, val label: String) {
    REPAIR("repair", "Sửa chữa"),
    INSPECTION("inspection", "Kiểm tra"),
    SURVEY("survey", "Khảo sát"),
    POLE_GPS_UPDATE("pole_gps_update", "GPS cột"),
}

private val ALWAYS_VISIBLE_TABS = setOf(HomeTaskTab.REPAIR, HomeTaskTab.INSPECTION)

data class HomeSummary(val total: Int, val overdue: Int, val inProgress: Int)

private val VISIBLE_KINDS = HomeTaskTab.entries.map { it.wireKind }.toSet()

fun HomeData.visibleTasks(): List<WorkOrderSummaryItem> =
    clusters.flatMap { it.items }.filter { it.taskKind in VISIBLE_KINDS }

fun List<WorkOrderSummaryItem>.homeSummary(today: LocalDate): HomeSummary =
    HomeSummary(
        total = size,
        overdue = count { dueDateLabel(it.dueDate, today).isOverdue },
        inProgress = count { it.woStatus == "in_progress" },
    )

fun List<WorkOrderSummaryItem>.countFor(tab: HomeTaskTab): Int = count { it.taskKind == tab.wireKind }

fun List<WorkOrderSummaryItem>.availableTabs(): List<HomeTaskTab> =
    HomeTaskTab.entries.filter { it in ALWAYS_VISIBLE_TABS || countFor(it) > 0 }

fun List<WorkOrderSummaryItem>.sortedFor(
    tab: HomeTaskTab,
    today: LocalDate,
): List<WorkOrderSummaryItem> =
    filter { it.taskKind == tab.wireKind }.sortedWith(
        compareByDescending<WorkOrderSummaryItem> { dueDateLabel(it.dueDate, today).isOverdue }
            .thenByDescending { dueDateLabel(it.dueDate, today).isToday }
            .thenByDescending { it.priorityScore ?: 0.0 },
    )

internal fun initialHomeTab(
    state: HomeUiState,
    selected: HomeTaskTab?,
): HomeTaskTab? {
    if (selected != null) return selected
    return when (state) {
        HomeUiState.Loading, is HomeUiState.Error -> null
        HomeUiState.Empty -> HomeTaskTab.REPAIR
        is HomeUiState.Success -> {
            val tasks = state.data.visibleTasks()
            HomeTaskTab.entries.firstOrNull { tasks.countFor(it) > 0 } ?: HomeTaskTab.REPAIR
        }
    }
}
