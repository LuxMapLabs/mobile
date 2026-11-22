package com.luxmap.feature.workorder.ui.detail.sections

import com.luxmap.feature.survey.data.PoleListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderPolesOrderTest {
    private fun pole(id: String) = PoleListItem(id, null, null, null, "normal")

    @Test
    fun `poles holding the order's faults come first, each group keeps API order`() {
        val poles = listOf(pole("P-1"), pole("P-2"), pole("P-3"), pole("P-4"))
        assertEquals(
            listOf("P-1", "P-3", "P-2", "P-4"),
            poles.faultPolesFirst(setOf("P-3", "P-1")).map { it.poleId },
        )
    }

    @Test
    fun `no fault keeps API order`() {
        val poles = listOf(pole("P-2"), pole("P-1"))
        assertEquals(listOf("P-2", "P-1"), poles.faultPolesFirst(emptySet()).map { it.poleId })
    }
}
