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
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.ErrorBanner
import com.luxmap.core.ui.components.InlineErrorText
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.auth.data.LoginFailureReason

// F01 Đăng nhập — chỉ 1 vai trò (Tổ khảo sát/sửa chữa), không có màn chọn vai trò (CLAUDE.md/A2).
// Layout theo Figma F01_Login_v2_4 (hero navy gradient + sheet bo góc trên). Icon/logo thật
// (wordmark LUXMAP) chưa export được từ Figma làm asset, để lại cho khi có asset chính thức.
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val identifierFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val errorPlacement = (uiState as? LoginUiState.Error)?.let { errorPlacementFor(it, isOnline) }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
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

    // Hero keeps its own (wrap-content) height; the sheet below takes all remaining screen
    // space via weight(1f) and scrolls its own content — so the sheet's white background
    // always reaches the bottom of the screen, even when its content is shorter than the
    // screen (a plain Column + outer verticalScroll would leave the background showing
    // through below short content instead).
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(BrandHeroGradient)
                    // Background bleeds under the status bar (order matters: background must
                    // come before the inset padding), content stays clear of it.
                    .statusBarsPadding()
                    .padding(Spacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark()
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        text = "LUXMAP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Vận hành hiện trường",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "Chào mừng trở lại",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Đăng nhập để nhận nhiệm vụ và tiếp tục công việc.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = Dimens.radiusSheet, topEnd = Dimens.radiusSheet),
                    )
                    // Background bleeds under the gesture navigation bar, content stops above it.
                    .navigationBarsPadding()
                    // The app draws edge to edge, so the window does not shrink when the keyboard
                    // opens. Without this the keyboard would cover the password field and button.
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.xl),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusPill)),
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            Text(text = "Đăng nhập làm việc", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Sử dụng tài khoản nhân viên đã được cấp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))

            FieldLabel("Mã nhân viên hoặc số điện thoại")
            OutlinedTextField(
                value = viewModel.identifier,
                onValueChange = viewModel::onIdentifierChange,
                placeholder = { Text("Ví dụ: NV-0125") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                enabled = !isSubmitting,
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
                supportingText = errorPlacement?.identifierMessage?.let { { InlineErrorText(it) } },
                modifier = Modifier.fillMaxWidth().focusRequester(identifierFocusRequester),
            )
            Spacer(Modifier.height(Spacing.lg))

            FieldLabel("Mật khẩu")
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = { Text("Nhập mật khẩu") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                singleLine = true,
                enabled = !isSubmitting,
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
                            if (!isSubmitting) viewModel.onLoginClick()
                        },
                    ),
                isError = errorPlacement?.passwordInvalid == true,
                supportingText = errorPlacement?.passwordMessage?.let { { InlineErrorText(it) } },
                visualTransformation =
                    if (viewModel.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::onTogglePasswordVisibility) {
                        val icon =
                            if (viewModel.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        Icon(
                            imageVector = icon,
                            contentDescription = if (viewModel.isPasswordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
            )
            Spacer(Modifier.height(Spacing.sm))

            RememberMeRow(
                checked = viewModel.rememberMe,
                onCheckedChange = viewModel::onRememberMeChange,
                enabled = !isSubmitting,
            )
            Spacer(Modifier.height(Spacing.md))

            if (!isOnline) {
                OfflineWarningNote()
                Spacer(Modifier.height(Spacing.md))
            }

            errorPlacement?.bannerMessage?.let { message ->
                ErrorBanner(message)
                Spacer(Modifier.height(Spacing.md))
            }

            PrimaryButton(
                text = "Đăng nhập",
                onClick = viewModel::onLoginClick,
                enabled = !isSubmitting,
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState is LoginUiState.Prefetching) {
                Spacer(Modifier.height(Spacing.lg))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Đang tải dữ liệu ngày làm việc...",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

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
            Spacer(Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Spacing.lg),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "Kết nối được mã hóa và bảo vệ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Placeholder brand mark (3 bars, evokes a lux/signal reading) — the real LUXMAP wordmark icon
// is not available as an exported asset from Figma yet, so this is plain shapes, not a hand-
// drawn copy of the real logo.
@Composable
private fun LogoMark() {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Dimens.radiusMedium))
                .background(Color.White.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(2.dp)))
            Box(Modifier.size(width = 4.dp, height = 22.dp).background(Color.White, RoundedCornerShape(2.dp)))
            Box(Modifier.size(width = 4.dp, height = 10.dp).background(Color.White, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(Spacing.xs))
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
        Text(
            text = "Duy trì đăng nhập trên thiết bị này",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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

private fun errorPlacementFor(
    error: LoginUiState.Error,
    isOnline: Boolean,
): ErrorPlacement =
    when (error.reason) {
        LoginFailureReason.MissingIdentifier ->
            ErrorPlacement(identifierMessage = error.message, identifierInvalid = true)
        LoginFailureReason.MissingPassword ->
            ErrorPlacement(passwordMessage = error.message, passwordInvalid = true)
        // The server has one code for a wrong username and a wrong password, so both fields are
        // marked and the message is shown under the password field.
        LoginFailureReason.InvalidCredentials ->
            ErrorPlacement(passwordMessage = error.message, identifierInvalid = true, passwordInvalid = true)
        // When the device is offline, OfflineWarningNote already says it, so do not repeat it.
        LoginFailureReason.Network -> ErrorPlacement(bannerMessage = error.message.takeIf { isOnline })
        LoginFailureReason.AccountLocked,
        LoginFailureReason.Unknown,
        -> ErrorPlacement(bannerMessage = error.message)
    }

// Only shown when the device has no network (see LoginScreen's isOnline check) — first login
// needs a real API call, so this is the one precondition worth calling out, placed right above
// the login button instead of always-on at the bottom of the screen. Body copy uses body scale
// (16sp) per CLAUDE.md's rule that Caption (14sp) is only for secondary metadata, not guidance.
@Composable
private fun OfflineWarningNote() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.radiusLarge))
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(
                text = "Cần kết nối Internet để đăng nhập lần đầu.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Kiểm tra kết nối rồi thử lại.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
