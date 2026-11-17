package com.luxmap.feature.profile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import com.luxmap.feature.profile.data.CurrentUserProfile
import org.junit.Rule
import org.junit.Test

class ProfileLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show() {
        val profile =
            CurrentUserProfile(
                fullName = "Nguyễn Hoàng Phước Thịnh Trường Giang",
                email = "nguyen.hoang.phuoc.thinh.truong.giang@luxmap.vn",
                role = "field_engineer",
                communeIds = listOf("COM-001", "COM-002", "COM-003"),
            )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        ProfileScreen(
                            username = "kysu_hien_truong_01",
                            profileState = ProfileUiState.Success(profile),
                            isLoggingOut = false,
                            deviceModel = "Samsung SM-A075F",
                            appVersion = "1.0.0",
                            pendingSyncCount = 2,
                            errorSyncCount = 1,
                            lastSyncedAt = null,
                            isOnline = false,
                            onConfirmLogout = {},
                            unreadNotificationCount = 12,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun long_identity_and_sync_summary_stay_visible_at_large_font() {
        show()
        composeRule.onNodeWithText("Nguyễn Hoàng Phước Thịnh Trường Giang").assertIsDisplayed()
        composeRule.onNodeWithText("Chờ 2 · Lỗi 1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun logout_keeps_a_48dp_target_at_large_font() {
        show()
        composeRule.onNodeWithText("Đăng xuất").performScrollTo().assertTouchTargetIsAtLeast(48.dp)
    }
}
