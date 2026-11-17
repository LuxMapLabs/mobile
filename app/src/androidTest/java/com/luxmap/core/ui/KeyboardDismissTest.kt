package com.luxmap.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeyboardDismissTest {
    @get:Rule val composeRule = createComposeRule()

    private var clicks = 0

    private fun show() {
        composeRule.setContent {
            LuxMapTheme {
                var text by remember { mutableStateOf("") }
                Column(modifier = Modifier.fillMaxSize().clearFocusOnTapOutside()) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.testTag("field"))
                    Text("Vùng trống")
                    Button(onClick = { clicks++ }) { Text("Gửi") }
                }
            }
        }
        composeRule.onNodeWithTag("field").performClick().assertIsFocused()
    }

    @Test
    fun tapping_empty_space_clears_focus() {
        show()
        composeRule.onNodeWithText("Vùng trống").performClick()
        composeRule.onNodeWithTag("field").assertIsNotFocused()
    }

    @Test
    fun tapping_a_button_still_clicks_it() {
        show()
        composeRule.onNodeWithText("Gửi").performClick()
        assertEquals(1, clicks)
    }
}
