package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.polegps.data.PoleReference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PoleGpsMeasureScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        state: PoleGpsMeasureUiState,
        onRetryPermission: () -> Unit = {},
    ) = rule.setContent {
        LuxMapTheme { PoleGpsMeasureContent(state, onBack = {}, onRetryPermission = onRetryPermission) }
    }

    @Test
    fun loading_state_is_announced() {
        show(PoleGpsMeasureUiState.Loading)
        rule.onNodeWithText("Đang chuẩn bị", substring = true).assertExists()
    }

    @Test
    fun empty_state_explains_the_pole_is_not_in_the_order() {
        show(PoleGpsMeasureUiState.Empty)
        rule.onNodeWithText("không thuộc lệnh này", substring = true).assertExists()
    }

    @Test
    fun error_state_offers_a_retry() {
        var retried = false
        show(PoleGpsMeasureUiState.Error("Cần cấp quyền vị trí"), onRetryPermission = { retried = true })
        rule.onNodeWithText("Thử lại").performClick()
        assertTrue(retried)
    }

    @Test
    fun back_button_invokes_onBack() {
        var backed = false
        rule.setContent {
            LuxMapTheme {
                PoleGpsMeasureContent(PoleGpsMeasureUiState.Loading, onBack = { backed = true }, onRetryPermission = {})
            }
        }
        rule.onNodeWithContentDescription("Quay lại").performClick()
        assertTrue(backed)
    }

    @Test
    fun top_bar_shows_the_pole_label_once_ready() {
        show(PoleGpsMeasureUiState.Success("Cột 3 · P-1", PoleReference("P-1", 3, 10.0, 106.0)))
        rule.onNodeWithText("Cột 3 · P-1").assertExists()
    }
}
