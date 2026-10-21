package com.luxmap.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EvidencePickerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun when_there_is_no_photo_shows_the_add_tile_and_invokes_onTileClick() {
        var tapped = false
        composeRule.setContent {
            LuxMapTheme { EvidencePicker(hasPhoto = false, filePath = null, onTileClick = { tapped = true }) }
        }
        composeRule.onNodeWithContentDescription("Chụp ảnh").performClick()
        assertTrue(tapped)
    }

    @Test
    fun when_there_is_a_photo_shows_the_filled_tile_and_invokes_onTileClick_to_preview() {
        var tapped = false
        composeRule.setContent {
            LuxMapTheme {
                EvidencePicker(
                    hasPhoto = true,
                    filePath = "/data/evidence.jpg",
                    onTileClick = { tapped = true },
                )
            }
        }
        composeRule.onNodeWithText("Chụp ảnh").performClick()
        assertTrue(tapped)
    }
}
