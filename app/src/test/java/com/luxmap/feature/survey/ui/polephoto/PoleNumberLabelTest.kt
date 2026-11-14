package com.luxmap.feature.survey.ui.polephoto

import org.junit.Assert.assertEquals
import org.junit.Test

class PoleNumberLabelTest {
    @Test
    fun `placed pole shows its number`() {
        assertEquals("Cột 3", poleNumberLabel(3))
    }

    @Test
    fun `unplaced pole shows no GPS yet`() {
        assertEquals("Chưa có GPS", poleNumberLabel(null))
    }
}
