package com.luxmap.feature.survey.ui.polephoto

import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.LuxActionPair
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.core.ui.components.LuxSegment
import com.luxmap.core.ui.components.LuxSegmented
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.LuxSnackbarHost
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.survey.ui.polephoto.lux.formatLux
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

private const val PHOTO_HEIGHT_FRACTION = 0.45f

@Composable
fun PolePhotoResultScreen(
    poleId: String,
    position: Int,
    state: PolePhotoResultUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetake: () -> Unit = {},
    onSubmitReview: (finalCvState: String, reviewNote: String?) -> Unit = { _, _ -> },
    onRequestAuthorizationHeader: suspend () -> String? = { null },
    isCompleted: Boolean = false,
    onWithdrawResult: (suspend () -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmWithdrawal by rememberSaveable { mutableStateOf(false) }
    var withdrawing by remember { mutableStateOf(false) }
    BackHandler(enabled = withdrawing) {}
    val reviewable = state as? PolePhotoResultUiState.Success
    val showFooter = reviewable != null && reviewable.decision == null && !isCompleted
    val draft = if (reviewable != null && showFooter) rememberReviewDraft(poleId, reviewable) else null

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LuxChildTopBar(
                title = "Kết quả AI",
                onBack = { if (!withdrawing) onBack() },
                subtitle = if (position > 0) "Cột $position ($poleId)" else poleId,
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                            .then(if (showFooter) Modifier else Modifier.navigationBarsPadding()),
                ) {
                    when (state) {
                        is PolePhotoResultUiState.Loading ->
                            LuxLoadingRegion(description = "Đang tải kết quả AI") {
                                LuxSkeleton(Modifier.fillMaxWidth().height(photoHeight()), MaterialTheme.shapes.medium)
                                LuxSkeleton(Modifier.fillMaxWidth().height(96.dp), MaterialTheme.shapes.medium)
                                LuxSkeleton(Modifier.fillMaxWidth().height(48.dp), MaterialTheme.shapes.medium)
                            }

                        is PolePhotoResultUiState.Success ->
                            PolePhotoResultBody(
                                poleId,
                                state,
                                if (isCompleted || withdrawing) null else onRetake,
                                onRequestAuthorizationHeader,
                            ) {
                                if (draft != null) ReviewCard(draft, hasSavedDraft = state.draftFinalCvState != null)
                            }

                        is PolePhotoResultUiState.Empty ->
                            LuxEmptyState(
                                title = "AI chưa phân loại được ảnh này",
                                message = "Model AI gặp lỗi khi xử lý, ảnh vẫn đã lưu trên hệ thống.",
                                icon = Icons.Filled.SmartToy,
                            )

                        is PolePhotoResultUiState.Error ->
                            LuxErrorState(
                                onRetry = onRetry,
                                kind = if (state.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                                title = "Không tải được kết quả AI",
                                message =
                                    if (state.isNetworkError) {
                                        "Kiểm tra kết nối rồi thử lại."
                                    } else {
                                        "Vui lòng thử lại sau ít phút."
                                    },
                            )
                    }

                    Spacer(Modifier.height(Spacing.lg))
                }
                LuxSnackbarHost(
                    state = snackbarHostState,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .then(if (showFooter) Modifier else Modifier.navigationBarsPadding()),
                )
            }

            if (draft != null) {
                ReviewSubmitBar(
                    draft = draft,
                    withdrawing = withdrawing,
                    onCancel = onWithdrawResult?.let { { confirmWithdrawal = true } },
                    onSubmit = { finalCvState, reviewNote ->
                        scope.launch { snackbarHostState.showSnackbar("Đã lưu, sẽ gửi khi Hoàn tất") }
                        onSubmitReview(finalCvState, reviewNote)
                    },
                )
            }
        }
    }
    if (confirmWithdrawal && onWithdrawResult != null) {
        LuxConfirmDialog(
            title = "Hủy kết quả khảo sát cột này?",
            message = "Ảnh hoặc video và kết quả AI hiện tại sẽ bị rút khỏi lượt khảo sát. Bạn có thể ghi nhận lại.",
            confirmLabel = "Hủy kết quả",
            dismissLabel = "Giữ lại",
            destructive = true,
            onDismiss = { confirmWithdrawal = false },
            onConfirm = {
                confirmWithdrawal = false
                withdrawing = true
                scope.launch {
                    try {
                        onWithdrawResult()
                        onBack()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        launch { snackbarHostState.showSnackbar("Không hủy được kết quả. Vui lòng thử lại.") }
                    } finally {
                        withdrawing = false
                    }
                }
            },
        )
    }
}

