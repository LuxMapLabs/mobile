package com.luxmap.feature.auth.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.auth.data.LoginFailureReason

// Render every phase without constructing a ViewModel or calling authentication services.
internal class LoginPreviewStateProvider : PreviewParameterProvider<LoginUiState> {
    override val values =
        sequenceOf(
            LoginUiState.Idle,
            LoginUiState.Error(LoginFailureReason.MissingPassword, "Vui lòng nhập mật khẩu."),
            LoginUiState.Error(LoginFailureReason.Network, "Kết nối thất bại. Vui lòng thử lại."),
            LoginUiState.LoggingIn,
            LoginUiState.Prefetching,
        )
}

@Preview(name = "Light · 360dp", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(
    name = "Night Field · 360dp",
    widthDp = 360,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(name = "Large text · 360dp", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Preview(name = "Text 1.3 · 360dp", widthDp = 360, heightDp = 800, fontScale = 1.3f, showBackground = true)
@Preview(
    name = "Night Field · large text",
    widthDp = 360,
    heightDp = 800,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun LoginPreview(
    @PreviewParameter(LoginPreviewStateProvider::class) state: LoginUiState,
) {
    LuxMapTheme {
        LoginContent(
            uiState = state,
            isOnline = state !is LoginUiState.Error || state.reason != LoginFailureReason.Network,
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
