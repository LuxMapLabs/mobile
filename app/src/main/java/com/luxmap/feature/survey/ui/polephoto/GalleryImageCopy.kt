package com.luxmap.feature.survey.ui.polephoto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

private const val JPEG_QUALITY = 90
private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

internal fun isJpeg(bytes: ByteArray): Boolean =
    bytes.size >= JPEG_SIGNATURE.size && bytes.copyOf(JPEG_SIGNATURE.size).contentEquals(JPEG_SIGNATURE)

suspend fun copyGalleryImageToFile(
    input: InputStream?,
    destination: File,
): Boolean =
    withContext(Dispatchers.IO) {
        if (input == null) {
            false
        } else {
            runCatching {
                input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
            }.isSuccess
        }
    }

suspend fun copyGalleryImageAsJpeg(
    input: InputStream?,
    destination: File,
): Boolean =
    withContext(Dispatchers.IO) {
        if (input == null) {
            false
        } else {
            runCatching {
                val bytes = input.use { it.readBytes() }
                if (isJpeg(bytes)) {
                    destination.writeBytes(bytes)
                    true
                } else {
                    reencodeAsJpeg(bytes, destination)
                }
            }.getOrDefault(false)
        }
    }

private fun reencodeAsJpeg(
    bytes: ByteArray,
    destination: File,
): Boolean {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
    return try {
        destination.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output) }
    } finally {
        bitmap.recycle()
    }
}
