package com.luxmap.feature.survey.ui.capture

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.theme.LuxMapTheme
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaptureScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun hud_row_shows_the_lux_value_when_present() {
        composeRule.setContent {
            LuxMapTheme {
                CaptureHudRow(gpsAccuracyMeters = 5f, latestLuxValue = 12.3f, freeStorageBytes = 500_000_000L)
            }
        }
        composeRule.onNodeWithText("12.3 lux").assertExists()
    }

    @Test
    fun hud_row_does_not_crash_with_every_value_null() {
        composeRule.setContent {
            LuxMapTheme { CaptureHudRow(gpsAccuracyMeters = null, latestLuxValue = null, freeStorageBytes = null) }
        }
        composeRule.onNodeWithText("Chưa có dữ liệu lux").assertExists()
    }

    @Test
    fun scanning_with_devices_lists_them() {
        val viewModel = mockk<CaptureViewModel>(relaxed = true)
        composeRule.setContent {
            LuxMapTheme {
                CaptureBottomPanelContent(
                    state =
                        CaptureUiState.Scanning(
                            devices = listOf(LuxDevice(address = "AA:BB", name = "LuxMap_ESP32")),
                        ),
                    surveySweepId = "SWEEP-1",
                    workOrderId = "WO-1",
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNodeWithText("LuxMap_ESP32").assertExists()
    }

    @Test
    fun recording_with_a_gps_and_ble_warning_shows_both_capture_warnings() {
        val viewModel = mockk<CaptureViewModel>(relaxed = true)
        composeRule.setContent {
            LuxMapTheme {
                CaptureBottomPanelContent(
                    state = CaptureUiState.Recording(gpsSignalLost = true, bleGapDetected = true),
                    surveySweepId = "SWEEP-1",
                    workOrderId = "WO-1",
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNodeWithText("Mất tín hiệu GPS").assertExists()
        composeRule.onNodeWithText("Mất kết nối cảm biến ánh sáng — vẫn tiếp tục quay").assertExists()
    }

    @Test
    fun packaging_failed_shows_the_reason_and_a_dismiss_action() {
        val viewModel = mockk<CaptureViewModel>(relaxed = true)
        composeRule.setContent {
            LuxMapTheme {
                CaptureBottomPanelContent(
                    state = CaptureUiState.PackagingFailed(reason = "Hết dung lượng"),
                    surveySweepId = "SWEEP-1",
                    workOrderId = "WO-1",
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNodeWithText("Không thể hoàn tất phiên khảo sát: Hết dung lượng").assertExists()
        composeRule.onNodeWithText("Dừng và đóng phiên").assertExists()
    }

    @Test
    fun releasing_the_hold_button_early_does_not_call_onHoldComplete() {
        var completed = false
        composeRule.setContent {
            LuxMapTheme { HoldToStopButton(onHoldComplete = { completed = true }) }
        }
        composeRule.onNodeWithText("Nhấn giữ để dừng quay").assertExists()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(HOLD_TO_STOP_BUTTON_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.onNodeWithTag(HOLD_TO_STOP_BUTTON_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(HOLD_TO_STOP_DURATION_MS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertFalse(completed)
        composeRule.onNodeWithText("Nhấn giữ để dừng quay").assertExists()
    }

    @Test
    fun holding_for_the_full_duration_calls_onHoldComplete() {
        var completed = false
        composeRule.setContent {
            LuxMapTheme { HoldToStopButton(onHoldComplete = { completed = true }) }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(HOLD_TO_STOP_BUTTON_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(HOLD_TO_STOP_DURATION_MS + 100L)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue(completed)
    }

    @Test
    fun hud_row_shows_mat_gps_in_amber_when_the_gps_signal_is_lost() {
        composeRule.setContent {
            LuxMapTheme {
                CaptureHudRow(
                    gpsAccuracyMeters = 5f,
                    latestLuxValue = null,
                    freeStorageBytes = null,
                    gpsSignalLost = true,
                )
            }
        }
        composeRule.onNodeWithText("Mất GPS").assertExists()
    }
}
