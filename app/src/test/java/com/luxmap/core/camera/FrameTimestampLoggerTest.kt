package com.luxmap.core.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTimestampLoggerTest {
    private val logger = FrameTimestampLogger()

    @Test
    fun `builds an entry carrying the segment index alongside both timestamp sources`() {
        val entry =
            logger.buildEntry(
                frameIndex = 42,
                sensorTimestampNs = 123_456_789L,
                videoPtsUs = 5_000_000L,
                segment = 1,
            )

        assertEquals(42, entry.frameIndex)
        assertEquals(123_456_789L, entry.sensorTimestampNs)
        assertEquals(5_000_000L, entry.videoPtsUs)
        assertEquals(1, entry.segment)
    }
}
