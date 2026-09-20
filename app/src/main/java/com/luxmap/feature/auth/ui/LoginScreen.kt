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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton

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

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
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

            Text(text = "Đăng nhập tác nghiệp", style = MaterialTheme.typography.titleLarge)
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
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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

            val errorState = uiState as? LoginUiState.Error
            if (errorState != null) {
                Text(
                    text = errorState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
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
