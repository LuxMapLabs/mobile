package com.luxmap.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LuxOverlaysTest {
    @get:Rule val composeRule = createComposeRule()

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun a_confirm_dialog_names_its_action_and_reports_each_choice() {
        var confirmed = 0
        var dismissed = 0
        composeRule.setContent {
            LuxMapTheme {
                LuxConfirmDialog(
                    title = "Đăng xuất khỏi thiết bị này?",
                    message = "Còn 3 mục chưa đồng bộ sẽ bị xoá.",
                    confirmLabel = "Đăng xuất",
                    destructive = true,
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.onNodeWithText("Đăng xuất khỏi thiết bị này?").assert(isHeading)
        composeRule.onNodeWithText("Còn 3 mục chưa đồng bộ sẽ bị xoá.").assertExists()
        composeRule.onNodeWithText("Huỷ").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Đăng xuất").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, dismissed)
        assertEquals(1, confirmed)
    }

    @Test
    fun a_bottom_sheet_has_a_heading_and_a_close_button_that_dismisses_it() {
        var dismissed = false
        composeRule.setContent {
            LuxMapTheme {
                var open by remember { mutableStateOf(true) }
                if (open) {
                    LuxBottomSheet(
                        title = "Yêu cầu hủy lệnh",
                        onDismiss = {
                            open = false
                            dismissed = true
                        },
                    ) {
                        Text("Chọn lý do")
                    }
                }
            }
        }
        composeRule.onNodeWithText("Yêu cầu hủy lệnh").assert(isHeading)
        composeRule.onNodeWithText("Chọn lý do").assertExists()
        composeRule.onNodeWithContentDescription("Đóng").assertTouchTargetIsAtLeast(48.dp).performClick()
        composeRule.waitForIdle()
        assertTrue(dismissed)
        composeRule.onNodeWithText("Chọn lý do").assertDoesNotExist()
    }
}
