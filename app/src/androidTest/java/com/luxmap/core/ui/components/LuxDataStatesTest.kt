package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
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

class LuxDataStatesTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun an_empty_state_shows_its_copy_and_fires_its_optional_action() {
        var acted = false
        composeRule.setContent {
            LuxMapTheme {
                LuxEmptyState(
                    title = "Không có thông báo",
                    message = "Thông báo mới sẽ hiện ở đây.",
                    actionLabel = "Bỏ lọc",
                    onAction = { acted = true },
                )
            }
        }
        composeRule.onNodeWithText("Không có thông báo", substring = true).assertExists()
        composeRule.onNodeWithText("Thông báo mới sẽ hiện ở đây.", substring = true).assertExists()
        composeRule.onNodeWithText("Bỏ lọc").performClick()
        assertTrue(acted)
    }

    @Test
    fun an_error_state_always_offers_a_full_size_retry() {
        var retries = 0
        composeRule.setContent { LuxMapTheme { LuxErrorState(onRetry = { retries++ }) } }
        composeRule.onNodeWithText("Thử lại").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun a_network_error_explains_that_saved_data_is_safe() {
        composeRule.setContent { LuxMapTheme { LuxErrorState(onRetry = {}, kind = LuxErrorKind.NETWORK) } }
        composeRule.onNodeWithText("Không có kết nối mạng", substring = true).assertExists()
        composeRule.onNodeWithText("vẫn an toàn", substring = true).assertExists()
    }

    @Test
    fun an_error_state_shows_the_secondary_action_when_given() {
        var backed = false
        composeRule.setContent {
            LuxMapTheme {
                LuxErrorState(onRetry = {}, secondaryActionLabel = "Quay lại", onSecondaryAction = { backed = true })
            }
        }
        composeRule.onNodeWithText("Quay lại").performClick()
        assertTrue(backed)
    }

    @Test
    fun a_loading_region_is_announced_once_by_its_description() {
        composeRule.setContent {
            LuxMapTheme {
                LuxLoadingRegion(description = "Đang tải danh sách lệnh") {
                    LuxSkeleton(modifier = Modifier.size(40.dp))
                    LuxSkeleton(modifier = Modifier.size(40.dp))
                }
            }
        }
        composeRule.onNodeWithContentDescription("Đang tải danh sách lệnh").assertExists()
    }
}
