package com.luxmap.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LuxContentTest {
    @get:Rule val composeRule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun a_clickable_card_is_a_button_and_fires() {
        var tapped = false
        composeRule.setContent {
            LuxMapTheme { LuxCard(onClick = { tapped = true }) { Text("Thông tin nhanh") } }
        }
        composeRule.onNodeWithText("Thông tin nhanh").assert(isButton).performClick()
        assertTrue(tapped)
    }

    @Test
    fun a_clickable_list_item_is_a_full_size_button() {
        var tapped = false
        composeRule.setContent {
            LuxMapTheme {
                LuxListItem(
                    overline = "Sửa chữa · WO-1042",
                    headline = "Tuyến ĐH-12",
                    supporting = "Hạn hôm nay",
                    leadingIcon = Icons.Filled.Build,
                    onClick = { tapped = true },
                )
            }
        }
        composeRule
            .onNodeWithText("Tuyến ĐH-12", substring = true)
            .assert(isButton)
            .assertHasClickAction()
            .assertHeightIsAtLeast(88.dp)
            .performClick()
        assertTrue(tapped)
    }

    @Test
    fun a_section_header_is_a_heading_with_an_optional_action() {
        var acted = false
        composeRule.setContent {
            LuxMapTheme {
                LuxSectionHeader(
                    title = "Sự cố cần sửa",
                    actionLabel = "Xem tất cả",
                    onAction = { acted = true },
                )
            }
        }
        composeRule.onNodeWithText("Sự cố cần sửa").assert(isHeading)
        composeRule.onNodeWithText("Xem tất cả").performClick()
        assertTrue(acted)
    }
}
