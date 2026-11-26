package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Spacing

private val StateIllustrationSize = 112.dp
private val StateBadgeSize = 40.dp
private val StateBadgeIconSize = 22.dp

enum class LuxErrorKind { NETWORK, GENERIC }

@Composable
fun LuxEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(icon = icon, title = title, message = message, modifier = modifier) {
        if (actionLabel != null && onAction != null) {
            LuxSecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LuxErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    kind: LuxErrorKind = LuxErrorKind.GENERIC,
    title: String = kind.defaultTitle(),
    message: String = kind.defaultMessage(),
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    StateLayout(
        icon = if (kind == LuxErrorKind.NETWORK) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
        title = title,
        message = message,
        modifier = modifier,
        announce = true,
    ) {
        LuxSecondaryButton(text = "Thử lại", onClick = onRetry, leadingIcon = Icons.Rounded.Refresh)
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            LuxTextButton(text = secondaryActionLabel, onClick = onSecondaryAction)
        }
    }
}

private fun LuxErrorKind.defaultTitle(): String =
    when (this) {
        LuxErrorKind.NETWORK -> "Không có kết nối mạng"
        LuxErrorKind.GENERIC -> "Không tải được dữ liệu"
    }

private fun LuxErrorKind.defaultMessage(): String =
    when (this) {
        LuxErrorKind.NETWORK -> "Dữ liệu đã lưu trên thiết bị vẫn an toàn. Kiểm tra kết nối rồi thử lại."
        LuxErrorKind.GENERIC -> "Dữ liệu trên thiết bị không bị ảnh hưởng. Thử lại sau ít phút."
    }

@Composable
private fun StateLayout(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    announce: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier.size(StateIllustrationSize),
            contentAlignment = Alignment.BottomEnd,
        ) {
            LuxLampIllustration(size = StateIllustrationSize)
            Box(
                modifier =
                    Modifier
                        .size(StateBadgeSize)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(StateBadgeIconSize),
                )
            }
        }
        Column(
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    if (announce) liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        actions()
    }
}

@Composable
fun LuxSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, shape))
}

@Composable
fun LuxLoadingRegion(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        content = content,
    )
}

@LuxPreviews
@Composable
private fun LuxDataStatesPreview() {
    LuxMapTheme {
        Column {
            LuxEmptyState(
                title = "Không có nhiệm vụ sửa chữa",
                message = "Lệnh sửa chữa được giao sẽ hiện ở đây.",
            )
            LuxErrorState(
                onRetry = {},
                kind = LuxErrorKind.NETWORK,
                secondaryActionLabel = "Quay lại",
                onSecondaryAction = {},
            )
            LuxLoadingRegion(description = "Đang tải danh sách lệnh", modifier = Modifier.padding(Spacing.lg)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        LuxSkeleton(modifier = Modifier.size(40.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            LuxSkeleton(modifier = Modifier.width(120.dp).height(14.dp))
                            LuxSkeleton(modifier = Modifier.width(200.dp).height(18.dp))
                        }
                    }
                }
            }
        }
    }
}
