package com.luxmap.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusLabelTest {
    @Test
    fun `every SyncStatus has the exact label from CLAUDE_md's sync badge table`() {
        assertEquals("Chờ mạng", SyncStatus.QUEUED_OFFLINE.label())
        assertEquals("Chờ đồng bộ", SyncStatus.QUEUED_ONLINE.label())
        assertEquals("Đang đồng bộ", SyncStatus.SYNCING.label())
        assertEquals("Đồng bộ lỗi", SyncStatus.FAILED.label())
        assertEquals("Xung đột", SyncStatus.CONFLICT.label())
        assertEquals("Đã đồng bộ", SyncStatus.DONE.label())
    }
}
