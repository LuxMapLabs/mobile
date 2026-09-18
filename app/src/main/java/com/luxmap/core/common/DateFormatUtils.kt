package com.luxmap.core.common

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateFormatUtils {
    private val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val isoDisplayFormatter = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy")
    private val isoDateOnlyFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun formatDisplay(dateTime: LocalDateTime): String = dateTime.format(displayFormatter)

    // Backend sends UTC instants ("2026-08-19T20:00:00Z"). Convert to the device's own
    // timezone before showing a time to the field crew — never show raw UTC. `iso` is
    // external data (network/mock), so a null or malformed string must not crash the
    // screen — falls back to a neutral label instead.
    fun formatIsoInstant(iso: String?): String {
        if (iso.isNullOrBlank()) return "Chưa cập nhật"
        return try {
            Instant.parse(iso).atZone(ZoneId.systemDefault()).format(isoDisplayFormatter)
        } catch (e: DateTimeParseException) {
            "Chưa cập nhật"
        }
    }

    // Same safety as formatIsoInstant, date only — for compact labels like a photo overlay.
    fun formatIsoDateOnly(iso: String?): String {
        if (iso.isNullOrBlank()) return "Chưa cập nhật"
        return try {
            Instant.parse(iso).atZone(ZoneId.systemDefault()).format(isoDateOnlyFormatter)
        } catch (e: DateTimeParseException) {
            "Chưa cập nhật"
        }
    }
}
