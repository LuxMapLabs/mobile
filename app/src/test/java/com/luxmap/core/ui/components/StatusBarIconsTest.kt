package com.luxmap.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarIconsTest {
    private val applied = mutableListOf<Boolean>()
    private val counter = StatusBarIconsCounter { applied += it }

    @Test
    fun `first acquire switches to light icons`() {
        counter.acquire()
        assertEquals(listOf(true), applied)
    }

    @Test
    fun `nested acquire does not apply twice`() {
        counter.acquire()
        counter.acquire()
        assertEquals(listOf(true), applied)
    }

    @Test
    fun `icons stay light until the last owner releases`() {
        counter.acquire()
        counter.acquire()
        counter.release()
        assertEquals(listOf(true), applied)
        counter.release()
        assertEquals(listOf(true, false), applied)
    }

    @Test
    fun `release without acquire is ignored`() {
        counter.release()
        assertEquals(emptyList<Boolean>(), applied)
    }
}
