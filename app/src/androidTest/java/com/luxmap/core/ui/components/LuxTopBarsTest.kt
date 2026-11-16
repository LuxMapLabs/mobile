package com.luxmap.core.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LuxTopBarsTest {
    @get:Rule val composeRule = createComposeRule()

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun SemanticsNodeInteraction.assertTouchTargetIsAtLeast(minimum: Dp): SemanticsNodeInteraction {
        val node = fetchSemanticsNode()
        val density = node.layoutInfo.density.density
        val widthDp = node.touchBoundsInRoot.width / density
        val heightDp = node.touchBoundsInRoot.height / density
        assertTrue("touch target was ${widthDp}x$heightDp dp", widthDp >= minimum.value - 0.5f)
        assertTrue("touch target was ${widthDp}x$heightDp dp", heightDp >= minimum.value - 0.5f)
        return this
    }

    @Test
    fun the_tab_top_bar_title_is_a_heading_and_the_bell_reads_the_unread_count() {
        var opened = false
        composeRule.setContent {
            LuxMapTheme {
                LuxTabTopBar(
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
            LuxMapTheme { LuxTabTopBar(title = "Khảo sát", unreadNotificationCount = 0, onNotificationsClick = {}) }
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
