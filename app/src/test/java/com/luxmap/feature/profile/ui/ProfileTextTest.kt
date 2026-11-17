package com.luxmap.feature.profile.ui

import com.luxmap.core.common.DateFormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ProfileTextTest {
    private val at = Instant.parse("2026-11-17T03:24:00Z")
    private val last = "Lần cuối ${DateFormatUtils.formatInstant(at)}"

    @Test
    fun `pending and errors with a last sync`() {
        assertEquals("Chờ 2\nLỗi 1\n$last", profileSyncSummary(2, 1, at))
    }

    @Test
    fun `zero counts are left out`() {
        assertEquals("Chờ 2\n$last", profileSyncSummary(2, 0, at))
        assertEquals("Lỗi 1\n$last", profileSyncSummary(0, 1, at))
    }

    @Test
    fun `nothing left means synced`() {
        assertEquals("Đã đồng bộ\n$last", profileSyncSummary(0, 0, at))
    }

    @Test
    fun `never synced`() {
        assertEquals("Chưa đồng bộ lần nào", profileSyncSummary(0, 0, null))
        assertEquals("Chờ 3", profileSyncSummary(3, 0, null))
    }
}
