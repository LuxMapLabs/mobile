package com.luxmap.core.ui.components

import androidx.compose.ui.graphics.Color
import com.luxmap.core.common.DateFormatUtils
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class DueLabel(val text: String, val isToday: Boolean, val isOverdue: Boolean)

fun dueDateLabel(
    dueDate: String?,
    today: LocalDate = LocalDate.now(),
): DueLabel {
    if (dueDate.isNullOrBlank()) return DueLabel("Chưa có hạn", isToday = false, isOverdue = false)
    val parsed =
        try {
            LocalDate.parse(dueDate)
        } catch (e: DateTimeParseException) {
            null
        } ?: return DueLabel("Chưa có hạn", isToday = false, isOverdue = false)
    val formattedDate = DateFormatUtils.formatPlainDate(dueDate)
    return when {
        parsed == today -> DueLabel("Hạn hôm nay", isToday = true, isOverdue = false)
        parsed.isBefore(today) -> {
            val daysOverdue = ChronoUnit.DAYS.between(parsed, today)
            DueLabel("Quá hạn $daysOverdue ngày · hạn $formattedDate", isToday = false, isOverdue = true)
        }
        else -> DueLabel("Hạn $formattedDate", isToday = false, isOverdue = false)
    }
}

val DueTodayColor = Color(0xFFB45309)
