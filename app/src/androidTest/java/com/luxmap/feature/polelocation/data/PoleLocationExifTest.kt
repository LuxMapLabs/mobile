package com.luxmap.feature.polelocation.data

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PoleLocationExifTest {
    @get:Rule val tmp = TemporaryFolder()
    private val exif = FileExifPoleLocation()

    private fun blankJpeg(): File {
        val file = tmp.newFile("pole.jpg")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        return file
    }

    @Test
    fun write_then_read_round_trips_the_coordinates() {
        val file = blankJpeg()

        exif.write(file, 10.123456, 106.654321)
        val result = exif.read(file)

        assertEquals(10.123456, result!!.first, 0.0001)
        assertEquals(106.654321, result.second, 0.0001)
    }

    @Test
    fun read_without_a_prior_write_is_null() {
        val file = blankJpeg()

        assertNull(exif.read(file))
    }
}
