package com.civicflow.core.common

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateFormatUtils {
    private val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun formatDisplay(dateTime: LocalDateTime): String = dateTime.format(displayFormatter)
}
