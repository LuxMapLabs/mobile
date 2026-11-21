package com.luxmap.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CommuneLabelTest {
    @Test
    fun `commune name is preferred with id fallback for missing or blank names`() {
        assertEquals("Phước Hiệp", communeLabel("COM-002", "Phước Hiệp"))
        for (name in listOf(null, "", " ")) {
            assertEquals("COM-002", communeLabel("COM-002", name))
        }
    }
}
