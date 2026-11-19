package com.luxmap.feature.workorder.polegps.ui.measure

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.polegps.data.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

class PoleGpsQualityUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun poor_accuracy_displays_sample_and_requires_an_explicit_action() {
        var confirmed = 0
        var retried = 0
        rule.setContent {
            LuxMapTheme {
                LowAccuracyConfirmation(
                    GpsFix(10.5, 106.5, 35.0, Instant.ofEpochSecond(42), 1L),
                    onConfirm = { confirmed++ },
                    onRetry = { retried++ },
                )
            }
        }
        rule.onNodeWithText("Sai số GPS: ±35.0 m", substring = true).assertExists()
        rule.runOnIdle { assertEquals(0, confirmed) }
        rule.onNodeWithText("Đo lại").performClick()
        rule.runOnIdle { assertEquals(1, retried) }
        rule.onNodeWithText("Vẫn lưu vị trí này").performClick()
        rule.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test
    fun light_large_font_keeps_confirmation_actions_accessible() = showLargeFont(false)

    @Test
    fun night_large_font_keeps_confirmation_actions_accessible() = showLargeFont(true)

    private fun showLargeFont(dark: Boolean) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                LuxMapTheme(forceDark = dark) {
                    LowAccuracyConfirmation(
                        GpsFix(10.5, 106.5, 35.0, Instant.ofEpochSecond(42), 1L),
                        onConfirm = {},
                        onRetry = {},
                    )
                }
            }
        }
        rule.onNodeWithText("Đo lại").assertIsDisplayed()
        rule.onNodeWithText("Vẫn lưu vị trí này").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output =
            File(context.getExternalFilesDir("gps-evidence"), if (dark) "night-font2.png" else "light-font2.png")
        output.outputStream().use {
            rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
