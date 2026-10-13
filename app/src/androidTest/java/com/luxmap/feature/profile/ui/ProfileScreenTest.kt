package com.luxmap.feature.profile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setContent(
        isOnline: Boolean = true,
        pendingSyncCount: Int = 0,
        errorSyncCount: Int = 0,
        lastSyncedAt: Instant? = null,
        bleConnectionState: BleConnectionState = BleConnectionState.Disconnected,
        onConfirmLogout: () -> Unit = {},
    ) {
        composeRule.setContent {
            LuxMapTheme {
                ProfileScreen(
                    username = "fe_thinh",
                    isLoggingOut = false,
                    deviceModel = "Pixel 7a",
                    appVersion = "1.0.0",
                    bleConnectionState = bleConnectionState,
                    pendingSyncCount = pendingSyncCount,
                    errorSyncCount = errorSyncCount,
                    lastSyncedAt = lastSyncedAt,
                    isOnline = isOnline,
                    onConfirmLogout = onConfirmLogout,
                )
            }
        }
    }

    @Test
    fun shows_the_username_and_device_model() {
        setContent()
        composeRule.onNodeWithText("fe_thinh").assertExists()
        composeRule.onNodeWithText("Pixel 7a").assertExists()
    }

    @Test
    fun shows_an_error_badge_only_when_there_are_sync_errors() {
        setContent(errorSyncCount = 0)
        composeRule.onNodeWithText("0 lỗi", substring = true).assertDoesNotExist()
    }

    @Test
    fun shows_the_error_count_when_there_are_sync_errors() {
        setContent(errorSyncCount = 2)
        composeRule.onNodeWithText("2 lỗi").assertExists()
    }

    @Test
    fun shows_chua_cap_nhat_when_never_synced() {
        setContent(lastSyncedAt = null)
        composeRule.onNodeWithText("Chưa cập nhật").assertExists()
    }

    @Test
    fun shows_offline_when_not_online() {
        setContent(isOnline = false)
        composeRule.onNodeWithText("Offline").assertExists()
    }

    @Test
    fun the_notification_row_says_not_supported_in_this_version() {
        setContent()
        composeRule.onNodeWithText("Chưa hỗ trợ trong phiên bản này", substring = true).assertExists()
    }

    @Test
    fun confirming_logout_still_invokes_onConfirmLogout() {
        var loggedOut = false
        setContent(onConfirmLogout = { loggedOut = true })
        composeRule.onNodeWithText("Đăng xuất").performClick()
        composeRule.onNodeWithText("Đăng xuất khỏi thiết bị này?").assertExists()
        composeRule.onAllNodesWithText("Đăng xuất")[1].performClick()
        assertTrue(loggedOut)
    }
}
