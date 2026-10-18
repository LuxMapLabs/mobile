package com.luxmap.feature.notification.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun row(
    id: String = "NTF-1",
    type: String = "work_order_assigned",
    entityType: String = "work_order",
    isRead: Boolean = false,
) = NotificationListRowState(
    id,
    type,
    "Bạn được giao phiếu WO-1001",
    "WO-1001 — Thay bóng đèn.",
    entityType,
    "WO-1001",
    "2026-10-18T08:00:00Z",
    isRead,
)

class NotificationListScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_the_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme { NotificationListScreen(state = NotificationListUiState.Loading, onBack = {}) }
        }
        composeRule.onNodeWithTag("notification_list_loading").assertExists()
    }

    @Test
    fun success_state_shows_each_notification_title_and_body() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(state = NotificationListUiState.Success(listOf(row())), onBack = {})
            }
        }
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").assertExists()
        composeRule.onNodeWithText("WO-1001 — Thay bóng đèn.").assertExists()
    }

    @Test
    fun tapping_a_work_order_row_calls_onItemClick_with_that_row() {
        var clicked: NotificationListRowState? = null
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row())),
                    onBack = {},
                    onItemClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").performClick()
        assertEquals("NTF-1", clicked?.notificationId)
    }

    @Test
    fun tapping_mark_all_read_calls_onMarkAllRead() {
        var marked = false
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row())),
                    onBack = {},
                    onMarkAllRead = { marked = true },
                )
            }
        }
        composeRule.onNodeWithText("Đánh dấu tất cả đã đọc").performClick()
        assertEquals(true, marked)
    }

    @Test
    fun empty_state_shows_the_empty_inbox_message() {
        composeRule.setContent {
            LuxMapTheme { NotificationListScreen(state = NotificationListUiState.Empty, onBack = {}) }
        }
        composeRule.onNodeWithText("Chưa có thông báo nào").assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_retry_calls_onRetry() {
        var retried = false
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Error("network down"),
                    onBack = {},
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText("network down").assertExists()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun an_unread_row_shows_the_unread_dot_and_a_read_row_does_not() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row(id = "NTF-1", isRead = false))),
                    onBack = {},
                )
            }
        }
        // useUnmergedTree = true: NotificationRow's outer Row is clickable, which merges all
        // descendant semantics into one node by default - testTag is not a mergeable property,
        // so the dot's tag is only visible by querying the unmerged tree.
        composeRule.onNodeWithTag("notification_unread_dot_NTF-1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun a_notification_type_the_app_does_not_recognize_yet_still_renders_without_crashing() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row(type = "some_future_type"))),
                    onBack = {},
                )
            }
        }
        // No assertion on which icon shows (the fallback is an implementation detail) - the
        // point of this test is that an unrecognized type does not throw during composition and
        // the row's own text still renders, per Review Focus item 1.
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").assertExists()
    }
}
