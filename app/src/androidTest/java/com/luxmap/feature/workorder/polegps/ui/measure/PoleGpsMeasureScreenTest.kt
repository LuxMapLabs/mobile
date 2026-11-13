package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.domain.FixQuality
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PoleGpsMeasureScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun success(
        quality: FixQuality,
        canSave: Boolean,
        accuracy: Double = 4.0,
    ) = PoleGpsMeasureUiState.Success(
        poleLabel = "Cột 3",
        previous = PoleReference("P-1", 3, 10.0, 106.0),
        liveFix = GpsFix(10.1, 106.1, accuracy, Instant.EPOCH),
        quality = quality,
        canSave = canSave,
        saved = false,
        stored = null,
    )

    private fun show(
        state: PoleGpsMeasureUiState,
        onSave: () -> Unit = {},
    ) = rule.setContent {
        LuxMapTheme {
            PoleGpsMeasureContent(state, onBack = {}, onSave = onSave, onMeasureAgain = {}, onRetryPermission = {})
        }
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
        show(PoleGpsMeasureUiState.Error("Cần cấp quyền vị trí để đo GPS"))
        rule.onNodeWithText("Cần cấp quyền vị trí để đo GPS").assertExists()
        rule.onNodeWithText("Thử lại").assertExists()
    }

    @Test
    fun save_is_disabled_while_the_fix_is_weak() {
        show(success(FixQuality.WEAK, canSave = false, accuracy = 25.0))
        rule.onNodeWithText("Lưu vị trí").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun save_is_enabled_and_fires_when_ready() {
        var saved = false
        show(success(FixQuality.READY, canSave = true), onSave = { saved = true })
        rule.onNodeWithText("Lưu vị trí").performScrollTo().assertIsEnabled().performClick()
        assertTrue(saved)
    }
}
