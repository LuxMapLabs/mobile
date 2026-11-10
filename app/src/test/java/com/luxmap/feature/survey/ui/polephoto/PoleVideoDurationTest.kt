package com.luxmap.feature.survey.ui.polephoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoleVideoDurationTest {
    @Test
    fun `a clip just under 3 seconds is invalid`() {
        assertFalse(isValidPoleVideoDuration(2_999))
    }

    @Test
    fun `a clip at exactly 3 seconds is valid`() {
        assertTrue(isValidPoleVideoDuration(3_000))
    }

    @Test
    fun `a clip at exactly 30_5 seconds is valid`() {
        assertTrue(isValidPoleVideoDuration(30_500))
    }

    @Test
    fun `a clip just over 30_5 seconds is invalid`() {
        assertFalse(isValidPoleVideoDuration(30_501))
    }

    @Test
    fun `a zero-byte file is invalid`() {
        assertFalse(isValidPoleVideoSize(0))
    }

    @Test
    fun `a 1-byte file is valid`() {
        assertTrue(isValidPoleVideoSize(1))
    }

    @Test
    fun `a file at exactly 100 MiB is valid`() {
        assertTrue(isValidPoleVideoSize(100L * 1024 * 1024))
    }

    @Test
    fun `a file one byte over 100 MiB is invalid`() {
        assertFalse(isValidPoleVideoSize(100L * 1024 * 1024 + 1))
    }
}
