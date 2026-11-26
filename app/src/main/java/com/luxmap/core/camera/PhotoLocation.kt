package com.luxmap.core.camera

import android.media.ExifInterface
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class PhotoLocation(
    val lat: Double,
    val lng: Double,
    val capturedAt: Instant?,
)

interface PhotoLocationReader {
    fun read(file: File): PhotoLocation?
}

class ExifPhotoLocationReader
    @Inject
    constructor() : PhotoLocationReader {
        override fun read(file: File): PhotoLocation? {
            val exif = ExifInterface(file.absolutePath)
            return photoLocationFrom(ZoneId.systemDefault()) { exif.getAttribute(it) }
        }
    }

private val EXIF_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
private val EXIF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd")

internal fun photoLocationFrom(
    zone: ZoneId,
    attribute: (String) -> String?,
): PhotoLocation? {
    val lat = exifCoordinate(attribute("GPSLatitude"), attribute("GPSLatitudeRef"), "N", "S") ?: return null
    val lng = exifCoordinate(attribute("GPSLongitude"), attribute("GPSLongitudeRef"), "E", "W") ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0 || (lat == 0.0 && lng == 0.0)) return null
    val capturedAt =
        exifDateTime(attribute("DateTimeOriginal"), attribute("OffsetTimeOriginal"), zone)
            ?: exifGpsTime(attribute("GPSDateStamp"), attribute("GPSTimeStamp"))
    return PhotoLocation(lat, lng, capturedAt)
}

internal fun exifCoordinate(
    value: String?,
    ref: String?,
    positive: String,
    negative: String,
): Double? {
    val parts = value?.split(',')?.map { rational(it) ?: return null } ?: return null
    if (parts.size != 3) return null
    val degrees = parts[0] + parts[1] / 60 + parts[2] / 3600
    if (!degrees.isFinite()) return null
    return when (ref?.trim()?.uppercase()) {
        positive -> degrees
        negative -> -degrees
        else -> null
    }
}

private fun rational(text: String): Double? {
    val pieces = text.trim().split('/')
    val numerator = pieces.getOrNull(0)?.toDoubleOrNull() ?: return null
    val denominator = if (pieces.size == 2) pieces[1].toDoubleOrNull() ?: return null else 1.0
    if (pieces.size > 2 || denominator == 0.0) return null
    return numerator / denominator
}

internal fun exifDateTime(
    dateTime: String?,
    offset: String?,
    zone: ZoneId,
): Instant? {
    val local = runCatching { LocalDateTime.parse(dateTime?.trim(), EXIF_DATE_TIME) }.getOrNull() ?: return null
    val zoneOffset = offset?.let { runCatching { ZoneOffset.of(it.trim()) }.getOrNull() }
    return if (zoneOffset != null) OffsetDateTime.of(local, zoneOffset).toInstant() else local.atZone(zone).toInstant()
}

internal fun exifGpsTime(
    date: String?,
    time: String?,
): Instant? {
    val day = runCatching { LocalDate.parse(date?.trim(), EXIF_DATE) }.getOrNull() ?: return null
    val parts = time?.split(',')?.map { rational(it) ?: return null } ?: return null
    if (parts.size != 3) return null
    val seconds = parts[0] * 3600 + parts[1] * 60 + parts[2]
    if (!seconds.isFinite() || seconds < 0 || seconds >= 86_400) return null
    return day.atTime(LocalTime.ofSecondOfDay(seconds.toLong())).toInstant(ZoneOffset.UTC)
}
