package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.AssignedSurveyRoute

@Composable
fun SurveyHubRoute(
    onCheckReadiness: (surveySweepId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SurveyHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LuxMapTheme(forceDark = true) {
        SurveyHubScreen(
            state = uiState,
            onCheckReadiness = onCheckReadiness,
            modifier = modifier,
            onRetry = viewModel::onRetry,
        )
    }
}

@Composable
fun SurveyHubScreen(
    state: SurveyHubUiState,
    onCheckReadiness: (surveySweepId: String) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onReviewAiConfirmation: (surveySweepId: String) -> Unit = {},
    onResurvey: (surveySweepId: String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Khảo sát",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(Spacing.lg),
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                is SurveyHubUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.testTag("survey_hub_loading"))

                is SurveyHubUiState.Success ->
                    SurveyHubContent(
                        state = state,
                        onCheckReadiness = onCheckReadiness,
                        onReviewAiConfirmation = onReviewAiConfirmation,
                        onResurvey = onResurvey,
                    )

                is SurveyHubUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Chưa có tuyến nào được phân công — liên hệ Kỹ sư bảo trì",
                        subtitle = "Khi có tuyến mới được giao, danh sách sẽ hiển thị tại đây.",
                        action = {},
                    )

                is SurveyHubUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = state.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                    ) {
                        OutlinedButton(
                            onClick = onRetry,
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                        ) {
                            Text("Thử lại")
                        }
                    }
            }
        }
    }
}

@Composable
private fun SurveyHubContent(
    state: SurveyHubUiState.Success,
    onCheckReadiness: (surveySweepId: String) -> Unit,
    onReviewAiConfirmation: (surveySweepId: String) -> Unit,
    onResurvey: (surveySweepId: String) -> Unit,
) {
    val hasAttentionCards =
        state.rejectedSweeps.isNotEmpty() ||
            state.pendingAiConfirmation.isNotEmpty() ||
            state.uploadingSessions.isNotEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (hasAttentionCards) {
            item {
                Text(
                    text = "CẦN BẠN XỬ LÝ",
                    style = OverlineTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.rejectedSweeps) { rejected ->
                RejectedSweepCard(rejected, onResurvey = { onResurvey(rejected.surveySweepId) })
            }
            items(state.pendingAiConfirmation) { pending ->
                AiPendingCard(pending, onReview = { onReviewAiConfirmation(pending.surveySweepId) })
            }
            items(state.uploadingSessions) { session -> UploadingSessionRow(session) }
        }
        if (state.assignedRoutes.isNotEmpty()) {
            item {
                Text(
                    text = "TUYẾN ĐƯỢC GIAO",
                    style = OverlineTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.assignedRoutes) { route ->
                AssignedRouteCard(route, onCheckReadiness = { onCheckReadiness(route.surveySweepId) })
            }
        }
    }
}

@Composable
private fun RejectedSweepCard(
    sweep: LocalRejectedSweep,
    onResurvey: () -> Unit,
) {
    val colors = AssetCondition.OUT.badgeColors(isDark = true)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.background, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "BỊ TỪ CHỐI · ${sweep.surveySweepId}", style = OverlineTextStyle, color = colors.text)
        Text(text = sweep.rejectionReason, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Bởi ${sweep.rejectedByName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Khảo sát lại", onClick = onResurvey, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AiPendingCard(
    pending: LocalPendingAiConfirmation,
    onReview: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "CHỜ XÁC NHẬN AI · ${pending.surveySweepId}", style = OverlineTextStyle)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(
                text = "${AssetCondition.NORMAL.label()} ${pending.normalCount}",
                colors = AssetCondition.NORMAL.badgeColors(isDark = true),
            )
            StatusBadge(
                text = "${AssetCondition.DIM.label()} ${pending.dimCount}",
                colors = AssetCondition.DIM.badgeColors(isDark = true),
            )
            StatusBadge(
                text = "${AssetCondition.OUT.label()} ${pending.outCount}",
                colors = AssetCondition.OUT.badgeColors(isDark = true),
            )
        }
        PrimaryButton(text = "Xem & xác nhận", onClick = onReview, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UploadingSessionRow(session: UploadingSurveySession) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "Chờ tải lên · ${session.surveySweepId}", style = MaterialTheme.typography.bodyMedium)
        StatusBadge(text = session.syncStatus.label(), colors = session.syncStatus.badgeColors())
    }
}

@Composable
private fun AssignedRouteCard(
    route: AssignedSurveyRoute,
    onCheckReadiness: () -> Unit,
) {
    val totalLengthMeters = route.roadSegments.mapNotNull { it.lengthMeters }.sum()
    val segmentsLabel =
        if (totalLengthMeters > 0) {
            "${route.roadSegments.size} đoạn · ${totalLengthMeters.toInt()} m"
        } else {
            "${route.roadSegments.size} đoạn"
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = route.surveySweepId, style = MaterialTheme.typography.titleMedium)
        Text(
            text = segmentsLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Ngày dự kiến: ${route.plannedDate} · Giao bởi: ${route.assignedByName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Kiểm tra sẵn sàng", onClick = onCheckReadiness, modifier = Modifier.fillMaxWidth())
    }
}
