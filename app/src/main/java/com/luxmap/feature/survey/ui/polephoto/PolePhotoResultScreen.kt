package com.luxmap.feature.survey.ui.polephoto

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.luxmap.BuildConfig
import com.luxmap.R
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dark950
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
import com.luxmap.core.ui.components.TopSlideToast
import com.luxmap.core.ui.components.TopToastState
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PHOTO_HEIGHT_FRACTION = 0.38f
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
    onRequestAuthorizationHeader: suspend () -> String? = { null },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // ModalBottomSheet renders in its own Android Window - a toast shown anywhere outside this
    // composable (e.g. over the trigger screen behind it) stays hidden until the sheet closes
    // (found on a real device, 2026-11-11). This toast lives inside the sheet's own content so
    // the review button gets visible feedback immediately, without closing the sheet first.
    val toastState = remember { TopToastState() }
    val scope = rememberCoroutineScope()
    val reviewable = state as? PolePhotoResultUiState.Success
    val showFooter = reviewable != null && reviewable.decision == null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PolePhotoResultHeader(poleId = poleId, position = position, onDismiss = onDismiss)

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                            .then(if (showFooter) Modifier else Modifier.navigationBarsPadding()),
                ) {
                    when (state) {
                        is PolePhotoResultUiState.Loading -> PolePhotoResultSkeleton()

                        is PolePhotoResultUiState.Success ->
                            PolePhotoResultBody(poleId, state, onRetake, onRequestAuthorizationHeader)

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

                if (reviewable != null && showFooter) {
                    ReviewFooter(
                        poleId = poleId,
                        state = reviewable,
                        onSubmit = { finalCvState, reviewNote ->
                            scope.launch { toastState.show("Đã lưu, sẽ gửi khi Hoàn tất") }
                            onSubmitReview(finalCvState, reviewNote)
                        },
                    )
                }
            }
            TopSlideToast(
                state = toastState,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
    }
}

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

@Composable
private fun photoHeight() = (LocalConfiguration.current.screenHeightDp * PHOTO_HEIGHT_FRACTION).dp

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
                    .height(photoHeight())
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SkeletonBlockHeight + Spacing.lg)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.minTouchTarget)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card)),
        )
    }
}

@Composable
private fun PolePhotoResultBody(
    poleId: String,
    state: PolePhotoResultUiState.Success,
    onRetake: () -> Unit,
    onRequestAuthorizationHeader: suspend () -> String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (state.isStale) {
            InlineBanner(
                message = "Đang xem kết quả đã lưu trên máy, có thể chưa phải bản mới nhất",
                tone = InlineBannerTone.OFFLINE,
            )
        }

        if (state.decision != null) {
            PolePhotoDecisionCard(state)
        }

        PolePhotoPreview(state, onRequestAuthorizationHeader)

        ConclusionCard(state)

        TechnicalDetails(poleId = poleId, state = state, onRetake = onRetake)
    }
}

@Composable
private fun cvStateColors(cvState: String?): BadgeColors {
    val condition =
        when (cvState) {
            "on" -> AssetCondition.NORMAL
            "off" -> AssetCondition.OUT
            else -> AssetCondition.UNKNOWN
        }
    return condition.badgeColors(isSystemInDarkTheme())
}

@Composable
private fun ConclusionCard(state: PolePhotoResultUiState.Success) {
    val reviewed = state.draftFinalCvState ?: state.finalCvState
    val effective = effectiveCvState(state.cvState, reviewed)
    val colors = cvStateColors(effective)
    val band = confidenceBand(state.confidence)
    val aiConclusive = state.cvState == "on" || state.cvState == "off"
    val bandCondition =
        when (band) {
            ConfidenceBand.HIGH -> AssetCondition.NORMAL
            ConfidenceBand.MEDIUM -> AssetCondition.DIM
            ConfidenceBand.LOW -> AssetCondition.OUT
        }
    val bandColors = bandCondition.badgeColors(isSystemInDarkTheme())
    val confidenceText = confidenceLabel(state.confidence)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "KẾT LUẬN", style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                modifier = Modifier.size(44.dp).background(colors.background, RoundedCornerShape(Radius.control)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Lightbulb, contentDescription = null, tint = colors.text)
            }
            Text(
                text = effective?.let(::cvStateLabel) ?: "Chưa có kết luận",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("ai_verdict_label"),
            )
        }
        Text(
            text = verdictSourceText(state.cvState, reviewed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (aiConclusive && confidenceText != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(bandColors.background, RoundedCornerShape(Radius.chip))
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (band != ConfidenceBand.HIGH) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = bandColors.text,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text =
                        when (band) {
                            ConfidenceBand.HIGH -> confidenceText
                            ConfidenceBand.MEDIUM -> "$confidenceText · nên xem lại ảnh"
                            ConfidenceBand.LOW -> "$confidenceText · cần bạn xác nhận lại"
                        },
                    style = MaterialTheme.typography.labelLarge,
                    color = bandColors.text,
                )
            }
        }
    }
}

