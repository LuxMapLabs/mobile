package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.LuxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun luxTopBarColors(): TopAppBarColors {
    val brand = LuxTheme.brand
    return TopAppBarDefaults.topAppBarColors(
        containerColor = brand.hero,
        scrolledContainerColor = brand.hero,
        titleContentColor = brand.onHero,
        navigationIconContentColor = brand.onHero,
        actionIconContentColor = brand.onHero,
    )
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
    LightStatusBarIcons()
    TopAppBar(
        modifier = modifier,
        title = {
            TitleBlock(
                title = title,
                subtitle = subtitle,
                subtitleColor = LuxTheme.brand.onHeroVariant,
            )
        },
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
    subtitleColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        }
    }
}

@Composable
internal fun NotificationAction(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val description = if (unreadCount > 0) "Thông báo, $unreadCount chưa đọc" else "Thông báo"
    Box {
        IconButton(onClick = onClick) {
            Icon(imageVector = Icons.Outlined.Notifications, contentDescription = description)
        }
        if (unreadCount > 0) {
            Badge(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 1.dp)
                        .testTag("notification_badge")
                        .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                        .clearAndSetSemantics {},
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@LuxPreviews
@Composable
private fun LuxTopBarsPreview() {
    LuxMapTheme {
        Column {
            LuxChildTopBar(title = "Lệnh sửa chữa", subtitle = "WO-1042", onBack = {})
        }
    }
}
