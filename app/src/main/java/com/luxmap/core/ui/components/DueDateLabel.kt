package com.luxmap.core.ui.components

import androidx.compose.ui.graphics.Color
import com.luxmap.core.common.DateFormatUtils
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class DueLabel(val text: String, val isToday: Boolean)

fun dueDateLabel(
    dueDate: String?,
    today: LocalDate = LocalDate.now(),
): DueLabel {
    if (dueDate.isNullOrBlank()) return DueLabel("Chưa có hạn", isToday = false)
    val parsed =
        try {
            LocalDate.parse(dueDate)
        } catch (e: DateTimeParseException) {
            null
        } ?: return DueLabel("Chưa có hạn", isToday = false)
    return if (parsed == today) {
        DueLabel("Hạn hôm nay", isToday = true)
    } else {
        DueLabel("Hạn ${DateFormatUtils.formatPlainDate(dueDate)}", isToday = false)
    }
}

val DueTodayColor = Color(0xFFB45309)
