package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun luxTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuxTabTopBar(
    title: String,
    unreadNotificationCount: Int,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .heightIn(min = 64.dp)
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleBlock(title = title, subtitle = subtitle, isTabRoot = true, modifier = Modifier.weight(1f))
            NotificationAction(unreadCount = unreadNotificationCount, onClick = onNotificationsClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuxChildTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = { TitleBlock(title = title, subtitle = subtitle, isTabRoot = false) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }
        },
        actions = actions,
        colors = luxTopBarColors(),
    )
}

@Composable
private fun TitleBlock(
    title: String,
    subtitle: String?,
    isTabRoot: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = if (isTabRoot) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationAction(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val description = if (unreadCount > 0) "Thông báo, $unreadCount chưa đọc" else "Thông báo"
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(modifier = Modifier.testTag("notification_badge").atLeastAsWideAsTall()) {
                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                    }
                }
            },
        ) {
            Icon(imageVector = Icons.Outlined.Notifications, contentDescription = description)
        }
    }
}

@LuxPreviews
@Composable
private fun LuxTopBarsPreview() {
    LuxMapTheme {
        Column {
            LuxTabTopBar(
                title = "Việc hôm nay",
                subtitle = "Thứ Hai, 16/11/2026",
                unreadNotificationCount = 3,
                onNotificationsClick = {},
            )
            LuxChildTopBar(title = "Lệnh sửa chữa", subtitle = "WO-1042", onBack = {})
        }
    }
}

private fun Modifier.atLeastAsWideAsTall(): Modifier =
    layout { measurable, constraints ->
        val height = measurable.minIntrinsicHeight(constraints.maxWidth)
        val minWidth = height.coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = minWidth))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
