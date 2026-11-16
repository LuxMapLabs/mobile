package com.luxmap.feature.auth.ui

import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.auth.data.LoginFailureReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LoginLayoutTest(
    private val fontScale: Float,
    private val darkMode: Boolean,
) {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun error_actions_and_header_remain_reachable_on_a_360dp_screen() {
        val message = "Không thể kết nối. Vui lòng kiểm tra mạng rồi thử đăng nhập lại."
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp)) then
                    DeviceConfigurationOverride.FontScale(fontScale) then
                    DeviceConfigurationOverride.DarkMode(darkMode),
            ) {
                LuxMapTheme {
                    LoginContent(
                        uiState = LoginUiState.Error(LoginFailureReason.Network, message),
                        isOnline = false,
                        identifier = "NV-0125",
                        password = "",
                        isPasswordVisible = false,
                        rememberMe = true,
                        onIdentifierChange = {},
                        onPasswordChange = {},
                        onTogglePasswordVisibility = {},
                        onRememberMeChange = {},
                        onLogin = {},
                        onGoogleSignIn = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithText(message).assertCountEquals(1)
        composeRule.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("Đăng nhập") and hasClickAction()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Đăng nhập bằng Google").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cần hỗ trợ đăng nhập?").performScrollTo().assertIsDisplayed()
        // A single scroll flow must also bring the header back after reaching the footer.
        composeRule.onNodeWithText("LUXMAP").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ngoại tuyến").performScrollTo().assertIsDisplayed()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "font={0}, night={1}")
        fun configurations(): List<Array<Any>> =
            listOf(1f, 1.3f, 2f).flatMap { font ->
                listOf(false, true).map { night -> arrayOf<Any>(font, night) }
            }
    }
}
