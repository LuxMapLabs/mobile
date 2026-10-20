package com.luxmap.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.Success600
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.profile.data.CurrentUserProfile
import java.time.Instant

// Exact hex from the F15 redesign doc ("ĐỀ XUẤT THEO HƯỚNG 2a") - no existing shared token matches
// this amber, same reasoning as the per-screen bespoke colors in PolePhotoResultBottomSheet.
private val AttentionColor = Color(0xFFB45309)
private val PendingCaptionColor = Color(0xFF92400E)

// Icon-chip size + the row's own Spacing.md gap, so the stat grid below a row lines up under its
// text instead of its icon.
private val SyncDetailIndent = 48.dp

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

// F15 Cá nhân - "Đề xuất theo hướng 2a": hero carries identity (name/username/role), the sheet
// below groups network/sync/lux sensor under one "Sẵn sàng làm việc" block, and device/version
// move to a plain footer line - replacing the old flat settings-list layout.
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        AccountSection(profile)
        ReadinessSection(
            bleConnectionState = bleConnectionState,
            pendingSyncCount = pendingSyncCount,
            errorSyncCount = errorSyncCount,
            lastSyncedAt = lastSyncedAt,
            isOnline = isOnline,
        )
        LogoutSection(isLoggingOut = isLoggingOut, pendingSyncCount = pendingSyncCount, onClick = onLogoutClick)
        FooterInfo(deviceModel = deviceModel, appVersion = appVersion)
    }
}

@Composable
private fun AccountSection(profile: CurrentUserProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = "TÀI KHOẢN", style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.fillMaxWidth().cardOutline()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    Text(
                        text = "Email",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = profile.email, style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "Địa bàn phụ trách · ${profile.communeIds.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CommuneChips(communeIds = profile.communeIds)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommuneChips(communeIds: List<String>) {
    val colors = SyncStatus.QUEUED_ONLINE.badgeColors()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        communeIds.forEach { communeId ->
            Box(
                modifier =
                    Modifier
                        .background(colors.background, RoundedCornerShape(Radius.badge))
                        .padding(horizontal = Spacing.sm, vertical = 4.dp),
            ) {
                Text(text = communeId, style = MaterialTheme.typography.labelLarge, color = colors.text)
            }
        }
    }
}

@Composable
private fun ReadinessSection(
    bleConnectionState: BleConnectionState,
    pendingSyncCount: Int,
    errorSyncCount: Int,
    lastSyncedAt: Instant?,
    isOnline: Boolean,
) {
    val issues =
        (if (!isOnline) 1 else 0) +
            (if (errorSyncCount > 0) 1 else 0) +
            (if (bleConnectionState != BleConnectionState.Connected) 1 else 0)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SẴN SÀNG LÀM VIỆC",
                style = OverlineTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val readyColor = if (issues == 0) Success600 else AttentionColor
            val readyLabel = if (issues == 0) "Sẵn sàng" else "$issues mục cần kiểm tra"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(8.dp).background(readyColor, CircleShape))
                Text(text = readyLabel, style = MaterialTheme.typography.labelMedium, color = readyColor)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().cardOutline()) {
            NetworkRow(isOnline = isOnline)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SyncRow(pendingSyncCount = pendingSyncCount, errorSyncCount = errorSyncCount, lastSyncedAt = lastSyncedAt)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            BleRow(state = bleConnectionState)
        }
    }
}

@Composable
private fun NetworkRow(isOnline: Boolean) {
    val colors = if (isOnline) SyncStatus.DONE.badgeColors() else SyncStatus.QUEUED_OFFLINE.badgeColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconChip(icon = if (isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff, colors = colors)
        Text(text = "Kết nối mạng", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        StatusBadge(text = if (isOnline) "Trực tuyến" else "Ngoại tuyến", colors = colors)
    }
}

@Composable
private fun SyncRow(
    pendingSyncCount: Int,
    errorSyncCount: Int,
    lastSyncedAt: Instant?,
) {
    val colors =
        when {
            errorSyncCount > 0 -> SyncStatus.FAILED.badgeColors()
            pendingSyncCount > 0 -> SyncStatus.QUEUED_ONLINE.badgeColors()
            else -> SyncStatus.DONE.badgeColors()
        }
    val icon =
        when {
            errorSyncCount > 0 -> Icons.Filled.ErrorOutline
            pendingSyncCount > 0 -> Icons.Filled.Sync
            else -> Icons.Filled.CloudDone
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconChip(icon = icon, colors = colors)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Đồng bộ dữ liệu", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Lần cuối: ${DateFormatUtils.formatInstant(lastSyncedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = SyncDetailIndent),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatCell(label = "Chờ đồng bộ", value = pendingSyncCount, isError = false, modifier = Modifier.weight(1f))
            StatCell(
                label = "Đồng bộ lỗi",
                value = errorSyncCount,
                isError = errorSyncCount > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BleRow(state: BleConnectionState) {
    val (icon, colors, label) =
        when (state) {
            BleConnectionState.Connected ->
                Triple(
                    Icons.Filled.BluetoothConnected,
                    SyncStatus.DONE.badgeColors(),
                    "Đã kết nối",
                )
            BleConnectionState.Connecting ->
                Triple(Icons.Filled.BluetoothSearching, SyncStatus.QUEUED_ONLINE.badgeColors(), "Đang kết nối")
            BleConnectionState.Disconnected ->
                Triple(Icons.Filled.BluetoothDisabled, SyncStatus.QUEUED_OFFLINE.badgeColors(), "Chưa kết nối")
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconChip(icon = icon, colors = colors)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Cảm biến lux", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Bluetooth · cần cho khảo sát video",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusBadge(text = label, colors = colors)
    }
}

@Composable
private fun IconChip(
    icon: ImageVector,
    colors: BadgeColors,
) {
    Box(
        modifier = Modifier.size(36.dp).background(colors.background, RoundedCornerShape(Radius.control)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatCell(
    label: String,
    value: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors =
        if (isError) {
            SyncStatus.FAILED.badgeColors()
        } else {
            BadgeColors(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    Column(modifier = modifier.background(colors.background, RoundedCornerShape(Radius.control)).padding(Spacing.sm)) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) colors.text else MaterialTheme.colorScheme.onSurface,
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.text)
    }
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
                Icon(imageVector = Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Đăng xuất")
            }
        }
        if (pendingSyncCount > 0) {
            Text(
                text = "Còn $pendingSyncCount mục chưa đồng bộ trên thiết bị này",
                style = MaterialTheme.typography.labelMedium,
                color = PendingCaptionColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FooterInfo(
    deviceModel: String,
    appVersion: String,
) {
    Text(
        text = "$deviceModel · Phiên bản $appVersion",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Modifier.cardOutline(): Modifier =
    this
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))

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
