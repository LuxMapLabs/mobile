package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.ui.components.LuxBottomSheet
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import java.io.File

private val TileSize = 88.dp
private val RemoveTouchTarget = 48.dp
private val RemoveVisualSize = 28.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompletionEvidenceSection(
    title: String,
    required: Boolean,
    photos: List<LocalWorkOrderEvidenceEntity>,
    editable: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CompletionFieldHeader(title = title, required = required)
        if (photos.isNotEmpty()) {
            Text(
                text = "${photos.size} ảnh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            photos.forEachIndexed { index, photo ->
                PhotoTile(
                    photo = photo,
                    index = index,
                    removable = editable && photo.uploadStatus == "draft",
                    onOpen = { previewId = photo.clientOpId },
                    onRemove = { onRemove(photo.clientOpId) },
                )
            }
            if (editable) AddPhotoTile(onClick = onAdd)
        }
    }
    photos.firstOrNull { it.clientOpId == previewId }?.let { photo ->
        LuxBottomSheet(title = title, onDismiss = { previewId = null }) {
            AsyncImage(
                model = File(photo.filePath),
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun PhotoTile(
    photo: LocalWorkOrderEvidenceEntity,
    index: Int,
    removable: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val state = photo.uploadStatus.toTileState()
    val badge = state.colors()
    Column(modifier = Modifier.width(TileSize), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(modifier = Modifier.size(TileSize)) {
            AsyncImage(
                model = File(photo.filePath),
                contentDescription = "Xem ảnh ${index + 1}",
                modifier =
                    Modifier
                        .size(TileSize)
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button, onClick = onOpen),
                contentScale = ContentScale.Crop,
            )
            if (state.sync != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(Spacing.xs)
                            .size(RemoveVisualSize)
                            .background(badge.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = state.icon,
                        contentDescription = null,
                        tint = badge.text,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (removable) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(RemoveTouchTarget)
                            .clickable(role = Role.Button, onClick = onRemove)
                            .semantics { contentDescription = "Bỏ ảnh ${index + 1}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(RemoveVisualSize)
                                .background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        if (state.sync != null) {
            Text(
                text = state.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier =
            Modifier
                .size(TileSize)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick)
                .drawBehind {
                    drawRoundRect(
                        color = outline,
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style =
                            Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                            ),
                    )
                },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "Thêm ảnh",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private data class TileState(
    val label: String,
    val icon: ImageVector,
    val sync: SyncStatus?,
)

@Composable
private fun TileState.colors(): BadgeColors =
    sync?.badgeColors()
        ?: BadgeColors(
            background = MaterialTheme.colorScheme.surfaceContainerHighest,
            text = MaterialTheme.colorScheme.onSurfaceVariant,
        )

@Composable
private fun String.toTileState(): TileState =
    when (this) {
        "synced" -> TileState("Đã đồng bộ", SyncStatus.DONE.icon(), SyncStatus.DONE)
        "pending" -> TileState("Chờ đồng bộ", SyncStatus.QUEUED_ONLINE.icon(), SyncStatus.QUEUED_ONLINE)
        "syncing" -> TileState("Đang tải lên", SyncStatus.SYNCING.icon(), SyncStatus.SYNCING)
        "retryable_error" -> TileState("Tải lỗi", SyncStatus.FAILED.icon(), SyncStatus.FAILED)
        "replaceable_error" -> TileState("Cần thay ảnh", SyncStatus.FAILED.icon(), SyncStatus.FAILED)
        "failed" -> TileState("Không tải được", SyncStatus.FAILED.icon(), SyncStatus.FAILED)
        "conflict" -> TileState("Xung đột", SyncStatus.CONFLICT.icon(), SyncStatus.CONFLICT)
        else -> TileState("Ảnh nháp", Icons.Filled.EditNote, null)
    }
