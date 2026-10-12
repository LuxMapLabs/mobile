package com.luxmap.feature.survey.ui.aiconfirm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.LocalAiClassificationReview
import java.util.Locale

private const val LOW_CONFIDENCE_THRESHOLD = 85

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfirmationScreen(
    surveySweepId: String,
    uiState: AiConfirmationUiState,
    sheet: AiReviewSheetState?,
    onBack: () -> Unit,
    onFilterSelected: (ReviewFilter) -> Unit = {},
    onReviewSelected: (LocalAiClassificationReview) -> Unit = {},
    onDismissSheet: () -> Unit = {},
    onConditionChosen: (AssetCondition) -> Unit = {},
    onReasonChanged: (String) -> Unit = {},
    onSaveSheet: () -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xác nhận kết quả AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
        bottomBar = {
            if (uiState is AiConfirmationUiState.Success) {
                BottomActionBar(ctaText = "Gửi", onCtaClick = onSubmit, ctaEnabled = true)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is AiConfirmationUiState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).testTag("ai_confirmation_loading"),
                    )

                is AiConfirmationUiState.Success ->
                    AiConfirmationContent(
                        state = uiState,
                        onFilterSelected = onFilterSelected,
                        onReviewSelected = onReviewSelected,
                    )

                is AiConfirmationUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Chưa có kết quả AI cần xác nhận cho phiên này",
                        subtitle = "Kết quả sẽ hiển thị tại đây sau khi AI xử lý xong.",
                        action = {},
                    )

                is AiConfirmationUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = uiState.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                        action = {},
                    )
            }
        }
    }
}

@Composable
private fun AiConfirmationContent(
    state: AiConfirmationUiState.Success,
    onFilterSelected: (ReviewFilter) -> Unit,
    onReviewSelected: (LocalAiClassificationReview) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { AiSummaryBlock(state) }
        item {
            Text(
                text = "Sửa nếu AI phân loại sai, kèm lý do. Kết quả gốc của AI luôn được giữ lại.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { FilterChipRow(selected = state.filter, onFilterSelected = onFilterSelected) }
        items(state.reviews) { review -> ReviewRow(review, onClick = { onReviewSelected(review) }) }
    }
}

@Composable
private fun AiSummaryBlock(state: AiConfirmationUiState.Success) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(Radius.block))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "TÓM TẮT PHÂN LOẠI",
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(
                text = "${AssetCondition.NORMAL.label()} ${state.normalCount}",
                colors = AssetCondition.NORMAL.badgeColors(isDark = true),
            )
            StatusBadge(
                text = "${AssetCondition.DIM.label()} ${state.dimCount}",
                colors = AssetCondition.DIM.badgeColors(isDark = true),
            )
            StatusBadge(
                text = "${AssetCondition.OUT.label()} ${state.outCount}",
                colors = AssetCondition.OUT.badgeColors(isDark = true),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    selected: ReviewFilter,
    onFilterSelected: (ReviewFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = selected == ReviewFilter.ALL,
            onClick = { onFilterSelected(ReviewFilter.ALL) },
            label = { Text("Tất cả") },
        )
        FilterChip(
            selected = selected == ReviewFilter.NEEDS_REVIEW,
            onClick = { onFilterSelected(ReviewFilter.NEEDS_REVIEW) },
            label = { Text("Cần xem") },
        )
        FilterChip(
            selected = selected == ReviewFilter.OVERRIDDEN,
            onClick = { onFilterSelected(ReviewFilter.OVERRIDDEN) },
            label = { Text("Đã sửa") },
        )
    }
}

@Composable
private fun ReviewRow(
    review: LocalAiClassificationReview,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrameThumbnail(finalClass = review.finalClass, modifier = Modifier.size(56.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = review.poleId, style = MaterialTheme.typography.bodyLarge)
            val distanceText = "%.1f".format(Locale.US, review.distanceKm)
            val base = "$distanceText km · AI ${review.aiConfidencePercent}%"
            val isLowConfidence = review.aiConfidencePercent < LOW_CONFIDENCE_THRESHOLD
            Text(
                text = if (isLowConfidence) "$base · tin cậy thấp" else base,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isLowConfidence) {
                        SyncStatus.SYNCING.badgeColors().text
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (review.overrideReason != null) {
                Text(
                    text = "đã sửa từ ${review.aiClass.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        StatusBadge(text = review.finalClass.label(), colors = review.finalClass.badgeColors(isDark = false))
    }
}

@Composable
private fun FrameThumbnail(
    finalClass: AssetCondition,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.control)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(finalClass.badgeColors(isDark = false).background),
        )
    }
}