@Composable
fun PolePhotoResultRoute(
    photoId: String,
    onBack: () -> Unit,
    onRetake: (poleId: String, mediaType: String, source: String) -> Unit,
    viewModel: PoleListViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiResultState by viewModel.aiResultState.collectAsState()
    val pole = (uiState as? PoleListUiState.Success)?.poles?.firstOrNull { it.serverPhotoId == photoId }

    LaunchedEffect(photoId) {
        viewModel.onViewAiResult(photoId)
    }

    val isCompleted = (uiState as? PoleListUiState.Success)?.isCompleted ?: false
    PolePhotoResultScreen(
        poleId = pole?.poleId.orEmpty(),
        position = pole?.position ?: 0,
        state = aiResultState ?: PolePhotoResultUiState.Loading,
        onBack = {
            viewModel.onDismissAiResult()
            onBack()
        },
        onRetry = { viewModel.onViewAiResult(photoId) },
        onRetake = {
            val result = aiResultState as? PolePhotoResultUiState.Success
            if (pole != null && result != null) {
                viewModel.onDismissAiResult()
                onRetake(pole.poleId, result.mediaType, mediaCaptureSource(result.filePath))
            }
        },
        onSubmitReview = { finalCvState, reviewNote ->
            viewModel.onSubmitReview(photoId, finalCvState, reviewNote)
        },
        onRequestAuthorizationHeader = viewModel::currentAuthorizationHeader,
        isCompleted = isCompleted,
        onWithdrawResult = { viewModel.withdrawResult(photoId) },
    )
}

@Composable
private fun photoHeight() = (LocalConfiguration.current.screenHeightDp * PHOTO_HEIGHT_FRACTION).dp

@Composable
private fun PolePhotoResultBody(
    poleId: String,
    state: PolePhotoResultUiState.Success,
    onRetake: (() -> Unit)?,
    onRequestAuthorizationHeader: suspend () -> String?,
    reviewCard: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        if (state.isStale) {
            LuxBanner(
                message = "Đang xem kết quả đã lưu trên máy, có thể chưa phải bản mới nhất",
                tone = LuxBannerTone.INFO,
            )
        }

        if (state.decision != null) {
            PolePhotoDecisionCard(state)
        }

        PolePhotoPreview(state, onRequestAuthorizationHeader)

        ConclusionCard(state)

        reviewCard()

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
    return condition.badgeColors()
}

@Composable
private fun ConclusionCard(state: PolePhotoResultUiState.Success) {
    val reviewed = state.draftFinalCvState ?: state.finalCvState
    val effective = effectiveCvState(state.cvState, reviewed)
    val colors = cvStateColors(effective)
    val band = confidenceBand(state.confidence)
    val aiConclusive = state.cvState == "on" || state.cvState == "off"
    val bandColors =
        when (band) {
            ConfidenceBand.HIGH -> BadgeColors(LuxTheme.colors.successContainer, LuxTheme.colors.success)
            ConfidenceBand.MEDIUM -> BadgeColors(LuxTheme.colors.warningContainer, LuxTheme.colors.warning)
            ConfidenceBand.LOW -> BadgeColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        }
    val confidenceText = confidenceLabel(state.confidence)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LuxSectionHeader(title = "Kết luận")
        LuxCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).background(colors.background, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Filled.Lightbulb, contentDescription = null, tint = colors.text)
                    }
                    Text(
                        text = effective?.let(::cvStateLabel) ?: "Chưa có kết luận",
                        style = MaterialTheme.typography.titleLarge,
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
                                .background(bandColors.background, MaterialTheme.shapes.small)
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
                            text = confidenceText,
                            style = MaterialTheme.typography.labelLarge,
                            color = bandColors.text,
                        )
                    }
                }
                state.luxValue?.let { lux -> FieldLuxRow(lux) }
            }
        }
    }
}

