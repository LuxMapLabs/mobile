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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.StatusBadge
import java.time.Instant

@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val username by viewModel.username.collectAsState()
    val bleConnectionState by viewModel.bleConnectionState.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val errorSyncCount by viewModel.errorSyncCount.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    ProfileScreen(
        // Null only for a session saved before the app kept the username.
        username = username ?: "Tài khoản",
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

@Composable
fun ProfileScreen(
    username: String,
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

    Column(
        modifier =
            modifier
                .fillMaxSize()
                // Opaque background so nothing behind this screen (for example the map) can show
                // through.
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(text = "Cá nhân", style = MaterialTheme.typography.titleLarge)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(username = username)
            Spacer(Modifier.height(Spacing.md))
            Text(text = username, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Tổ khảo sát/sửa chữa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroup(title = "THIẾT BỊ") {
            SettingsRow(label = "Thiết bị", value = deviceModel)
            SettingsRow(label = "Cảm biến lux (Bluetooth)", value = bleConnectionLabel(bleConnectionState))
            SettingsRow(label = "Bản đồ ngoại tuyến", value = "Chưa hỗ trợ trong phiên bản này")
        }

        SettingsGroup(title = "ĐỒNG BỘ") {
            SyncQueueRow(pendingSyncCount = pendingSyncCount, errorSyncCount = errorSyncCount)
            SettingsRow(label = "Lần đồng bộ cuối", value = DateFormatUtils.formatInstant(lastSyncedAt))
            SettingsRow(label = "Kết nối mạng", value = if (isOnline) "Online" else "Offline")
        }

        SettingsGroup(title = "ỨNG DỤNG") {
            SettingsRow(label = "Giao diện", value = "Theo hệ thống")
            SettingsRow(label = "Thông báo", value = "Chưa hỗ trợ trong phiên bản này")
            SettingsRow(label = "Phiên bản", value = appVersion)
        }

        // Outlined (not filled) red: logout only ends the session on this device, so it should
        // be easy to find but not look like the main action of the screen.
        OutlinedButton(
            onClick = { showConfirmDialog = true },
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
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
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

// Round avatar showing the first letter of the username (no profile photo in the data yet).
@Composable
private fun Avatar(username: String) {
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
