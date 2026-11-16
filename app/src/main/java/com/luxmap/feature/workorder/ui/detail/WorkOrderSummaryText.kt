package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.WorkOrderTaskKind
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

fun workOrderHeaderTitle(kind: WorkOrderTaskKind): String =
    when (kind) {
        WorkOrderTaskKind.REPAIR -> "Lệnh sửa chữa"
        WorkOrderTaskKind.INSPECTION -> "Lệnh kiểm tra"
        WorkOrderTaskKind.SURVEY -> "Lệnh khảo sát"
        WorkOrderTaskKind.POLE_GPS_UPDATE -> "Lệnh đo GPS cột"
    }

fun workOrderScheduleLabel(kind: WorkOrderTaskKind): String =
    when (kind) {
        WorkOrderTaskKind.REPAIR -> "Lịch sửa chữa"
        WorkOrderTaskKind.POLE_GPS_UPDATE -> "Lịch đo GPS"
        WorkOrderTaskKind.SURVEY -> "Lịch khảo sát"
        WorkOrderTaskKind.INSPECTION -> "Lịch kiểm tra"
    }

data class DueSummary(
    val dateText: String,
    val statusText: String?,
    val isOverdue: Boolean,
    val isToday: Boolean,
)

fun dueSummary(
    dueDate: String?,
    today: LocalDate,
): DueSummary {
    val parsed =
        try {
            dueDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            null
        } ?: return DueSummary("Chưa có hạn", null, isOverdue = false, isToday = false)
    val dateText = DateFormatUtils.formatPlainDate(dueDate)
    return when {
        parsed == today -> DueSummary(dateText, "Hạn hôm nay", isOverdue = false, isToday = true)
        parsed.isBefore(today) -> {
            val days = ChronoUnit.DAYS.between(parsed, today)
            DueSummary(dateText, "Quá hạn $days ngày", isOverdue = true, isToday = false)
        }
        else -> DueSummary(dateText, null, isOverdue = false, isToday = false)
    }
}

enum class DueTone { ALERT, WARNING, NEUTRAL }

fun dueTone(
    due: DueSummary,
    isScheduleOverdue: Boolean,
): DueTone =
    when {
        due.isOverdue && isScheduleOverdue -> DueTone.ALERT
        due.isToday -> DueTone.WARNING
        else -> DueTone.NEUTRAL
    }
