package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxActionPair
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.PrimaryButton
import kotlin.math.roundToInt

@Composable
fun PoleSurveySummaryRoute(
    viewModel: PoleListViewModel,
    onBack: () -> Unit,
    onRetake: (poleId: String) -> Unit,
    onSurveyCompleted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.surveyCompleted.collect { onSurveyCompleted() }
    }
    (state as? PoleListUiState.Success)?.let { success ->
        PoleSurveySummaryScreen(
            state = success,
            onBack = onBack,
            onRetake = onRetake,
            onSubmit = viewModel::onComplete,
        )
    }
}

// End-of-route check, shown once every pole is photographed or skipped. Lists only the poles
// where the AI read the photo differently from the engineer, or could not decide. Nothing here
// adopts the AI's answer: the engineer keeps their label or retakes the photo, and either way
// the survey can be sent - the Manager reviews these poles first.
@Composable
fun PoleSurveySummaryScreen(
    state: PoleListUiState.Success,
    onBack: () -> Unit,
    onRetake: (poleId: String) -> Unit,
    onSubmit: () -> Unit,
) {
    var keptPoleIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    val mismatches = state.aiMismatches
    Scaffold(
        topBar = { LuxChildTopBar(title = "Kiểm tra trước khi gửi", onBack = onBack) },
        bottomBar = {
            SummaryBottomBar(
                canSubmit = state.canComplete,
                onSubmit = { showConfirm = true },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SurveyCounts(state.poles) }
            if (state.awaitingAiCount > 0) {
                item {
                    LuxBanner(
                        message =
                            "${state.awaitingAiCount} ảnh chưa được AI đọc (chưa tải lên). " +
                                "Bạn vẫn gửi được, Quản lý sẽ đối chiếu sau.",
                        tone = LuxBannerTone.INFO,
                    )
                }
            }
            if (mismatches.isEmpty()) {
                item {
                    LuxBanner(
                        message = "AI đồng ý với bạn ở tất cả ảnh đã đọc.",
                        tone = LuxBannerTone.SUCCESS,
                    )
                }
            } else {
                item {
                    Text(
                        text = "AI thấy khác bạn ở ${mismatches.size} cột",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Nếu còn ở gần, hãy nhìn lại đèn. Nhãn của bạn không bị đổi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(mismatches, key = { it.poleId }) { mismatch ->
                    MismatchCard(
                        mismatch = mismatch,
                        isKept = mismatch.poleId in keptPoleIds,
                        onKeep = { keptPoleIds = keptPoleIds + mismatch.poleId },
                        onRetake = { onRetake(mismatch.poleId) },
                    )
                }
            }
        }
    }

    if (showConfirm && state.canComplete) {
        LuxConfirmDialog(
            title = "Gửi khảo sát tuyến?",
            message = "Kết quả ${state.poles.size} cột sẽ gửi cho Quản lý duyệt.",
            confirmLabel = "Gửi",
            dismissLabel = "Kiểm tra lại",
            confirmModifier = Modifier.testTag("confirm_survey_complete"),
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                onSubmit()
            },
        )
    }
}

@Composable
private fun SurveyCounts(poles: List<PoleListRowState>) {
    val counts = poles.mapNotNull { it.observation }.groupingBy { it }.eachCount()
    val skipped = poles.count { it.skipReason != null }
    LuxCard {
        Text(text = "${poles.size} cột trên tuyến", style = MaterialTheme.typography.titleMedium)
        Text(
            text =
                Observation.entries.joinToString(" · ") { "${it.label} ${counts[it] ?: 0}" } +
                    " · Bỏ qua $skipped",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MismatchCard(
    mismatch: AiMismatch,
    isKept: Boolean,
    onKeep: () -> Unit,
    onRetake: () -> Unit,
) {
    LuxCard(modifier = Modifier.testTag("mismatch_${mismatch.poleId}")) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AsyncImage(
                model = mismatch.filePath,
                contentDescription = "Ảnh cột ${mismatch.poleId}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.small),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = mismatch.position?.let { "Cột $it · ${mismatch.poleId}" } ?: mismatch.poleId,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = "Bạn: ${mismatch.observation.label}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = aiLabel(mismatch),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isKept) {
            Text(
                text = "Đã giữ nhãn của bạn",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        } else {
            LuxActionPair(
                modifier = Modifier.padding(top = Spacing.md),
                secondary = { LuxSecondaryButton(text = "Chụp lại", onClick = onRetake, modifier = it) },
                primary = { LuxSecondaryButton(text = "Giữ nhãn của tôi", onClick = onKeep, modifier = it) },
            )
        }
    }
}

private fun aiLabel(mismatch: AiMismatch): String =
    when (mismatch.cvState) {
        "on", "off" -> {
            val confidence = mismatch.confidence?.let { " (${(it * 100).roundToInt()}%)" }.orEmpty()
            "AI: ${cvStateLabel(mismatch.cvState)}$confidence"
        }
        null -> "AI không đọc được ảnh"
        else -> "AI không chắc"
    }

@Composable
private fun SummaryBottomBar(
    canSubmit: Boolean,
    onSubmit: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PrimaryButton(
                text = "Gửi khảo sát",
                onClick = onSubmit,
                enabled = canSubmit,
                branded = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
    }
}
