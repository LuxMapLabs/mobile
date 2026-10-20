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
        bleConnectionState: BleConnectionState = BleConnectionState.Connected,
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
    fun success_state_shows_a_chip_per_assigned_commune() {
        setContent()
        composeRule.onNodeWithText("COM-001").assertExists()
        composeRule.onNodeWithText("COM-002").assertExists()
        composeRule.onNodeWithText("Địa bàn phụ trách · 2", substring = true).assertExists()
    }

    @Test
    fun success_state_shows_the_device_model_and_version_in_the_footer() {
        setContent()
        composeRule.onNodeWithText("Pixel 7a", substring = true).assertExists()
        composeRule.onNodeWithText("1.0.0", substring = true).assertExists()
    }

    @Test
    fun all_ready_shows_san_sang_label() {
        setContent(isOnline = true, errorSyncCount = 0, bleConnectionState = BleConnectionState.Connected)
        composeRule.onNodeWithText("Sẵn sàng").assertExists()
    }

    @Test
    fun offline_and_a_sync_error_shows_2_items_need_attention() {
        setContent(isOnline = false, errorSyncCount = 1, bleConnectionState = BleConnectionState.Connected)
        composeRule.onNodeWithText("2 mục cần kiểm tra").assertExists()
    }

    @Test
    fun shows_offline_when_not_online() {
        setContent(isOnline = false)
        composeRule.onNodeWithText("Ngoại tuyến").assertExists()
    }

    @Test
    fun shows_chua_cap_nhat_when_never_synced() {
        setContent(lastSyncedAt = null)
        composeRule.onNodeWithText("Chưa cập nhật", substring = true).assertExists()
    }

    @Test
    fun hides_the_pending_count_caption_when_nothing_is_pending() {
        setContent(pendingSyncCount = 0)
        composeRule.onNodeWithText("chưa đồng bộ trên thiết bị này", substring = true).assertDoesNotExist()
    }

    @Test
    fun shows_the_pending_count_caption_when_something_is_pending() {
        setContent(pendingSyncCount = 3)
        composeRule.onNodeWithText("Còn 3 mục chưa đồng bộ trên thiết bị này").assertExists()
    }

    @Test
    fun shows_chua_ket_noi_when_ble_is_disconnected() {
        setContent(bleConnectionState = BleConnectionState.Disconnected)
        composeRule.onNodeWithText("Chưa kết nối").assertExists()
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
