package com.luxmap.feature.notification.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import org.junit.Rule
import org.junit.Test

class NotificationLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show() {
        val row =
            NotificationListRowState(
                "NTF-1",
                "work_order_assigned",
                "Bạn được giao phiếu sửa chữa tuyến đường liên xã Phước Hiệp",
                "WO-1042 — Thay bóng đèn và kiểm tra tủ điện đoạn qua chợ.",
                "work_order",
                "WO-1042",
                "2026-10-18T08:00:00Z",
                isRead = false,
            )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        NotificationListScreen(state = NotificationListUiState.Success(listOf(row)), onBack = {})
                    }
                }
            }
        }
    }

    @Test
    fun top_bar_action_filter_and_row_keep_48dp_targets_at_large_font() {
        show()
        composeRule.onNodeWithText("Đọc tất cả").assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNodeWithText("Tất cả · 1").assertTouchTargetIsAtLeast(48.dp)
        composeRule
            .onNode(hasClickAction() and hasText("Bạn được giao phiếu", substring = true))
            .assertTouchTargetIsAtLeast(48.dp)
    }
}
