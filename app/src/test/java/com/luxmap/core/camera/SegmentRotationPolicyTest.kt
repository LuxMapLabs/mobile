package com.luxmap.core.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentRotationPolicyTest {
    private val policy = SegmentRotationPolicy(targetDurationMs = 180_000L) // 3 minutes

    @Test
    fun `does not rotate before target duration even at a keyframe`() {
        assertFalse(policy.shouldRotate(currentSegmentDurationMs = 60_000L, isKeyFrame = true))
    }

    @Test
    fun `does not rotate at target duration if not a keyframe`() {
        assertFalse(policy.shouldRotate(currentSegmentDurationMs = 180_000L, isKeyFrame = false))
    }

    @Test
    fun `rotates once duration has passed target and frame is a keyframe`() {
        assertTrue(policy.shouldRotate(currentSegmentDurationMs = 181_000L, isKeyFrame = true))
    }
}