@Composable
private fun FieldLuxRow(lux: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("ai_result_lux"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.WbSunny,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Lux đo tại hiện trường",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = "${formatLux(lux)} (tương đối)", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TechnicalDetails(
    poleId: String,
    state: PolePhotoResultUiState.Success,
    onRetake: (() -> Unit)?,
) {
    var expanded by rememberSaveable(poleId) { mutableStateOf(false) }
    var confirmRetake by remember { mutableStateOf(false) }
    val replacementLabel = mediaReplacementLabel(state.mediaType, mediaCaptureSource(state.filePath))
    val mediaName = if (state.mediaType == "video") "Video" else "Ảnh"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "Đang mở" else "Đang đóng" }
                    .testTag("details_toggle"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chi tiết kỹ thuật",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DetailRow("Phân tích lúc", state.analyzedAt?.let { DateFormatUtils.formatInstant(it) } ?: "—")
                if (state.reviewedBy != null) {
                    DetailRow("Đã review lúc", DateFormatUtils.formatInstant(state.reviewedAt))
                }
                if (state.decision == null && onRetake != null) {
                    LuxSecondaryButton(
                        text = replacementLabel,
                        onClick = { confirmRetake = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (confirmRetake) {
        LuxConfirmDialog(
            title = "$replacementLabel?",
            message = "$mediaName mới sẽ thay cho tệp hiện tại sau khi bạn xác nhận.",
            confirmLabel = "Tiếp tục",
            onConfirm = {
                confirmRetake = false
                onRetake?.invoke()
            },
            onDismiss = { confirmRetake = false },
            dismissLabel = "Hủy",
            destructive = true,
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

@Stable
private class ReviewDraft(
    val aiCvState: String?,
    val baselineCvState: String?,
    val baselineNote: String?,
    selectedState: MutableState<String?>,
    noteState: MutableState<String>,
) {
    var selected by selectedState
    var note by noteState
    val noteRequired: Boolean get() = selected != null && selected != aiCvState
    val normalizedNote: String? get() = note.trim().ifBlank { null }
    val hasChange: Boolean get() = selected != baselineCvState || normalizedNote != baselineNote
    val canSubmit: Boolean get() = selected != null && (!noteRequired || note.isNotBlank()) && hasChange
}

@Composable
private fun rememberReviewDraft(
    poleId: String,
    state: PolePhotoResultUiState.Success,
): ReviewDraft {
    val baselineCvState = state.draftFinalCvState ?: state.finalCvState
    val baselineNote = state.draftReviewNote ?: state.reviewNote
    val selected =
        rememberSaveable(poleId, baselineCvState, baselineNote) {
            mutableStateOf(baselineCvState ?: state.cvState?.takeIf { it == "on" || it == "off" })
        }
    val note = rememberSaveable(poleId, baselineCvState, baselineNote) { mutableStateOf(baselineNote.orEmpty()) }
    return ReviewDraft(state.cvState, baselineCvState, baselineNote, selected, note)
}

@Composable
private fun ReviewCard(
    draft: ReviewDraft,
    hasSavedDraft: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LuxSectionHeader(title = "Kỹ sư xác nhận nhãn")
        LuxCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (draft.aiCvState != "on" && draft.aiCvState != "off") {
                    Text(
                        text =
                            "Nếu thấy rõ trạng thái đèn, hãy chọn Đèn sáng hoặc Đèn tắt và nhập ghi chú. " +
                                "Nếu chưa rõ, hãy dùng ảnh hoặc video khác.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LuxSegmented(
                    segments =
                        listOf(
                            LuxSegment("on", "Đèn sáng", Icons.Filled.Lightbulb),
                            LuxSegment("off", "Đèn tắt", Icons.Outlined.Lightbulb),
                        ),
                    selected = draft.selected,
                    onSelect = { draft.selected = it },
                )
                if (draft.noteRequired) {
                    OutlinedTextField(
                        value = draft.note,
                        onValueChange = { draft.note = it },
                        label = {
                            Text(
                                buildAnnotatedString {
                                    append("Ghi chú ")
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("*") }
                                },
                            )
                        },
                        isError = draft.note.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (draft.note.isBlank()) {
                        Text(
                            text =
                                if (draft.aiCvState == "on" || draft.aiCvState == "off") {
                                    "Cần ghi chú vì khác với nhận định của AI"
                                } else {
                                    "Ghi rõ căn cứ bạn dùng để xác định trạng thái đèn"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (hasSavedDraft && !draft.hasChange) {
                    Text(
                        text = "Đã lưu, sẽ gửi khi Hoàn tất",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewSubmitBar(
    draft: ReviewDraft,
    withdrawing: Boolean,
    onCancel: (() -> Unit)?,
    onSubmit: (finalCvState: String, reviewNote: String?) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val padding = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
            val confirm: @Composable (Modifier) -> Unit = { modifier ->
                PrimaryButton(
                    text = "Xác nhận",
                    onClick = { draft.selected?.let { onSubmit(it, draft.normalizedNote) } },
                    enabled = draft.canSubmit && !withdrawing,
                    modifier = modifier,
                )
            }
            if (onCancel == null) {
                confirm(padding.fillMaxWidth())
            } else {
                LuxActionPair(
                    modifier = padding,
                    secondary = { modifier ->
                        LuxSecondaryButton(
                            text = if (withdrawing) "Đang hủy…" else "Hủy",
                            onClick = onCancel,
                            enabled = !withdrawing,
                            modifier = modifier,
                        )
                    },
                    primary = confirm,
                )
            }
        }
    }
}

@Composable
private fun PolePhotoPreview(
    state: PolePhotoResultUiState.Success,
    onRequestAuthorizationHeader: suspend () -> String?,
) {
    var viewing by remember { mutableStateOf(false) }
    val isVideo = state.mediaType == "video"
    val reviewed = state.draftFinalCvState ?: state.finalCvState
    val effective = effectiveCvState(state.cvState, reviewed)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(photoHeight())
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .then(
                    if (isVideo) {
                        Modifier
                    } else {
                        Modifier.clickable(onClickLabel = "Phóng to ảnh", role = Role.Image) {
                            viewing = true
                        }.testTag("photo_preview")
                    },
                ),
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
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!isVideo && effective != null) {
            val pillColors = cvStateColors(effective)
            Text(
                text = cvStateLabel(effective),
                style = MaterialTheme.typography.labelLarge,
                color = pillColors.text,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(Spacing.sm)
                        .background(pillColors.background, MaterialTheme.shapes.extraSmall)
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
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim).testTag("photo_viewer")) {
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
                .clip(MaterialTheme.shapes.medium)
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
        // PlayerView's default SurfaceView composites in its own hardware layer and punches
        // through other overlays (e.g. this screen's own header) instead of respecting normal
        // view z-order (found on a real device, 2026-11-11, back when this screen was still a
        // ModalBottomSheet). TextureView is a regular view that participates in the usual view
        // hierarchy, which fixes it.
        factory = { viewContext ->
            (LayoutInflater.from(viewContext).inflate(R.layout.view_player_texture, null) as PlayerView)
                .apply { this.player = player }
        },
        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
    )
}

@Composable
private fun PolePhotoDecisionCard(state: PolePhotoResultUiState.Success) {
    val (title, tone) =
        when (state.decision) {
            "confirm" -> "Quản lý đã xác nhận" to LuxBannerTone.SUCCESS
            "reject" -> "Quản lý đã từ chối" to LuxBannerTone.ERROR
            else -> "Chưa có quyết định từ Quản lý" to LuxBannerTone.INFO
        }
    val message =
        listOfNotNull(
            state.decisionNote?.takeIf { it.isNotBlank() },
            state.decidedAt?.let { "Lúc ${DateFormatUtils.formatInstant(it)}" },
        ).joinToString("\n")
    LuxBanner(title = title, message = message, tone = tone)
}
