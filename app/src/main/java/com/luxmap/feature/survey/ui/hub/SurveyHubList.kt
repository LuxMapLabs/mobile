package com.luxmap.feature.survey.ui.hub

import com.luxmap.core.common.DateFormatUtils
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.workorder.data.CompletionSyncState
import com.luxmap.feature.workorder.ui.detail.workOrderScheduleWindow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

enum class HubSection(val title: String) {
    IN_PROGRESS("Đang làm"),
    TODO("Chưa bắt đầu"),
    PENDING_SYNC("Chờ đồng bộ"),
}

enum class HubItemKind { SURVEY_ROUTE, POLE_GPS }

data class HubItem(
    val order: AssignedSurveyRoute,
    val kind: HubItemKind,
    val syncState: CompletionSyncState?,
)

data class HubGroup(val section: HubSection, val items: List<HubItem>)

fun surveyHubGroups(
    routes: List<AssignedSurveyRoute>,
    poleGpsOrders: List<AssignedSurveyRoute>,
    syncStates: Map<String, CompletionSyncState>,
    completedIds: Set<String>,
): List<HubGroup> {
    val routeIds = routes.map { it.workOrderId }.toSet()
    val all =
        routes.map { it to HubItemKind.SURVEY_ROUTE } +
            poleGpsOrders.filterNot { it.workOrderId in routeIds }.map { it to HubItemKind.POLE_GPS }
    val bySection = HubSection.entries.associateWith { mutableListOf<HubItem>() }
    for ((order, kind) in all) {
        val sync = syncStates[order.workOrderId]
        val section =
            when {
                sync != null -> HubSection.PENDING_SYNC
                order.workOrderId in completedIds -> null
                order.woStatus == "in_progress" -> HubSection.IN_PROGRESS
                order.woStatus in NOT_STARTED_STATUSES -> HubSection.TODO
                else -> null
            } ?: continue
        bySection.getValue(section) += HubItem(order, kind, sync)
    }
    return HubSection.entries.mapNotNull { section ->
        bySection.getValue(section).takeIf { it.isNotEmpty() }?.let { HubGroup(section, it) }
    }
}

private val NOT_STARTED_STATUSES = setOf("assigned", "open")

sealed interface StartGate {
    data object Open : StartGate

    data class NotYet(val opensOn: LocalDate) : StartGate

    data object Overdue : StartGate
}

fun startGate(
    scheduledDate: String?,
    today: LocalDate,
): StartGate {
    val window = workOrderScheduleWindow(scheduledDate, today)
    val unlocksOn = window.unlocksOn
    return when {
        window.canActOnSchedule -> StartGate.Open
        window.isOverdueToComplete -> StartGate.Overdue
        unlocksOn != null -> StartGate.NotYet(unlocksOn)
        else -> StartGate.Open
    }
}

fun StartGate.hint(): String? =
    when (this) {
        StartGate.Open -> null
        is StartGate.NotYet -> "Mở vào " + DateFormatUtils.formatPlainDate(opensOn.toString())
        StartGate.Overdue -> "Đã khoá do quá hạn"
    }

fun scheduleValue(
    scheduledDate: String?,
    today: LocalDate,
): String {
    val parsed =
        try {
            scheduledDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            null
        } ?: return "Chưa có lịch"
    val text = DateFormatUtils.formatPlainDate(scheduledDate)
    return if (parsed == today) "Hôm nay ($text)" else text
}

private val VI = Locale.forLanguageTag("vi")

internal fun surveyHubDate(today: LocalDate): String =
    today.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", VI)).replaceFirstChar { it.titlecase(VI) }
