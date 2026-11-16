package com.luxmap.feature.workorder.ui.completion

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal const val MAX_EVIDENCE_BYTES = 16L * 1024 * 1024
private const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
private const val MAX_IMAGE_EDGE = 4_096
private val exifDateFormat = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss")

internal data class GalleryEvidence(
    val file: File,
    val capturedAt: Instant?,
    val coordinates: Pair<Double, Double>?,
    val importedAt: Instant,
)

internal suspend fun importGalleryEvidence(
    resolver: ContentResolver,
    uri: Uri,
    destination: File,
): GalleryEvidence =
    withContext(Dispatchers.IO) {
        val source = File.createTempFile("gallery-", ".tmp", destination.parentFile)
        var imported = false
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open image")
            input.use { stream ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytes = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        bytes += count
                        if (bytes > MAX_SOURCE_BYTES) throw IOException("Image input exceeds size limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val exif = runCatching { ExifInterface(source) }.getOrNull()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Invalid image")
            if (options.outMimeType == "image/jpeg" && source.length() <= MAX_EVIDENCE_BYTES) {
                source.copyTo(destination, overwrite = true)
            } else {
                encodeEvidenceJpeg(source, destination, options, exif)
            }
            if (destination.length() !in 1..MAX_EVIDENCE_BYTES) throw IOException("Image exceeds 16 MiB")
            imported = true
            GalleryEvidence(destination, exif?.captureTime(), exif?.validCoordinates(), Instant.now())
        } finally {
            source.delete()
            if (!imported) destination.delete()
        }
    }

private fun encodeEvidenceJpeg(
    source: File,
    destination: File,
    bounds: BitmapFactory.Options,
    exif: ExifInterface?,
) {
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE) sample *= 2
    val pixels =
        BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Unsupported image")
    val matrix =
        Matrix().apply {
            if (exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
    try {
        val oriented = Bitmap.createBitmap(pixels, 0, 0, pixels.width, pixels.height, matrix, true)
        try {
            val written = destination.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (!written) throw IOException("Cannot encode image")
        } finally {
            if (oriented !== pixels) oriented.recycle()
        }
    } finally {
        pixels.recycle()
    }
}

private fun ExifInterface.captureTime(): Instant? =
    runCatching {
        val date = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
        val offset = getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: return null
        LocalDateTime.parse(date, exifDateFormat).toInstant(ZoneOffset.of(offset))
    }.getOrNull()

private fun ExifInterface.validCoordinates(): Pair<Double, Double>? {
    val coordinates = latLong ?: return null
    val (lat, lng) = coordinates
    return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
}
