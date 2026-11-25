package com.luxmap.core.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LuxTopBarsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun a_single_digit_unread_badge_is_a_circle() {
        composeRule.setContent {
            LuxMapTheme {
                LuxTabHero(title = "Việc hôm nay", unreadNotificationCount = 3, onNotificationsClick = {})
            }
        }
        val badge = composeRule.onNodeWithTag("notification_badge", useUnmergedTree = true).fetchSemanticsNode()
        val density = badge.layoutInfo.density.density
        val widthDp = badge.size.width / density
        val heightDp = badge.size.height / density
        assertTrue("badge was ${widthDp}x${heightDp}dp", kotlin.math.abs(widthDp - heightDp) < 1f)
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun the_tab_hero_title_is_a_heading_and_the_bell_reads_the_unread_count() {
        var opened = false
        composeRule.setContent {
            LuxMapTheme {
                LuxTabHero(
                    title = "Việc hôm nay",
                    unreadNotificationCount = 3,
                    onNotificationsClick = { opened = true },
                )
            }
        }
        composeRule.onNodeWithText("Việc hôm nay").assert(isHeading)
        composeRule
            .onNodeWithContentDescription("Thông báo, 3 chưa đọc")
            .assertTouchTargetIsAtLeast(48.dp)
            .performClick()
        assertTrue(opened)
    }

    @Test
    fun the_bell_has_a_plain_label_when_nothing_is_unread() {
        composeRule.setContent {
            LuxMapTheme { LuxTabHero(title = "Khảo sát", unreadNotificationCount = 0, onNotificationsClick = {}) }
        }
        composeRule.onNodeWithContentDescription("Thông báo").assertExists()
    }

    @Test
    fun the_child_top_bar_goes_back_and_shows_its_subtitle() {
        var backed = false
        composeRule.setContent {
            LuxMapTheme { LuxChildTopBar(title = "Lệnh sửa chữa", subtitle = "WO-1042", onBack = { backed = true }) }
        }
        composeRule.onNodeWithText("WO-1042").assertExists()
        composeRule.onNodeWithContentDescription("Quay lại").assertTouchTargetIsAtLeast(48.dp).performClick()
        assertTrue(backed)
    }
}
