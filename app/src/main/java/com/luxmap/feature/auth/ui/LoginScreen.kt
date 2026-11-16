package com.luxmap.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.BuildConfig
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LoginColors
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.auth.data.LoginFailureReason

// F01 Đăng nhập — chỉ 1 vai trò (Tổ khảo sát/sửa chữa), không có màn chọn vai trò (CLAUDE.md/A2).
// Civic Utility F01 / T-07: compact flat navy header and one scrollable form.
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) onLoginSuccess()
    }

    LoginContent(
        uiState = uiState,
        isOnline = isOnline,
        identifier = viewModel.identifier,
        password = viewModel.password,
        isPasswordVisible = viewModel.isPasswordVisible,
        rememberMe = viewModel.rememberMe,
        onIdentifierChange = viewModel::onIdentifierChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onRememberMeChange = viewModel::onRememberMeChange,
        onLogin = viewModel::onLoginClick,
        onGoogleSignIn = { viewModel.onGoogleSignInClick(context) },
        modifier = modifier,
    )
}

// State and callbacks keep rendering independent of Hilt, authentication and navigation.
@Composable
internal fun LoginContent(
    uiState: LoginUiState,
    isOnline: Boolean,
    identifier: String,
    password: String,
    isPasswordVisible: Boolean,
    rememberMe: Boolean,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val identifierFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val errorPlacement = (uiState as? LoginUiState.Error)?.let { errorPlacementFor(it) }

    LaunchedEffect(uiState) {
        // Put the cursor in the field that needs fixing, so the user can type again right away.
        // Errors shown in the banner do not move the focus.
        when ((uiState as? LoginUiState.Error)?.reason) {
            LoginFailureReason.MissingIdentifier -> identifierFocusRequester.requestFocus()
            LoginFailureReason.MissingPassword,
            LoginFailureReason.InvalidCredentials,
            -> passwordFocusRequester.requestFocus()
            else -> Unit
        }
    }

    val isSubmitting = uiState is LoginUiState.LoggingIn || uiState is LoginUiState.Prefetching

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(LoginColors.headerContainer)
                    .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                LogoMark()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LUXMAP",
                        style = MaterialTheme.typography.titleLarge,
                        color = LoginColors.onHeader,
                    )
                    Text(
                        text = "Vận hành hiện trường",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LoginColors.onHeader,
                    )
                }
            }
            NetworkStatus(isOnline = isOnline)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Spacing.lg),
        ) {
            Text(
                text = "Đăng nhập",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Sử dụng tài khoản nhân viên đã được cấp.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))

            val fieldColors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )

            OutlinedTextField(
                value = identifier,
                onValueChange = onIdentifierChange,
                label = { Text("Mã nhân viên hoặc số điện thoại", style = MaterialTheme.typography.labelMedium) },
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("Ví dụ: NV-0125") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                enabled = !isSubmitting,
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors,
                // Next moves to the password field. The code (for example NV-0125) must not be
                // changed by auto-correct or auto-capitalization.
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                isError = errorPlacement?.identifierInvalid == true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(identifierFocusRequester),
            )
            // Shown as a sibling below the field, not in supportingText - that slot has its own
            // Material3 padding and does not line up flush with the field's own edge.
            errorPlacement?.identifierMessage?.let {
                Spacer(Modifier.height(Spacing.xs))
                LoginFieldError(it)
            }
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Mật khẩu", style = MaterialTheme.typography.labelMedium) },
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("Nhập mật khẩu") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                singleLine = true,
                enabled = !isSubmitting,
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors,
                // Done hides the keyboard and signs in. The isSubmitting check stops a second
                // request when the user taps Done twice quickly.
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (!isSubmitting) onLogin()
                        },
                    ),
                isError = errorPlacement?.passwordInvalid == true,
                visualTransformation =
                    if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        val icon =
                            if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        Icon(
                            imageVector = icon,
                            contentDescription =
                                if (isPasswordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                        )
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester),
            )
            errorPlacement?.passwordMessage?.let {
                Spacer(Modifier.height(Spacing.xs))
                LoginFieldError(it)
            }
            Spacer(Modifier.height(Spacing.sm))

            RememberMeRow(
                checked = rememberMe,
                onCheckedChange = onRememberMeChange,
                enabled = !isSubmitting,
            )
            Spacer(Modifier.height(Spacing.md))

            errorPlacement?.bannerMessage?.let { message ->
                LuxBanner(message = message, tone = LuxBannerTone.ERROR)
                Spacer(Modifier.height(Spacing.md))
            }

            val loginButtonText =
                when (uiState) {
                    is LoginUiState.LoggingIn -> "Đang đăng nhập…"
                    is LoginUiState.Prefetching -> "Đã đăng nhập"
                    else -> "Đăng nhập"
                }
            PrimaryButton(
                text = loginButtonText,
                onClick = onLogin,
                enabled = !isSubmitting,
                isLoading = uiState is LoginUiState.LoggingIn,
                trailingIcon = if (isSubmitting) null else Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState is LoginUiState.Prefetching) {
                Spacer(Modifier.height(Spacing.lg))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                MaterialTheme.shapes.medium,
                            )
                            .padding(Spacing.lg)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        text = "Đang tải dữ liệu ngày làm việc...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            OrDivider()
            Spacer(Modifier.height(Spacing.lg))
            LuxSecondaryButton(
                text = "Đăng nhập bằng Google",
                onClick = onGoogleSignIn,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.lg))

            // Support flow is not specified yet (no destination/handler in the spec), so this
            // is plain text, not link-styled (no underline/link color) — styling it like a
            // working link when nothing happens on tap would be misleading.
            Text(
                text = "Cần hỗ trợ đăng nhập?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Phiên bản ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Retain the existing placeholder mark until an official brand asset is supplied.
@Composable
private fun LogoMark() {
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(width = 4.dp, height = 14.dp).background(LoginColors.onHeader, RoundedCornerShape(2.dp)))
            Box(Modifier.size(width = 4.dp, height = 22.dp).background(LoginColors.onHeader, RoundedCornerShape(2.dp)))
            Box(Modifier.size(width = 4.dp, height = 10.dp).background(LoginColors.onHeader, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "Hoặc",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NetworkStatus(isOnline: Boolean) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = LoginColors.onHeader,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (isOnline) "Trực tuyến" else "Ngoại tuyến",
            style = MaterialTheme.typography.bodySmall,
            color = LoginColors.onHeader,
        )
    }
}

