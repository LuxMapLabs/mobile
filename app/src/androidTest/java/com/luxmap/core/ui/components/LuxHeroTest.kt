package com.luxmap.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LuxHeroTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabHeroShowsTitleSubtitleAndContentSlot() {
        composeRule.setContent {
            LuxMapTheme {
                LuxTabHero(
                    title = "Việc hôm nay",
                    subtitle = "Thứ Tư, 25/11/2026",
                    unreadNotificationCount = 0,
                    onNotificationsClick = {},
                ) {
                    LuxStatTile(label = "Tổng", value = 4)
                }
            }
        }
        composeRule.onNodeWithText("Việc hôm nay").assertIsDisplayed()
        composeRule.onNodeWithText("Thứ Tư, 25/11/2026").assertIsDisplayed()
        composeRule.onNodeWithText("Tổng").assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
    }

    @Test
    fun tabHeroBellReportsUnreadCountAndHandlesClick() {
        var clicked = false
        composeRule.setContent {
            LuxMapTheme {
                LuxTabHero(
                    title = "Khảo sát",
                    unreadNotificationCount = 3,
                    onNotificationsClick = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Thông báo, 3 chưa đọc").performClick()
        assertTrue(clicked)
    }

    @Test
    fun statTileShowsLabelAndValue() {
        composeRule.setContent {
            LuxMapTheme { LuxStatTile(label = "Quá hạn", value = 2) }
        }
        composeRule.onNodeWithText("Quá hạn").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
    }
}
