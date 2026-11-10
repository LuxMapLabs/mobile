package com.luxmap.feature.survey.ui.polephoto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PoleVideoReadDurationTest {
    @Test
    fun readVideoDurationMs_returns_0_for_a_file_the_retriever_cannot_read() {
        val garbage = File.createTempFile("not-a-video", ".mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val durationMs = readVideoDurationMs(garbage)

        assertEquals(0L, durationMs)
    }
}
