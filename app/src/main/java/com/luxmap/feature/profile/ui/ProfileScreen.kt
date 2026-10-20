package com.luxmap.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.profile.data.CurrentUserProfile
import java.time.Instant

@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val username by viewModel.username.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val bleConnectionState by viewModel.bleConnectionState.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val errorSyncCount by viewModel.errorSyncCount.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    ProfileScreen(
        // Null only for a session saved before the app kept the username.
        username = username ?: "Tài khoản",
        profileState = profileState,
        isLoggingOut = viewModel.isLoggingOut,
        deviceModel = viewModel.deviceModel,
        appVersion = viewModel.appVersion,
        bleConnectionState = bleConnectionState,
        pendingSyncCount = pendingSyncCount,
        errorSyncCount = errorSyncCount,
        lastSyncedAt = lastSyncedAt,
        isOnline = isOnline,
        onConfirmLogout = viewModel::onConfirmLogout,
        modifier = modifier,
    )
}

// F15 Cá nhân - "Đề xuất theo hướng 2a": hero carries identity (name/username/role). Content
// below is still the plain settings-row layout for now - restyled into the "Sẵn sàng làm việc"
// card group in the next commit.
@Composable
fun ProfileScreen(
    username: String,
    profileState: ProfileUiState,
    isLoggingOut: Boolean,
    deviceModel: String,
    appVersion: String,
    bleConnectionState: BleConnectionState,
    pendingSyncCount: Int,
    errorSyncCount: Int,
    lastSyncedAt: Instant?,
    isOnline: Boolean,
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Survives rotation, so the dialog does not disappear while the user is deciding.
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ProfileHeader(username = username, profileState = profileState)
        // Overlaps the hero's bottom edge, same pattern as Home/Survey Hub/pole list - the clip
        // (not just background) matters once this content scrolls, see the F02/F05 fix this
        // exact bug needed: background alone paints the rounded shape but never cuts the
        // column's own square corners.
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .offset(y = -Spacing.xl)
                    .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                    .background(MaterialTheme.colorScheme.background),
        ) {
            when (profileState) {
                is ProfileUiState.Loading ->
                    Box(
                        modifier = Modifier.fillMaxSize().testTag("profile_loading"),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                is ProfileUiState.Success ->
                    ProfileContent(
                        profile = profileState.profile,
                        isLoggingOut = isLoggingOut,
                        deviceModel = deviceModel,
                        appVersion = appVersion,
                        bleConnectionState = bleConnectionState,
                        pendingSyncCount = pendingSyncCount,
                        errorSyncCount = errorSyncCount,
                        lastSyncedAt = lastSyncedAt,
                        isOnline = isOnline,
                        onLogoutClick = { showConfirmDialog = true },
                    )

                is ProfileUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = "Không tải được thông tin cá nhân",
                        subtitle = profileState.message,
                        action = {},
                    )
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                val tone = AssetCondition.OUT.badgeColors(isDark = false)
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(tone.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Filled.Logout, contentDescription = null, tint = tone.text)
                }
            },
            title = { Text("Đăng xuất khỏi thiết bị này?") },
            text = { Text("Bạn cần đăng nhập lại để tiếp tục sử dụng ứng dụng.") },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
                ) {
                    Text("Huỷ")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onConfirmLogout()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Đăng xuất")
                }
            },
        )
    }
}

// The notification bell is already floated globally by NavGraph's Scaffold (NotificationBellBar,
// on every tab) - this hero deliberately has no bell of its own, that would duplicate it.
@Composable
private fun ProfileHeader(
    username: String,
    profileState: ProfileUiState,
) {
    // Falls back to the auth username while the richer profile is still loading or failed to
    // load, instead of leaving the hero blank or branching its whole layout on profileState.
    val displayName = (profileState as? ProfileUiState.Success)?.profile?.fullName ?: username
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient)
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xs, bottom = Spacing.xxxl),
    ) {
        Text(text = "Cá nhân", style = DisplayTextStyle, color = Color.White)
        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ProfileAvatar(initials = initialsOf(displayName))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = displayName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                RoleBadge()
            }
        }
    }
}

