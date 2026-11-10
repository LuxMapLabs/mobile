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
}
