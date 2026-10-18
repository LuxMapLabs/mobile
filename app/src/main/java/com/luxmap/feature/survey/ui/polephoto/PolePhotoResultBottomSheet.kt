package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import kotlin.math.roundToInt

private val StatePlaceholderHeight = 160.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolePhotoResultBottomSheet(
    state: PolePhotoResultUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            Text(text = "Kết quả AI", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.md))

            when (state) {
                is PolePhotoResultUiState.Loading ->
                    Box(
                        modifier =
                            Modifier.fillMaxWidth().height(StatePlaceholderHeight).testTag("pole_photo_result_loading"),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                is PolePhotoResultUiState.Success -> PolePhotoResultContent(state)

                // heightIn (not a fixed height) - MessageState's action button (Error) makes this
                // content taller than the Loading spinner needs, and a fixed height here clipped
                // the button outside the touchable area.
                is PolePhotoResultUiState.Empty ->
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = StatePlaceholderHeight)) {
                        MessageState(
                            icon = Icons.Filled.SmartToy,
                            iconColors = SyncStatus.QUEUED_OFFLINE.badgeColors(),
                            title = "AI chưa phân loại được ảnh này",
                            subtitle = "Model AI gặp lỗi khi xử lý, ảnh vẫn đã lưu trên hệ thống.",
                            action = {},
                        )
                    }

                is PolePhotoResultUiState.Error ->
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = StatePlaceholderHeight)) {
                        MessageState(
                            icon = Icons.Filled.CloudOff,
                            iconColors = SyncStatus.FAILED.badgeColors(),
                            title = "Không tải được kết quả AI",
                            subtitle = state.message,
                            action = { PrimaryButton(text = "Thử lại", onClick = onRetry) },
                        )
                    }
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun PolePhotoResultContent(state: PolePhotoResultUiState.Success) {
    if (state.isStale) {
        InlineBanner(
            message = "Đang xem kết quả đã lưu trên máy, có thể chưa phải bản mới nhất",
            tone = InlineBannerTone.OFFLINE,
        )
        Spacer(Modifier.height(Spacing.sm))
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatusBadge(text = cvStateLabel(state.cvState), colors = cvStateBadgeColors(state.cvState))
        if (state.confidence != null) {
            Text(
                text = "Độ tin cậy ${(state.confidence * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = "Phân tích lúc ${DateFormatUtils.formatInstant(state.analyzedAt)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Spacing.md))

    if (state.decision == null) {
        Text(
            text = "Chưa có quyết định từ Kỹ sư bảo trì",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = if (state.decision == "confirm") "Kỹ sư bảo trì đã xác nhận" else "Kỹ sư bảo trì đã từ chối",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!state.decisionNote.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(text = state.decisionNote, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Lúc ${DateFormatUtils.formatInstant(state.decidedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun cvStateLabel(cvState: String): String =
    when (cvState) {
        "on" -> "Đèn sáng"
        "off" -> "Đèn tắt"
        else -> "Không rõ"
    }

private fun cvStateBadgeColors(cvState: String): BadgeColors =
    when (cvState) {
        "on" -> SyncStatus.DONE.badgeColors()
        "off" -> SyncStatus.FAILED.badgeColors()
        else -> SyncStatus.QUEUED_OFFLINE.badgeColors()
    }
