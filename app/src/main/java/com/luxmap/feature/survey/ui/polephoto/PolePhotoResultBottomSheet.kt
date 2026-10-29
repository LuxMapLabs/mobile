package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.luxmap.core.theme.BrandHeroGradient
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

// Matches the captured-photo aspect ratio used right after taking the shot
// (PolePhotoCaptureScreen) - a fixed height here would crop a portrait photo down to a sliver.
private const val PHOTO_ASPECT_RATIO = 3f / 4f
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
    onSubmitReview: (finalCvState: String, reviewNote: String?) -> Unit = { _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PolePhotoResultHeader(poleId = poleId, position = position, onDismiss = onDismiss)

            // The photo now shows at its real 3:4 aspect ratio (tall), plus ReviewCard added more
            // content below it, so this can now reach the bottom of the screen on real devices -
            // navigationBarsPadding keeps the last button (retake/submit) clear of the system
            // nav bar area instead of its tap target sitting flush against it.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        .navigationBarsPadding(),
            ) {
                when (state) {
                    is PolePhotoResultUiState.Loading -> PolePhotoResultSkeleton()

                    is PolePhotoResultUiState.Success ->
                        PolePhotoResultContent(poleId, state, onRetake, onSubmitReview)

                    // heightIn (not a fixed height) - MessageState's action button (Error) makes this
                    // content taller than the skeleton needs, and a fixed height here clipped
                    // the button outside the touchable area.
                    // Defensive-only branch - see PolePhotoResultUiState.Empty's own comment.
                    // A real "AI model failed" result (ai_status = "unavailable") now renders as
                    // Success with cvState = null instead, so review stays possible.
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
// Navy band (Khao Sat v4 - Ket Qua AI mock, CLAUDE.md 2026-10-30) matching the mock's hero -
// stays a bottom sheet (not a full screen/route), so the X here still just dismisses it, same
// as before the restyle.
@Composable
private fun PolePhotoResultHeader(
    poleId: String,
    position: Int,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient, RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(HeaderBadgeSize)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(Radius.control)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "#$position", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "KẾT QUẢ AI", style = OverlineTextStyle, color = Color.White.copy(alpha = 0.75f))
            Text(text = poleId, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng", tint = Color.White)
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
                    .aspectRatio(PHOTO_ASPECT_RATIO)
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
    poleId: String,
    state: PolePhotoResultUiState.Success,
    onRetake: () -> Unit,
    onSubmitReview: (finalCvState: String, reviewNote: String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PolePhotoPreview(state)

        if (state.isStale) {
            InlineBanner(
                message = "Đang xem kết quả đã lưu trên máy, có thể chưa phải bản mới nhất",
                tone = InlineBannerTone.OFFLINE,
            )
        }

        AiResultCard(cvState = state.cvState, confidence = state.confidence)

        if (state.analyzedAt != null) {
            Text(
                text = "Phân tích lúc ${DateFormatUtils.formatInstant(state.analyzedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Locked once a Manager has already decided (POLE_PHOTO_ALREADY_DECIDED) - review no
        // longer applies, the decision card below already shows the published outcome.
        if (state.decision == null) {
            ReviewCard(poleId = poleId, state = state, onSubmit = onSubmitReview)
        }

        PolePhotoDecisionCard(state)

        RetakeButton(onClick = onRetake, modifier = Modifier.fillMaxWidth())
    }
}

// Lets the Field Engineer correct/confirm the AI's cvState before a Manager decides (ticket
// PHOTO-REVIEW, 2026-10-20). Keyed on poleId so switching to a different pole's sheet resets the
// draft instead of carrying over a stale selection from the previous photo.
@Composable
private fun ReviewCard(
    poleId: String,
    state: PolePhotoResultUiState.Success,
    onSubmit: (finalCvState: String, reviewNote: String?) -> Unit,
) {
    var selected by
        rememberSaveable(poleId) {
            mutableStateOf(state.finalCvState ?: state.cvState?.takeIf { it == "on" || it == "off" })
        }
    var note by rememberSaveable(poleId) { mutableStateOf(state.reviewNote.orEmpty()) }
    // Required whenever the chosen label differs from the AI's own cvState, including when
    // cvState is "inconclusive" or null (server rule, see docs/contract-drift.md "PATCH
    // .../review" - review_note is mandatory even when AI never reached on/off or failed
    // outright, not only when it disagrees).
    val noteRequired = selected != null && selected != state.cvState
    val canSubmit = selected != null && !state.isSubmittingReview && (!noteRequired || note.isNotBlank())

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "KỸ SƯ XÁC NHẬN NHÃN",
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ReviewChoiceSegment(selected = selected, onSelected = { selected = it })

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Ghi chú") },
            isError = noteRequired && note.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (noteRequired && note.isBlank()) {
            Text(
                text = "Cần ghi chú vì khác với nhận định của AI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.reviewedBy != null) {
            Text(
                text = "Đã review lúc ${DateFormatUtils.formatInstant(state.reviewedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.reviewError != null) {
            Text(
                text = state.reviewError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        PrimaryButton(
            text = if (state.isSubmittingReview) "Đang gửi..." else "Gửi xác nhận",
            onClick = { selected?.let { onSubmit(it, note.trim().ifBlank { null }) } },
            enabled = canSubmit,
            isLoading = state.isSubmittingReview,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// 2 equal chips in a gray tray, same visual pattern as OutcomeSegment (F09/F10) - a separate
// component rather than generalizing that one, since its 3 options/colors are specific to
// fault_outcomes and not meant to be reused as a generic 2-choice control.
@Composable
private fun ReviewChoiceSegment(
    selected: String?,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.chip))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("on" to "Đèn sáng", "off" to "Đèn tắt").forEach { (value, label) ->
            val isSelected = selected == value
            val colors = cvStateBadgeColors(value)
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (isSelected) colors.background else Color.Transparent,
                            RoundedCornerShape(Radius.chip),
                        )
                        .clickable(onClick = { onSelected(value) })
                        .testTag("review_choice_$value")
                        .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.text else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PolePhotoPreview(state: PolePhotoResultUiState.Success) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(PHOTO_ASPECT_RATIO)) {
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

// Single card (Khao Sat v4 - Ket Qua AI mock, CLAUDE.md 2026-10-30) replacing the previous
// side-by-side AiVerdictCard + ConfidenceCard - the mock groups the AI's label and its
// confidence bar into one block instead of 2 separate cards.
@Composable
private fun AiResultCard(
    cvState: String?,
    confidence: Double?,
) {
    val colors = cvStateBadgeColors(cvState)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "AI NHẬN ĐỊNH", style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                modifier = Modifier.size(44.dp).background(colors.background, RoundedCornerShape(Radius.control)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Lightbulb, contentDescription = null, tint = colors.text)
            }
            Text(
                text = cvStateLabel(cvState),
                style = MaterialTheme.typography.titleMedium,
                // Distinguishes this from ReviewChoiceSegment's chips, which can show the same
                // "Đèn sáng"/"Đèn tắt" text at the same time once ReviewCard is visible.
                modifier = Modifier.testTag("ai_verdict_label"),
            )
        }
        if (confidence != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                LinearProgressIndicator(
                    progress = { confidence.toFloat() },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "Tin cậy ${(confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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

private fun cvStateLabel(cvState: String?): String =
    when (cvState) {
        "on" -> "Đèn sáng"
        "off" -> "Đèn tắt"
        "inconclusive" -> "AI không chắc"
        null -> "AI không xử lý được"
        else -> "Không rõ"
    }

private fun cvStateBadgeColors(cvState: String?): BadgeColors =
    when (cvState) {
        "on" -> SyncStatus.DONE.badgeColors()
        "off" -> SyncStatus.FAILED.badgeColors()
        else -> SyncStatus.QUEUED_OFFLINE.badgeColors()
    }
