package com.luxmap.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Rule
import org.junit.Test

class RequirementListTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun renders_each_requirement_label_in_order() {
        composeRule.setContent {
            LuxMapTheme {
                RequirementList(
                    items =
                        listOf(
                            Requirement("Có ảnh sau sửa chữa", met = true),
                            Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = false),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Có ảnh sau sửa chữa").assertExists()
        composeRule.onNodeWithText("Đã ghi chú kết quả (≥ 10 ký tự)").assertExists()
    }
}
