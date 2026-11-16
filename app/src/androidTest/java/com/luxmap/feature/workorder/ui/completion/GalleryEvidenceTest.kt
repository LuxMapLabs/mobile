package com.luxmap.feature.workorder.ui.completion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class GalleryEvidenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun importsPngAsJpegAndLeavesMissingGpsForTheUser() =
        runTest {
            val source = image(Bitmap.CompressFormat.PNG)
            val destination = File.createTempFile("gallery-output", ".jpg", context.cacheDir)
            try {
                val photo = importGalleryEvidence(context.contentResolver, Uri.fromFile(source), destination)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(photo.file.path, bounds)
                assertEquals("image/jpeg", bounds.outMimeType)
                assertEquals(16, bounds.outWidth)
                assertNull(photo.coordinates)
                assertNull(photo.capturedAt)
            } finally {
                source.delete()
                destination.delete()
            }
        }

    @Test
    fun preservesExifGpsAndInterpretsTheOriginalTimeWithItsOffset() =
        runTest {
            val source = image(Bitmap.CompressFormat.JPEG)
            val destination = File.createTempFile("gallery-output", ".jpg", context.cacheDir)
            try {
                ExifInterface(source).apply {
                    setLatLong(10.0, 106.0)
                    setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:16 10:00:00")
                    setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+07:00")
                    saveAttributes()
                }
                val photo = importGalleryEvidence(context.contentResolver, Uri.fromFile(source), destination)
                assertEquals(10.0 to 106.0, photo.coordinates)
                assertEquals(Instant.parse("2026-11-16T03:00:00Z"), photo.capturedAt)
            } finally {
                source.delete()
                destination.delete()
            }
        }

    private fun image(format: Bitmap.CompressFormat): File {
        val file = File.createTempFile("gallery-input", ".img", context.cacheDir)
        val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(format, 90, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