@Composable
private fun LoginFieldError(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

// The whole row is the touch target (min 48dp), not just the small checkbox square. Label uses
// body scale (16sp) because it explains a choice, and Caption is only for secondary metadata.
@Composable
private fun RememberMeRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.minTouchTarget)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Duy trì đăng nhập trên thiết bị này",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Lần mở sau vào thẳng, kể cả khi offline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Where one login error is shown. Errors of a field go with that field, the others go to the
// banner above the button.
private data class ErrorPlacement(
    val identifierMessage: String? = null,
    val passwordMessage: String? = null,
    val identifierInvalid: Boolean = false,
    val passwordInvalid: Boolean = false,
    val bannerMessage: String? = null,
)

private fun errorPlacementFor(error: LoginUiState.Error): ErrorPlacement =
    when (error.reason) {
        LoginFailureReason.MissingIdentifier ->
            ErrorPlacement(identifierMessage = error.message, identifierInvalid = true)
        LoginFailureReason.MissingPassword ->
            ErrorPlacement(passwordMessage = error.message, passwordInvalid = true)
        // The server has one code for a wrong username and a wrong password, so both fields are
        // marked and the message is shown under the password field.
        LoginFailureReason.InvalidCredentials ->
            ErrorPlacement(passwordMessage = error.message, identifierInvalid = true, passwordInvalid = true)
        LoginFailureReason.AccountLocked,
        LoginFailureReason.Network,
        LoginFailureReason.Unknown,
        LoginFailureReason.GoogleSignInFailed,
        -> ErrorPlacement(bannerMessage = error.message)
    }
