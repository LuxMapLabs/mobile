package com.luxmap.feature.map.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapTapPickingTest {
    @Test
    fun `no candidate picks nothing`() {
        assertNull(pickNearestFeature(10f, 10f, emptyList()))
    }

    @Test
    fun `the candidate nearest the tap wins`() {
        val picked =
            pickNearestFeature(
                100f,
                100f,
                listOf(
                    TapCandidate("FAR", 130f, 100f),
                    TapCandidate("NEAR", 104f, 97f),
                    TapCandidate("MID", 90f, 112f),
                ),
            )
        assertEquals("NEAR", picked)
    }

    @Test
    fun `equal distances pick the smallest id so the result is stable`() {
        assertEquals("A", pickNearestFeature(0f, 0f, listOf(TapCandidate("B", 3f, 4f), TapCandidate("A", -3f, -4f))))
    }
}
