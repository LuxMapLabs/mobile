package com.luxmap.feature.profile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.profile.data.CurrentUserProfile
import org.junit.Assert.assertEquals
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
        onConfirmLogout: () -> Unit = {},
        unread: Int = 0,
        onOpenNotifications: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            LuxMapTheme {
                ProfileScreen(
                    username = "fe_thinh",
                    profileState = profileState,
                    isLoggingOut = false,
                    deviceModel = "Pixel 7a",
                    appVersion = "1.0.0",
                    pendingSyncCount = pendingSyncCount,
                    errorSyncCount = errorSyncCount,
                    lastSyncedAt = lastSyncedAt,
                    isOnline = isOnline,
                    onConfirmLogout = onConfirmLogout,
                    unreadNotificationCount = unread,
                    onOpenNotifications = onOpenNotifications,
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun loading_is_one_announced_region() {
        setContent(profileState = ProfileUiState.Loading)
        composeRule.onNodeWithContentDescription("Đang tải thông tin cá nhân").assertExists()
    }

    @Test
    fun success_state_shows_the_full_name_username_and_email() {
        setContent()
        composeRule.onNodeWithText("Nguyễn Văn Bình").assertExists()
        composeRule.onNodeWithText("@fe_thinh", substring = true).assertExists()
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
    fun the_status_card_shows_network_and_a_real_sync_summary() {
        setContent(isOnline = false, pendingSyncCount = 2, errorSyncCount = 1, lastSyncedAt = null)
        composeRule.onNodeWithText("Tình trạng làm việc").assertExists()
        composeRule.onNodeWithText("Ngoại tuyến").assertExists()
        composeRule.onNodeWithText("Chờ 2 · Lỗi 1").assertExists()
        composeRule.onNodeWithText("Sẵn sàng").assertDoesNotExist()
        composeRule.onNodeWithText("mục cần kiểm tra", substring = true).assertDoesNotExist()
    }

    @Test
    fun shows_offline_when_not_online() {
        setContent(isOnline = false)
        composeRule.onNodeWithText("Ngoại tuyến").assertExists()
    }

    @Test
    fun never_synced_says_so() {
        setContent(pendingSyncCount = 0, errorSyncCount = 0, lastSyncedAt = null)
        composeRule.onNodeWithText("Chưa đồng bộ lần nào").assertExists()
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
    fun readiness_no_longer_lists_the_lux_sensor() {
        setContent()
        composeRule.onNodeWithText("Cảm biến lux").assertDoesNotExist()
    }

    @Test
    fun an_error_offers_retry_and_still_lets_the_engineer_sign_out() {
        var retries = 0
        setContent(
            profileState = ProfileUiState.Error("java.net.UnknownHostException", isNetworkError = true),
            onRetry = { retries++ },
        )
        composeRule.onNodeWithText("Không tải được thông tin cá nhân").assertExists()
        composeRule.onNodeWithText("UnknownHostException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithText("Tình trạng làm việc").performScrollTo().assertExists()
        composeRule.onNodeWithText("Đăng xuất").performScrollTo().assertExists()
    }

    @Test
    fun confirming_logout_still_invokes_onConfirmLogout() {
        var loggedOut = false
        setContent(onConfirmLogout = { loggedOut = true })
        composeRule.onNodeWithText("Đăng xuất").performScrollTo().performClick()
        composeRule.onNodeWithText("Đăng xuất khỏi thiết bị này?").assertExists()
        composeRule.onAllNodesWithText("Đăng xuất")[1].performClick()
        assertTrue(loggedOut)
    }

    @Test
    fun the_top_bar_has_the_title_and_a_bell_that_opens_notifications() {
        var opened = 0
        setContent(unread = 4, onOpenNotifications = { opened++ })
        composeRule.onNodeWithText("Cá nhân").assertExists()
        composeRule.onNodeWithContentDescription("Thông báo, 4 chưa đọc").performClick()
        assertEquals(1, opened)
    }
}
