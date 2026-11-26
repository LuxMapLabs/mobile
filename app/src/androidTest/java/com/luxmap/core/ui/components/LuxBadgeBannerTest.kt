package com.luxmap.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LuxBadgeBannerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun a_status_badge_is_at_least_28dp_tall() {
        composeRule.setContent {
            LuxMapTheme {
                StatusBadge(text = "Đã đồng bộ", colors = SyncStatus.DONE.badgeColors(), icon = Icons.Rounded.CloudDone)
            }
        }
        composeRule.onNodeWithText("Đã đồng bộ").assertHeightIsAtLeast(28.dp)
    }

    @Test
    fun a_banner_shows_its_title_and_message_and_fires_its_action() {
        var acted = false
        composeRule.setContent {
            LuxMapTheme {
                LuxBanner(
                    tone = LuxBannerTone.INFO,
                    title = "Dữ liệu đã lưu",
                    message = "Cập nhật lúc 21:45",
                    actionLabel = "Tải lại",
                    onAction = { acted = true },
                )
            }
        }
        composeRule.onNodeWithText("Dữ liệu đã lưu", substring = true).assertExists()
        composeRule.onNodeWithText("Cập nhật lúc 21:45", substring = true).assertExists()
        composeRule.onNodeWithText("Tải lại").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(acted)
    }

    @Test
    fun an_error_banner_is_announced_assertively() {
        composeRule.setContent {
            LuxMapTheme { LuxBanner(tone = LuxBannerTone.ERROR, message = "Không nộp được báo cáo") }
        }
        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assertExists()
    }

    @Test
    fun a_non_error_banner_is_announced_politely() {
        composeRule.setContent {
            LuxMapTheme { LuxBanner(tone = LuxBannerTone.WARNING, message = "Hạn xử lý hôm nay") }
        }
        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
    }
}
