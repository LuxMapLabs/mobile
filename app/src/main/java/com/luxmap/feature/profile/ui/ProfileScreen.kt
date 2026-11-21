package com.luxmap.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.communeLabel
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.LuxDestructiveButton
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.LuxTabTopBar
import com.luxmap.feature.profile.data.CurrentUserProfile
import java.time.Instant

@Composable
fun ProfileRoute(
    unreadNotificationCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profileState by viewModel.profileState.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val errorSyncCount by viewModel.errorSyncCount.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    ProfileScreen(
        profileState = profileState,
        isLoggingOut = viewModel.isLoggingOut,
        pendingSyncCount = pendingSyncCount,
        errorSyncCount = errorSyncCount,
        lastSyncedAt = lastSyncedAt,
        isOnline = isOnline,
        onConfirmLogout = viewModel::onConfirmLogout,
        unreadNotificationCount = unreadNotificationCount,
        onOpenNotifications = onOpenNotifications,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun ProfileScreen(
    profileState: ProfileUiState,
    isLoggingOut: Boolean,
    pendingSyncCount: Int,
    errorSyncCount: Int,
    lastSyncedAt: Instant?,
    isOnline: Boolean,
    onConfirmLogout: () -> Unit,
    unreadNotificationCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LuxTabTopBar(
                title = "Cá nhân",
                unreadNotificationCount = unreadNotificationCount,
                onNotificationsClick = onOpenNotifications,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (profileState) {
                is ProfileUiState.Loading ->
                    LuxLoadingRegion(
                        description = "Đang tải thông tin cá nhân",
                        modifier = Modifier.padding(Spacing.lg),
                    ) {
                        LuxSkeleton(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            shape = MaterialTheme.shapes.medium,
                        )
                        LuxSkeleton(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                is ProfileUiState.Success ->
                    ProfileBody(
                        isLoggingOut = isLoggingOut,
                        pendingSyncCount = pendingSyncCount,
                        errorSyncCount = errorSyncCount,
                        lastSyncedAt = lastSyncedAt,
                        isOnline = isOnline,
                        onLogoutClick = { showConfirmDialog = true },
                    ) {
                        ProfileCard(profileState.profile)
                    }
                is ProfileUiState.Error ->
                    ProfileBody(
                        isLoggingOut = isLoggingOut,
                        pendingSyncCount = pendingSyncCount,
                        errorSyncCount = errorSyncCount,
                        lastSyncedAt = lastSyncedAt,
                        isOnline = isOnline,
                        onLogoutClick = { showConfirmDialog = true },
                    ) {
                        LuxErrorState(
                            onRetry = onRetry,
                            kind = if (profileState.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                            title = "Không tải được thông tin cá nhân",
                            message =
                                if (profileState.isNetworkError) {
                                    "Kiểm tra kết nối rồi thử lại."
                                } else {
                                    "Vui lòng thử lại sau ít phút."
                                },
                        )
                    }
            }
        }
    }
    if (showConfirmDialog) {
        LuxConfirmDialog(
            title = "Đăng xuất khỏi thiết bị này?",
            message = "Bạn cần đăng nhập lại để tiếp tục sử dụng ứng dụng.",
            confirmLabel = "Đăng xuất",
            onConfirm = {
                showConfirmDialog = false
                onConfirmLogout()
            },
            onDismiss = { showConfirmDialog = false },
            destructive = true,
        )
    }
}

@Composable
private fun ProfileBody(
    isLoggingOut: Boolean,
    pendingSyncCount: Int,
    errorSyncCount: Int,
    lastSyncedAt: Instant?,
    isOnline: Boolean,
    onLogoutClick: () -> Unit,
    header: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        header()
        StatusSection(
            isOnline = isOnline,
            pendingSyncCount = pendingSyncCount,
            errorSyncCount = errorSyncCount,
            lastSyncedAt = lastSyncedAt,
        )
        LogoutSection(isLoggingOut = isLoggingOut, pendingSyncCount = pendingSyncCount, onLogoutClick = onLogoutClick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(profile: CurrentUserProfile) {
    LuxCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initialsOf(profile.fullName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = profile.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = "Email",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.md),
                )
                Text(text = profile.email, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = "Địa bàn phụ trách (${profile.communeIds.size})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                profile.communeIds.forEach { communeId ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = communeLabel(communeId, profile.communeNames[communeId]),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSection(
    isOnline: Boolean,
    pendingSyncCount: Int,
    errorSyncCount: Int,
    lastSyncedAt: Instant?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LuxSectionHeader(title = "Tình trạng làm việc")
        LuxCard {
            Column {
                StatusRow(
                    icon = if (isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    label = "Kết nối mạng",
                    value = if (isOnline) "Trực tuyến" else "Ngoại tuyến",
                    valueColor = if (isOnline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatusRow(
                    icon = Icons.Filled.Sync,
                    label = "Đồng bộ dữ liệu",
                    value = profileSyncSummary(pendingSyncCount, errorSyncCount, lastSyncedAt),
                    valueColor =
                        if (errorSyncCount > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(
            modifier = Modifier.padding(end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun LogoutSection(
    isLoggingOut: Boolean,
    pendingSyncCount: Int,
    onLogoutClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LuxDestructiveButton(
            text = if (isLoggingOut) "Đang đăng xuất…" else "Đăng xuất",
            onClick = onLogoutClick,
            filled = false,
            enabled = !isLoggingOut,
            modifier = Modifier.fillMaxWidth(),
        )
        if (pendingSyncCount > 0) {
            Text(
                text = "Còn $pendingSyncCount mục chưa đồng bộ trên thiết bị này",
                style = MaterialTheme.typography.bodySmall,
                color = LuxTheme.colors.warning,
            )
        }
    }
}

private fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first().take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}
