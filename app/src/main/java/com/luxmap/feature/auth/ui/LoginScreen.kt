package com.luxmap.feature.auth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import com.luxmap.BuildConfig
import com.luxmap.core.theme.Amber500
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.Green400
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.TitleTextStyle
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
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
    val errorPlacement = (uiState as? LoginUiState.Error)?.let { errorPlacementFor(it) }

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

    // The sheet floats over the hero's bottom edge by SheetOverlap (spec 2a-2h). Hero's own
    // height is only known after it is measured (onGloballyPositioned gives px, not before
    // first layout), so the overlap is computed at runtime instead of hardcoded - the hero's
    // height changes with font scale/locale text length. fillMaxSize() + padding(top = ...)
    // on the sheet (not a plain offset) is what keeps its bottom flush with the screen bottom:
    // an offset would only shift where it draws, leaving a gap the same size as the overlap.
    Box(modifier = modifier.fillMaxSize()) {
        var heroHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { heroHeightPx = it.size.height }
                    .background(BrandHeroGradient)
                    // Background bleeds under the status bar (order matters: background must
                    // come before the inset padding), content stays clear of it.
                    .statusBarsPadding()
                    .padding(Spacing.xl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
                NetworkChip(isOnline = isOnline)
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "Chào mừng trở lại",
                style = DisplayTextStyle,
                color = Color.White,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Đăng nhập để nhận nhiệm vụ và tiếp tục công việc.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(Spacing.lg))
            StreetlightSilhouette(modifier = Modifier.fillMaxWidth().height(40.dp))
        }

        val heroHeightDp = with(density) { heroHeightPx.toDp() }
        val sheetTopInset = (heroHeightDp - SheetOverlap).coerceAtLeast(0.dp)
        val sheetShape = RoundedCornerShape(topStart = Radius.block, topEnd = Radius.block)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = sheetTopInset)
                    .shadow(elevation = SheetElevation, shape = sheetShape, clip = false)
                    .background(color = MaterialTheme.colorScheme.surface, shape = sheetShape)
                    // Background bleeds under the gesture navigation bar, content stops above it.
                    .navigationBarsPadding()
                    // The app draws edge to edge, so the window does not shrink when the keyboard
                    // opens. Without this the keyboard would cover the password field and button.
                    // Footer stays IN this same scrolling column (not pinned outside it) - a
                    // fixed footer would keep claiming its space when the keyboard opens and
                    // the form needs that room most.
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.xl),
        ) {
            Text(text = "Đăng nhập làm việc", style = TitleTextStyle)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Sử dụng tài khoản nhân viên đã được cấp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))

            val fieldShape = RoundedCornerShape(Radius.control)
            val fieldColors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = FieldUnfocusedBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface,
                )

            FieldLabel("Mã nhân viên hoặc số điện thoại")
            OutlinedTextField(
                value = viewModel.identifier,
                onValueChange = viewModel::onIdentifierChange,
                placeholder = { Text("Ví dụ: NV-0125") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                enabled = !isSubmitting,
                shape = fieldShape,
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
                        .alpha(if (isSubmitting) FIELD_SUBMITTING_ALPHA else 1f)
                        .focusRequester(identifierFocusRequester),
            )
            // Shown as a sibling below the field, not in supportingText - that slot has its own
            // Material3 padding and does not line up flush with the field's own edge.
            errorPlacement?.identifierMessage?.let {
                Spacer(Modifier.height(Spacing.xs))
                InlineErrorText(it, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.lg))

            FieldLabel("Mật khẩu")
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = { Text("Nhập mật khẩu") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                singleLine = true,
                enabled = !isSubmitting,
                shape = fieldShape,
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
                            if (!isSubmitting) viewModel.onLoginClick()
                        },
                    ),
                isError = errorPlacement?.passwordInvalid == true,
                visualTransformation =
                    if (viewModel.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::onTogglePasswordVisibility) {
                        val icon =
                            if (viewModel.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        Icon(
                            imageVector = icon,
                            contentDescription =
                                if (viewModel.isPasswordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                        )
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (isSubmitting) FIELD_SUBMITTING_ALPHA else 1f)
                        .focusRequester(passwordFocusRequester),
            )
            errorPlacement?.passwordMessage?.let {
                Spacer(Modifier.height(Spacing.xs))
                InlineErrorText(it, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.sm))

            RememberMeRow(
                checked = viewModel.rememberMe,
                onCheckedChange = viewModel::onRememberMeChange,
                enabled = !isSubmitting,
            )
            Spacer(Modifier.height(Spacing.md))

            // Animate the banner in and out. If it appears at once, the button and everything
            // below it jump down by the banner height. lastBannerMessage keeps the text while
            // the banner fades out (bannerMessage is already null at that moment).
            val bannerMessage = errorPlacement?.bannerMessage
            val lastBannerMessage = remember { mutableStateOf("") }
            if (bannerMessage != null) lastBannerMessage.value = bannerMessage
            AnimatedVisibility(
                visible = bannerMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    InlineBanner(message = lastBannerMessage.value, tone = InlineBannerTone.ERROR)
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            val loginButtonText =
                when (uiState) {
                    is LoginUiState.LoggingIn -> "Đang đăng nhập…"
                    is LoginUiState.Prefetching -> "Đã đăng nhập"
                    else -> "Đăng nhập"
                }
            PrimaryButton(
                text = loginButtonText,
                onClick = viewModel::onLoginClick,
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
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(Radius.card),
                            )
                            .padding(Spacing.lg),
                ) {
                    Text(
                        text = "Đang tải dữ liệu ngày làm việc...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Spacing.lg),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "Kết nối được mã hóa và bảo vệ · v${BuildConfig.VERSION_NAME}",
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
                .size(40.dp)
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

private val FieldUnfocusedBackground = Color(0xFFF8F7F4)
private const val FIELD_SUBMITTING_ALPHA = 0.6f
private val SheetOverlap = 24.dp
private val SheetElevation = 2.dp

@Composable
private fun NetworkChip(isOnline: Boolean) {
    val dotColor = if (isOnline) Green400 else Amber500
    val label = if (isOnline) "Trực tuyến" else "Ngoại tuyến"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun StreetlightSilhouette(modifier: Modifier = Modifier) {
    val poleColor = Color(0xFF343842)
    val headColor = Color(0xFFDDE1FF)
    val glowColor = Color.White.copy(alpha = 0.18f)
    val offColumns = setOf(4, 9)
    val columnCount = 13
    Canvas(modifier = modifier) {
        val columnWidth = size.width / columnCount
        val poleHeight = size.height * 0.75f
        val poleTopY = size.height - poleHeight
        for (column in 1..columnCount) {
            val x = columnWidth * (column - 1) + columnWidth / 2f
            val isOn = column !in offColumns
            drawLine(
                color = poleColor,
                start = Offset(x, size.height),
                end = Offset(x, poleTopY),
                strokeWidth = 2.dp.toPx(),
            )
            if (isOn) {
                drawCircle(color = glowColor, radius = 9.dp.toPx(), center = Offset(x, poleTopY))
            }
            drawCircle(
                color = if (isOn) headColor else poleColor,
                radius = 3.dp.toPx(),
                center = Offset(x, poleTopY),
            )
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
        Column {
            Text(
                text = "Duy trì đăng nhập trên thiết bị này",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Lần mở sau vào thẳng, kể cả khi offline",
                style = MaterialTheme.typography.bodySmall,
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
        -> ErrorPlacement(bannerMessage = error.message)
    }
