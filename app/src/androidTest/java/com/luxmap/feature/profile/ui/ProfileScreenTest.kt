package com.luxmap.feature.profile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.profile.data.CurrentUserProfile
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private val SAMPLE_PROFILE =
    CurrentUserProfile(
        fullName = "Nguyễn Văn Bình",
        email = "binh.nv@luxmap.vn",
        role = "field_engineer",
        communeIds = listOf("COM-001", "COM-002"),
    )

class ProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setContent(
        profileState: ProfileUiState = ProfileUiState.Success(SAMPLE_PROFILE),
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
                    profileState = profileState,
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
    fun loading_state_shows_the_progress_indicator() {
        setContent(profileState = ProfileUiState.Loading)
        composeRule.onNodeWithTag("profile_loading").assertExists()
    }

    @Test
    fun success_state_shows_the_full_name_username_and_email() {
        setContent()
        composeRule.onNodeWithText("Nguyễn Văn Bình").assertExists()
        composeRule.onNodeWithText("@fe_thinh").assertExists()
        composeRule.onNodeWithText("binh.nv@luxmap.vn").assertExists()
    }

    @Test
    fun success_state_shows_the_assigned_communes_and_device_footer() {
        setContent()
        composeRule.onNodeWithText("COM-001, COM-002", substring = true).assertExists()
        composeRule.onNodeWithText("Pixel 7a", substring = true).assertExists()
        composeRule.onNodeWithText("1.0.0", substring = true).assertExists()
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
        composeRule.onNodeWithText("Ngoại tuyến").assertExists()
    }

    @Test
    fun error_state_shows_the_message() {
        setContent(profileState = ProfileUiState.Error("network down"))
        composeRule.onNodeWithText("network down").assertExists()
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
