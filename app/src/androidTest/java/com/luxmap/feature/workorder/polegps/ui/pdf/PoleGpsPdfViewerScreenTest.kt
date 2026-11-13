package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PoleGpsPdfViewerScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        state: PoleGpsPdfViewerUiState,
        onRetry: () -> Unit = {},
    ) = rule.setContent {
        LuxMapTheme {
            PoleGpsPdfViewerContent(state, onBack = {}, onRetry = onRetry)
        }
    }

    @Test
    fun loading_state_is_announced() {
        show(PoleGpsPdfViewerUiState.Loading)
        rule.onNodeWithText("Đang mở file PDF", substring = true).assertExists()
    }

    @Test
    fun empty_state_says_the_file_was_not_found() {
        show(PoleGpsPdfViewerUiState.Empty)
        rule.onNodeWithText("Không tìm thấy file này", substring = true).assertExists()
    }

    @Test
    fun error_with_retry_allowed_shows_an_enabled_retry_button() {
        var retried = false
        show(PoleGpsPdfViewerUiState.Error("Tải file lỗi, thử lại", canRetry = true), onRetry = { retried = true })
        rule.onNodeWithText("Thử lại").assertIsEnabled().performClick()
        assertTrue(retried)
    }

    @Test
    fun error_without_retry_shows_no_retry_button() {
        show(PoleGpsPdfViewerUiState.Error("Đang tải, hãy thử lại sau", canRetry = false))
        rule.onNodeWithText("Thử lại").assertDoesNotExist()
    }
}
