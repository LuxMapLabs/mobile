package com.luxmap.feature.map.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.map.data.PoleDetail
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun detail(
    iotStatus: String? = "online",
    lat: Double? = 10.8,
) = PoleDetail(
    poleId = "POLE-0047",
    segmentId = "SEG-1",
    segmentName = "Đường liên xã",
    lat = lat,
    lng = 106.7,
    fixtureType = "led",
    powerSource = "grid",
    lampWatt = 60,
    installDate = null,
    warrantyExpiry = "2027-01-01",
    fixtureStatus = AssetCondition.OUT,
    statusConfidence = 0.9,
    determinedAt = "2026-11-10T03:00:00Z",
    sourceChannel = "cv",
    hasIotNode = iotStatus != null,
    iotNodeStatus = iotStatus,
    iotLastReportAt = "2026-11-17T01:00:00Z",
    luminanceHistory = emptyList(),
    runtimeHistory = emptyList(),
    openFaults = emptyList(),
    recentFrames = emptyList(),
    note = null,
)

class PoleDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun the_top_bar_names_the_pole() {
        composeRule.setContent {
            LuxMapTheme { PoleDetailScreen(uiState = PoleDetailUiState.Success(detail()), onBack = {}) }
        }
        composeRule.onNodeWithText("Chi tiết cột đèn").assertExists()
        composeRule.onNodeWithText("POLE-0047").assertExists()
    }

    @Test
    fun loading_is_one_announced_region() {
        composeRule.setContent { LuxMapTheme { PoleDetailScreen(uiState = PoleDetailUiState.Loading, onBack = {}) } }
        composeRule.onNodeWithContentDescription("Đang tải chi tiết cột").assertExists()
    }

    @Test
    fun empty_says_the_pole_was_not_found() {
        composeRule.setContent { LuxMapTheme { PoleDetailScreen(uiState = PoleDetailUiState.Empty, onBack = {}) } }
        composeRule.onNodeWithText("Không tìm thấy cột đèn này").assertExists()
    }

    @Test
    fun a_network_error_uses_screen_copy_and_retries() {
        var retries = 0
        composeRule.setContent {
            LuxMapTheme {
                PoleDetailScreen(
                    uiState = PoleDetailUiState.Error("java.net.UnknownHostException", isNetworkError = true),
                    onBack = {},
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Không tải được chi tiết cột").assertExists()
        composeRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeRule.onNodeWithText("UnknownHostException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }
}
