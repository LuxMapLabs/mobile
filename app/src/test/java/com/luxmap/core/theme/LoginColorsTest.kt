package com.luxmap.core.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class LoginColorsTest {
    @Test
    fun `brand header text and connectivity labels reach AAA in both themes`() {
        val ratio = contrastRatio(LoginColors.onHeader, LoginColors.headerContainer)
        assertTrue("Login header contrast was $ratio, needs 7.0", ratio >= 7.0)
    }
}
