package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val PhotoPreviewHeight = 180.dp
private val StatePlaceholderHeight = 160.dp
private val SkeletonBlockHeight = 60.dp
private val HeaderBadgeSize = 44.dp
private val photoOverlayFormatter = DateTimeFormatter.ofPattern("dd/MM · HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolePhotoResultBottomSheet(
    poleId: String,
    position: Int,
    state: PolePhotoResultUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRetake: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PolePhotoResultHeader(poleId = poleId, position = position, onDismiss = onDismiss)

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                when (state) {
                    is PolePhotoResultUiState.Loading -> PolePhotoResultSkeleton()

                    is PolePhotoResultUiState.Success -> PolePhotoResultContent(state, onRetake)

                    // heightIn (not a fixed height) - MessageState's action button (Error) makes this
                    // content taller than the skeleton needs, and a fixed height here clipped
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
}

// Shown for every state (loading/success/empty/error), not only Success - the pole being
// reviewed and the ability to close the sheet don't depend on whether its AI result loaded.
@Composable
private fun PolePhotoResultHeader(
    poleId: String,
    position: Int,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(HeaderBadgeSize)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.control)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "#$position",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "KẾT QUẢ AI", style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = poleId, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng")
        }
    }
}

// Mirrors the Success content's layout (photo + 2 info blocks + a decision-size block) so the
// sheet doesn't visibly reflow once the real result swaps in.
@Composable
private fun PolePhotoResultSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("pole_photo_result_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PhotoPreviewHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(SkeletonBlockHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card)),
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(SkeletonBlockHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card)),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.minTouchTarget + Spacing.md)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card)),
        )
    }
}

@Composable
private fun PolePhotoResultContent(
    state: PolePhotoResultUiState.Success,
    onRetake: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PolePhotoPreview(state)

        if (state.isStale) {
            InlineBanner(
                message = "Đang xem kết quả đã lưu trên máy, có thể chưa phải bản mới nhất",
                tone = InlineBannerTone.OFFLINE,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AiVerdictCard(cvState = state.cvState, modifier = Modifier.weight(1f))
            if (state.confidence != null) {
                ConfidenceCard(confidence = state.confidence, modifier = Modifier.weight(1f))
            }
        }

        Text(
            text = "Phân tích lúc ${DateFormatUtils.formatInstant(state.analyzedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PolePhotoDecisionCard(state)

        RetakeButton(onClick = onRetake, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PolePhotoPreview(state: PolePhotoResultUiState.Success) {
    Box(modifier = Modifier.fillMaxWidth().height(PhotoPreviewHeight)) {
        AsyncImage(
            model = File(state.filePath),
            contentDescription = "Ảnh cột đèn đã chụp",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.card)),
        )
        if (state.analyzedAt != null) {
            Text(
                text = state.analyzedAt.atZone(ZoneId.systemDefault()).format(photoOverlayFormatter),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(Spacing.sm)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(Radius.badge))
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun AiVerdictCard(
    cvState: String,
    modifier: Modifier = Modifier,
) {
    val colors = cvStateBadgeColors(cvState)
    Column(
        modifier = modifier.background(colors.background, RoundedCornerShape(Radius.card)).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = "AI NHẬN ĐỊNH", style = OverlineTextStyle, color = colors.text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(18.dp),
            )
            Text(text = cvStateLabel(cvState), style = MaterialTheme.typography.titleMedium, color = colors.text)
        }
    }
}

@Composable
private fun ConfidenceCard(
    confidence: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = "ĐỘ TIN CẬY", style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = "Độ tin cậy ${(confidence * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { confidence.toFloat() },
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

// Separate from BadgeColors (background + text only) - the decision card also needs its own
// border color, which no existing shared token provides.
private data class DecisionTone(
    val background: Color,
    val border: Color,
    val foreground: Color,
    val icon: ImageVector,
    val title: String,
)

private fun decisionTone(decision: String?): DecisionTone =
    when (decision) {
        "confirm" ->
            DecisionTone(
                background = Color(0xFFF0FAF6),
                border = Color(0xFFBFE6D6),
                foreground = Color(0xFF1E6B5C),
                icon = Icons.Filled.Verified,
                title = "Kỹ sư bảo trì đã xác nhận",
            )
        "reject" ->
            DecisionTone(
                background = Color(0xFFFEF2F2),
                border = Color(0xFFFECACA),
                foreground = Color(0xFF991B1B),
                icon = Icons.Filled.Block,
                title = "Kỹ sư bảo trì đã từ chối",
            )
        else ->
            DecisionTone(
                background = Color(0xFFF8FAFC),
                border = Color(0xFFE2E8F0),
                foreground = Color(0xFF64748B),
                icon = Icons.Filled.HourglassEmpty,
                title = "Chưa có quyết định từ Kỹ sư bảo trì",
            )
    }

@Composable
private fun PolePhotoDecisionCard(state: PolePhotoResultUiState.Success) {
    val tone = decisionTone(state.decision)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(tone.background, RoundedCornerShape(Radius.card))
                .border(1.dp, tone.border, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(imageVector = tone.icon, contentDescription = null, tint = tone.foreground)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = tone.title, style = MaterialTheme.typography.titleSmall)
            if (!state.decisionNote.isNullOrBlank()) {
                Text(text = state.decisionNote, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.decision != null) {
                Text(
                    text = "Lúc ${DateFormatUtils.formatInstant(state.decidedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RetakeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        shape = RoundedCornerShape(Radius.control),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text = "Chụp lại cột này")
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
