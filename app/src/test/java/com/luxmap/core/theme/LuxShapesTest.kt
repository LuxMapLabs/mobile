package com.luxmap.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LuxShapesTest {
    @Test
    fun `shape scale follows the approved vNext radii`() {
        assertEquals(RoundedCornerShape(6.dp), LuxShapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), LuxShapes.small)
        assertEquals(RoundedCornerShape(12.dp), LuxShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), LuxShapes.large)
        assertEquals(RoundedCornerShape(24.dp), LuxShapes.extraLarge)
    }
}
