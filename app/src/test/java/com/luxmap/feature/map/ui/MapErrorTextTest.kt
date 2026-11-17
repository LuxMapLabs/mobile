package com.luxmap.feature.map.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MapErrorTextTest {
    @Test
    fun `network errors ask to check the connection`() {
        assertEquals(
            "Không tải được dữ liệu bản đồ. Kiểm tra kết nối rồi thử lại.",
            mapErrorText(isNetworkError = true),
        )
    }

    @Test
    fun `other errors ask to try again later`() {
        assertEquals(
            "Không tải được dữ liệu bản đồ. Vui lòng thử lại sau ít phút.",
            mapErrorText(isNetworkError = false),
        )
    }
}
