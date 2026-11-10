package com.luxmap.feature.survey.ui.polephoto

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class GalleryImageCopyTest {
    @Test
    fun `copies the stream's bytes into the destination file and returns true`() =
        runTest {
            val destination = File.createTempFile("gallery-copy-test", ".jpg")
            val input = ByteArrayInputStream(byteArrayOf(1, 2, 3))

            val result = copyGalleryImageToFile(input, destination)

            assertTrue(result)
            assertTrue(destination.readBytes().toList() == listOf<Byte>(1, 2, 3))
        }

    @Test
    fun `returns false without throwing when the stream is null`() =
        runTest {
            val destination = File.createTempFile("gallery-copy-test", ".jpg")

            val result = copyGalleryImageToFile(null, destination)

            assertFalse(result)
        }

    @Test
    fun `returns false without throwing when the stream fails mid-copy`() =
        runTest {
            val destination = File.createTempFile("gallery-copy-test", ".jpg")
            val input =
                object : InputStream() {
                    override fun read(): Int = throw IOException("boom")
                }

            val result = copyGalleryImageToFile(input, destination)

            assertFalse(result)
        }

    @Test
    fun `isJpeg is true for bytes starting with the JPEG signature`() {
        assertTrue(isJpeg(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2)))
    }

    @Test
    fun `isJpeg is false for a PNG signature`() {
        assertFalse(isJpeg(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }

    @Test
    fun `isJpeg is false for bytes shorter than the signature`() {
        assertFalse(isJpeg(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
    }

    @Test
    fun `copyGalleryImageAsJpeg writes already-JPEG bytes through unchanged`() =
        runTest {
            val destination = File.createTempFile("gallery-copy-jpeg-test", ".jpg")
            val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
            val input = ByteArrayInputStream(jpegBytes)

            val result = copyGalleryImageAsJpeg(input, destination)

            assertTrue(result)
            assertTrue(destination.readBytes().toList() == jpegBytes.toList())
        }

    @Test
    fun `copyGalleryImageAsJpeg returns false without throwing when the stream is null`() =
        runTest {
            val destination = File.createTempFile("gallery-copy-jpeg-test", ".jpg")

            val result = copyGalleryImageAsJpeg(null, destination)

            assertFalse(result)
        }
}
