package com.luxmap.feature.home.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeGreetingTest {
    @Test
    fun `greeting has the name and the capitalized vietnamese date`() {
        assertEquals("Xin chào, kysu1 · Thứ Hai, 16/11/2026", homeGreeting("kysu1", LocalDate.parse("2026-11-16")))
    }

    @Test
    fun `a blank name is left out`() {
        assertEquals("Xin chào · Thứ Hai, 16/11/2026", homeGreeting(" ", LocalDate.parse("2026-11-16")))
    }
}