@Composable
private fun TechnicalDetails(
    poleId: String,
    state: PolePhotoResultUiState.Success,
    onRetake: () -> Unit,
) {
    var expanded by rememberSaveable(poleId) { mutableStateOf(false) }
    var confirmRetake by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget)
                    .clickable { expanded = !expanded }
                    .testTag("details_toggle"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chi tiết kỹ thuật",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DetailRow("Model AI", state.modelVersion ?: "—")
                DetailRow("Phân tích lúc", state.analyzedAt?.let { DateFormatUtils.formatInstant(it) } ?: "—")
                if (state.reviewedBy != null) {
                    DetailRow("Đã review lúc", DateFormatUtils.formatInstant(state.reviewedAt))
                }
                RetakeButton(onClick = { confirmRetake = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmRetake) {
        AlertDialog(
            onDismissRequest = { confirmRetake = false },
            title = { Text("Chụp lại cột này?") },
            text = { Text("Ảnh hiện tại sẽ được thay bằng ảnh mới.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRetake = false
                        onRetake()
                    },
                ) { Text("Chụp lại") }
            },
            dismissButton = { TextButton(onClick = { confirmRetake = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewFooter(
    poleId: String,
    state: PolePhotoResultUiState.Success,
    onSubmit: (finalCvState: String, reviewNote: String?) -> Unit,
) {
    val baselineCvState = state.draftFinalCvState ?: state.finalCvState
    val baselineNote = state.draftReviewNote ?: state.reviewNote
    var selected by
        rememberSaveable(poleId) {
            mutableStateOf(baselineCvState ?: state.cvState?.takeIf { it == "on" || it == "off" })
        }
    var note by rememberSaveable(poleId) { mutableStateOf(baselineNote.orEmpty()) }
    // Required whenever the chosen label differs from the AI's own cvState, including when
    // cvState is "inconclusive" or null (server rule, see docs/contract-drift.md "PATCH
    // .../review" - review_note is mandatory even when AI never reached on/off or failed
    // outright, not only when it disagrees).
    val noteRequired = selected != null && selected != state.cvState
    val normalizedNote = note.trim().ifBlank { null }
    val hasChange = selected != baselineCvState || normalizedNote != baselineNote
    val canSubmit = selected != null && (!noteRequired || note.isNotBlank()) && hasChange

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "KỸ SƯ XÁC NHẬN NHÃN",
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ReviewChoiceSegment(selected = selected, onSelected = { selected = it })

        if (noteRequired) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Ghi chú") },
                isError = note.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (note.isBlank()) {
                Text(
                    text = "Cần ghi chú vì khác với nhận định của AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (state.draftFinalCvState != null && !hasChange) {
            Text(
                text = "Đã lưu, sẽ gửi khi Hoàn tất",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PrimaryButton(
            text = reviewActionLabel(selected, state.cvState),
            onClick = { selected?.let { onSubmit(it, normalizedNote) } },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReviewChoiceSegment(
    selected: String?,
    onSelected: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf("on" to "Đèn sáng", "off" to "Đèn tắt").forEach { (value, label) ->
            ReviewChoiceCard(
                value = value,
                label = label,
                isSelected = selected == value,
                onClick = { onSelected(value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReviewChoiceCard(
    value: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = cvStateColors(value)
    val shape = RoundedCornerShape(Radius.chip)
    Row(
        modifier =
            modifier
                .heightIn(min = Dimens.minTouchTarget)
                // clip must come before clickable, otherwise the tap ripple draws as a full
                // rectangle instead of following this card's rounded corners (found on a real
                // device, 2026-10-29).
                .clip(shape)
                .background(if (isSelected) colors.background else MaterialTheme.colorScheme.surfaceVariant, shape)
                .border(if (isSelected) 2.dp else 0.dp, colors.text, shape)
                .clickable(onClick = onClick)
                .testTag("review_choice_$value")
                .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .background(
                        if (isSelected) colors.text else colors.background,
                        RoundedCornerShape(Dimens.radiusSmall),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (value == "on") Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = if (isSelected) Color.White else colors.text,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) colors.text else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PolePhotoPreview(
    state: PolePhotoResultUiState.Success,
    onRequestAuthorizationHeader: suspend () -> String?,
) {
    var viewing by remember { mutableStateOf(false) }
    val isVideo = state.mediaType == "video"
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(photoHeight())
                .clip(RoundedCornerShape(Radius.card))
                .background(Dark950)
                .then(if (isVideo) Modifier else Modifier.clickable { viewing = true }.testTag("photo_preview")),
    ) {
        if (isVideo) {
            VideoPlayer(
                localFilePath = state.filePath,
                videoUrl = state.videoUrl,
                onRequestAuthorizationHeader = onRequestAuthorizationHeader,
            )
        } else {
            AsyncImage(
                model = File(state.filePath),
                contentDescription = "Ảnh cột đèn đã chụp",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
    if (viewing) {
        PhotoViewerDialog(filePath = state.filePath, onDismiss = { viewing = false })
    }
}

@Composable
private fun PhotoViewerDialog(
    filePath: String,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Dark950).testTag("photo_viewer")) {
            AsyncImage(
                model = File(filePath),
                contentDescription = "Ảnh cột đèn phóng to",
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale == 1f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm)) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng ảnh", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoPreviewPlaceholder() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Video clip",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun String.toAbsoluteVideoUrl(): String {
    if (startsWith("http://") || startsWith("https://")) return this
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    val path = if (startsWith("/")) this else "/$this"
    return base + path
}

@Composable
private fun VideoPlayer(
    localFilePath: String,
    videoUrl: String?,
    onRequestAuthorizationHeader: suspend () -> String?,
) {
    val context = LocalContext.current
    val localFile = remember(localFilePath) { File(localFilePath) }
    var authorizationHeader by remember(videoUrl) { mutableStateOf<String?>(null) }
    var authorizationHeaderReady by remember(videoUrl) { mutableStateOf(localFile.exists()) }

    LaunchedEffect(videoUrl, localFile) {
        if (!localFile.exists() && videoUrl != null) {
            authorizationHeader = onRequestAuthorizationHeader()
            authorizationHeaderReady = true
        }
    }

    if (!authorizationHeaderReady) {
        VideoPreviewPlaceholder()
        return
    }

    val mediaItem =
        remember(localFile, videoUrl) {
            if (localFile.exists()) {
                MediaItem.fromUri(Uri.fromFile(localFile))
            } else if (videoUrl != null) {
                MediaItem.fromUri(Uri.parse(videoUrl.toAbsoluteVideoUrl()))
            } else {
                null
            }
        }

    if (mediaItem == null) {
        VideoPreviewPlaceholder()
        return
    }

    val player =
        remember(mediaItem, authorizationHeader) {
            val dataSourceFactory =
                DefaultHttpDataSource.Factory().apply {
                    authorizationHeader?.let { setDefaultRequestProperties(mapOf("Authorization" to it)) }
                }
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
                .build()
                .apply {
                    setMediaItem(mediaItem)
                    prepare()
                }
        }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        // ModalBottomSheet renders in its own Window, with a scrim drawn on top of this sheet's
        // content - PlayerView's default SurfaceView composites in its own hardware layer and
        // punches through that scrim/the sheet's own header instead of respecting normal view
        // z-order (found on a real device, 2026-11-11). TextureView is a regular view that
        // participates in the usual view hierarchy, which fixes it.
        factory = { viewContext ->
            (LayoutInflater.from(viewContext).inflate(R.layout.view_player_texture, null) as PlayerView)
                .apply { this.player = player }
        },
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.card)),
    )
}

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
            Text(text = tone.title, style = MaterialTheme.typography.titleSmall, color = tone.foreground)
            if (!state.decisionNote.isNullOrBlank()) {
                Text(text = state.decisionNote, style = MaterialTheme.typography.bodyMedium, color = tone.foreground)
            }
            if (state.decision != null) {
                Text(
                    text = "Lúc ${DateFormatUtils.formatInstant(state.decidedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tone.foreground,
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
