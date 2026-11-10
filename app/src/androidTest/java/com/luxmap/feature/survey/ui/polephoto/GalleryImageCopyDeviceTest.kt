package com.luxmap.feature.survey.ui.polephoto

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

// A real, valid 1x1 pixel PNG - BitmapFactory needs genuinely decodable image bytes, not just a
// PNG-looking header, so a hand-rolled byte array would not exercise the real decode path.
private const val ONE_PIXEL_PNG_BASE64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg=="

@RunWith(AndroidJUnit4::class)
class GalleryImageCopyDeviceTest {
    @Test
    fun copyGalleryImageAsJpeg_reencodes_a_PNG_into_real_JPEG_bytes() =
        runTest {
            val pngBytes = Base64.decode(ONE_PIXEL_PNG_BASE64, Base64.DEFAULT)
            val destination = File.createTempFile("gallery-copy-png-test", ".jpg")

            val result = copyGalleryImageAsJpeg(ByteArrayInputStream(pngBytes), destination)

            assertTrue(result)
            assertTrue(isJpeg(destination.readBytes()))
        }
}
