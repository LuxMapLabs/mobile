package com.luxmap.feature.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.auth.data.LoginFailureReason
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun editing_fields_and_toggling_visibility_preserves_the_entered_password() {
        setContent()
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("NV-0125")
        composeRule.onNodeWithText("NV-0125").assertExists()
        fields[0].performImeAction()
        fields[1].assertIsFocused().performTextInput("secret")
        composeRule.onNodeWithContentDescription("Hiện mật khẩu").performClick()
        composeRule.onNodeWithText("secret").assertExists()
        composeRule.onNodeWithContentDescription("Ẩn mật khẩu").performClick()
        composeRule.onNodeWithContentDescription("Hiện mật khẩu").assertExists()
    }

    @Test
    fun remember_me_can_be_changed_from_the_whole_row() {
        setContent()
        composeRule.onNodeWithText("Duy trì đăng nhập trên thiết bị này")
            .performScrollTo().assertIsOn().performClick().assertIsOff()
    }

    @Test
    fun login_button_and_password_ime_done_submit_the_form() {
        var submitted = 0
        setContent(onLogin = { submitted++ })
        composeRule.onNode(hasText("Đăng nhập") and hasClickAction()).performScrollTo().performClick()
        composeRule.onAllNodes(hasSetTextAction())[1].performScrollTo().performImeAction()
        assertEquals(2, submitted)
    }

    @Test
    fun google_action_does_not_submit_the_password_form() {
        var submitted = 0
        var googleSignIns = 0
        setContent(onLogin = { submitted++ }, onGoogleSignIn = { googleSignIns++ })
        composeRule.onNodeWithText("Đăng nhập bằng Google").performScrollTo().performClick()
        assertEquals(1, googleSignIns)
        assertEquals(0, submitted)
    }

    @Test
    fun logging_in_disables_fields_remember_me_and_both_sign_in_actions() {
        setContent(uiState = LoginUiState.LoggingIn)
        assertSubmissionDisabled("Đang đăng nhập…")
    }

    @Test
    fun prefetching_disables_submission_and_shows_its_own_progress_message() {
        setContent(uiState = LoginUiState.Prefetching)
        assertSubmissionDisabled("Đã đăng nhập")
        composeRule.onNodeWithText("Đang tải dữ liệu ngày làm việc...").assertExists()
    }

    @Test
    fun a_missing_identifier_error_is_shown_once_and_focuses_identifier() {
        setContent(uiState = LoginUiState.Error(LoginFailureReason.MissingIdentifier, "Nhập mã nhân viên"))
        composeRule.onAllNodesWithText("Nhập mã nhân viên").assertCountEquals(1)
        composeRule.onAllNodes(hasSetTextAction())[0].assertIsFocused()
    }

    @Test
    fun invalid_credentials_are_shown_once_and_focus_password() {
        setContent(uiState = LoginUiState.Error(LoginFailureReason.InvalidCredentials, "Thông tin không đúng"))
        composeRule.onAllNodesWithText("Thông tin không đúng").assertCountEquals(1)
        composeRule.onAllNodes(hasSetTextAction())[1].assertIsFocused()
    }

    @Test
    fun a_network_error_keeps_both_login_actions_available_for_retry() {
        setContent(uiState = LoginUiState.Error(LoginFailureReason.Network, "Không thể kết nối"))
        composeRule.onAllNodesWithText("Không thể kết nối").assertCountEquals(1)
        composeRule.onNode(hasText("Đăng nhập") and hasClickAction()).assertIsEnabled()
        composeRule.onNodeWithText("Đăng nhập bằng Google").assertIsEnabled()
    }

    @Test
    fun a_missing_password_error_is_shown_once_and_focuses_password() {
        setContent(uiState = LoginUiState.Error(LoginFailureReason.MissingPassword, "Vui lòng nhập mật khẩu."))
        composeRule.onAllNodesWithText("Vui lòng nhập mật khẩu.").assertCountEquals(1)
        composeRule.onAllNodes(hasSetTextAction())[1].assertIsFocused()
    }

    @Test
    fun an_account_locked_error_keeps_the_retry_actions_available() {
        setContent(uiState = LoginUiState.Error(LoginFailureReason.AccountLocked, "Tài khoản đã bị khóa."))
        composeRule.onAllNodesWithText("Tài khoản đã bị khóa.").assertCountEquals(1)
        composeRule.onNode(hasText("Đăng nhập") and hasClickAction()).assertIsEnabled()
        composeRule.onNodeWithText("Đăng nhập bằng Google").assertIsEnabled()
    }

    @Test
    fun a_google_error_allows_retrying_google_without_submitting_password_credentials() {
        var submitted = 0
        var googleSignIns = 0
        setContent(
            uiState = LoginUiState.Error(LoginFailureReason.GoogleSignInFailed, "Không thể đăng nhập bằng Google."),
            onLogin = { submitted++ },
            onGoogleSignIn = { googleSignIns++ },
        )
        composeRule.onAllNodesWithText("Không thể đăng nhập bằng Google.").assertCountEquals(1)
        composeRule.onNodeWithText("Đăng nhập bằng Google").performScrollTo().performClick()
        assertEquals(1, googleSignIns)
        assertEquals(0, submitted)
    }

    @Test
    fun tapping_disabled_submission_actions_does_not_start_another_request() {
        var submitted = 0
        var googleSignIns = 0
        setContent(
            uiState = LoginUiState.LoggingIn,
            onLogin = { submitted++ },
            onGoogleSignIn = { googleSignIns++ },
        )
        composeRule.onNodeWithText("Đang đăng nhập…").performScrollTo().performClick()
        composeRule.onNodeWithText("Đăng nhập bằng Google").performScrollTo().performClick()
        assertEquals(0, submitted)
        assertEquals(0, googleSignIns)
    }

    private fun assertSubmissionDisabled(loginLabel: String) {
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].assertIsNotEnabled()
        fields[1].assertIsNotEnabled()
        composeRule.onNodeWithText("Duy trì đăng nhập trên thiết bị này").assertIsNotEnabled()
        composeRule.onNodeWithText(loginLabel).assertIsNotEnabled()
        composeRule.onNodeWithText("Đăng nhập bằng Google").assertIsNotEnabled()
    }

    private fun setContent(
        uiState: LoginUiState = LoginUiState.Idle,
        onLogin: () -> Unit = {},
        onGoogleSignIn: () -> Unit = {},
    ) {
        composeRule.setContent {
            var identifier by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var visible by remember { mutableStateOf(false) }
            var rememberMe by remember { mutableStateOf(true) }
            LuxMapTheme {
                LoginContent(
                    uiState = uiState,
                    isOnline = true,
                    identifier = identifier,
                    password = password,
                    isPasswordVisible = visible,
                    rememberMe = rememberMe,
                    onIdentifierChange = { identifier = it },
                    onPasswordChange = { password = it },
                    onTogglePasswordVisibility = { visible = !visible },
                    onRememberMeChange = { rememberMe = it },
                    onLogin = onLogin,
                    onGoogleSignIn = onGoogleSignIn,
                )
            }
        }
    }
}