@Composable
private fun ProfileAvatar(initials: String) {
    Box(
        modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

// Only one mobile role exists (Field Engineer, see CLAUDE.md) - a static label, not driven by
// CurrentUserProfile.role, same as the design doc's own hardcoded badge text.
@Composable
private fun RoleBadge() {
    Box(
        modifier =
            Modifier
                .padding(top = Spacing.xs)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(Dimens.radiusPill))
                .padding(horizontal = Spacing.sm, vertical = 4.dp),
    ) {
        Text(text = "Tổ khảo sát/sửa chữa", style = LabelTextStyle, color = Color.White)
    }
}

@Composable
private fun ProfileContent(
    profile: CurrentUserProfile,
    isLoggingOut: Boolean,
    deviceModel: String,
    appVersion: String,
    bleConnectionState: BleConnectionState,
    pendingSyncCount: Int,
    errorSyncCount: Int,
    lastSyncedAt: Instant?,
    isOnline: Boolean,
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SettingsGroup(title = "TÀI KHOẢN") {
            SettingsRow(label = "Email", value = profile.email)
            SettingsRow(label = "Địa bàn phụ trách", value = profile.communeIds.joinToString(", "))
        }

        SettingsGroup(title = "SẴN SÀNG LÀM VIỆC") {
            SettingsRow(label = "Kết nối mạng", value = if (isOnline) "Trực tuyến" else "Ngoại tuyến")
            SyncQueueRow(pendingSyncCount = pendingSyncCount, errorSyncCount = errorSyncCount)
            SettingsRow(label = "Lần đồng bộ cuối", value = DateFormatUtils.formatInstant(lastSyncedAt))
            SettingsRow(label = "Cảm biến lux (Bluetooth)", value = bleConnectionLabel(bleConnectionState))
        }

        LogoutSection(isLoggingOut = isLoggingOut, pendingSyncCount = pendingSyncCount, onClick = onLogoutClick)

        SettingsGroup(title = "THIẾT BỊ") {
            SettingsRow(label = "Thiết bị", value = deviceModel)
            SettingsRow(label = "Phiên bản", value = appVersion)
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = title, style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                    .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncQueueRow(
    pendingSyncCount: Int,
    errorSyncCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Hàng đợi đồng bộ", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$pendingSyncCount mục",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (errorSyncCount > 0) {
                StatusBadge(text = "$errorSyncCount lỗi", colors = SyncStatus.FAILED.badgeColors())
            }
        }
    }
}

private fun bleConnectionLabel(state: BleConnectionState): String =
    when (state) {
        BleConnectionState.Connected -> "Đã kết nối"
        BleConnectionState.Connecting -> "Đang kết nối"
        BleConnectionState.Disconnected -> "Chưa kết nối"
    }

@Composable
private fun LogoutSection(
    isLoggingOut: Boolean,
    pendingSyncCount: Int,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Outlined (not filled) red: logout only ends the session on this device, so it should
        // be easy to find but not look like the main action of the screen.
        OutlinedButton(
            onClick = onClick,
            enabled = !isLoggingOut,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Đăng xuất")
            }
        }
        if (pendingSyncCount > 0) {
            Text(
                text = "Còn $pendingSyncCount mục chưa đồng bộ trên thiết bị này",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// "NB" from "Nguyễn Văn Bình" - first letter of the first word + first letter of the last word.
// Falls back to the single token's own first letter (e.g. a bare username while loading/erroring).
private fun initialsOf(name: String): String {
    val tokens = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        tokens.size >= 2 -> "${tokens.first().first()}${tokens.last().first()}".uppercase()
        tokens.size == 1 -> tokens.first().take(1).uppercase()
        else -> "?"
    }
}
