package com.luxmap.feature.home.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeGreetingTest {
    @Test
    fun `full name wins over identifier and missing full name falls back`() {
        val today = LocalDate.parse("2026-11-16")
        assertEquals(
            "Xin chào, Nguyễn Văn Bình\nThứ Hai, 16/11/2026",
            homeGreeting("crew", today, "Nguyễn Văn Bình"),
        )
        for (name in listOf(null, "", " ")) {
            assertEquals("Xin chào, crew\nThứ Hai, 16/11/2026", homeGreeting("crew", today, name))
        }
        assertEquals("Xin chào\nThứ Hai, 16/11/2026", homeGreeting(null, today, null))
    }

    @Test
    fun `greeting has the name and the capitalized vietnamese date`() {
        assertEquals("Xin chào, kysu1\nThứ Hai, 16/11/2026", homeGreeting("kysu1", LocalDate.parse("2026-11-16")))
    }

    @Test
    fun `a blank name is left out`() {
        assertEquals("Xin chào\nThứ Hai, 16/11/2026", homeGreeting(" ", LocalDate.parse("2026-11-16")))
    }
}
