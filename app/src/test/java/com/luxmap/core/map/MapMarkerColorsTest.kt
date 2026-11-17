package com.luxmap.core.map

import androidx.compose.ui.graphics.Color
import com.luxmap.core.theme.AssetCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class MapMarkerColorsTest {
    @Test
    fun `marker fills match the web map`() {
        assertEquals(Color(0xFF10B981), AssetCondition.NORMAL.markerFill())
        assertEquals(Color(0xFFF59E0B), AssetCondition.DIM.markerFill())
        assertEquals(Color(0xFFF43F5E), AssetCondition.OUT.markerFill())
        assertEquals(Color(0xFF64748B), AssetCondition.UNKNOWN.markerFill())
    }

    @Test
    fun `the legacy marker color reads the same source`() {
        AssetCondition.entries.forEach { assertEquals(it.markerFill(), it.markerColor()) }
    }
}
