package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge

@Composable
fun PoleListRoute(
    onCapturePole: (poleId: String) -> Unit,
    onDone: () -> Unit,
    viewModel: PoleListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiResultState by viewModel.aiResultState.collectAsState()
    // Remembered so the bottom sheet's "Thử lại" button can re-fetch the same photo on error -
    // the sheet itself only knows the current PolePhotoResultUiState, not which photo it's for.
    var lastViewedServerPhotoId by rememberSaveable { mutableStateOf<String?>(null) }

    PoleListScreen(
        state = uiState,
        aiResultState = aiResultState,
        onCapturePole = onCapturePole,
        onDone = onDone,
        onRetry = viewModel::onRetry,
        onViewAiResult = { serverPhotoId ->
            lastViewedServerPhotoId = serverPhotoId
            viewModel.onViewAiResult(serverPhotoId)
        },
        onDismissAiResult = viewModel::onDismissAiResult,
        onRetryAiResult = { lastViewedServerPhotoId?.let(viewModel::onViewAiResult) },
    )
}

@Composable
fun PoleListScreen(
    state: PoleListUiState,
    onCapturePole: (poleId: String) -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit = {},
    aiResultState: PolePhotoResultUiState? = null,
    onViewAiResult: (serverPhotoId: String) -> Unit = {},
    onDismissAiResult: () -> Unit = {},
    onRetryAiResult: () -> Unit = {},
) {
    Scaffold(
        topBar = { AppTopBar(title = "Chụp ảnh cột đèn", onBack = onDone) },
        bottomBar = {
            if (state is PoleListUiState.Success) {
                BottomActionBar(ctaText = "Hoàn tất", onCtaClick = onDone, ctaEnabled = true)
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            when (state) {
                is PoleListUiState.Loading -> CircularProgressIndicator()

                is PoleListUiState.Success ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.poles) { pole ->
                            PoleRow(
                                pole = pole,
                                onClick = { onCapturePole(pole.poleId) },
                                onViewAiResult = { pole.serverPhotoId?.let(onViewAiResult) },
                            )
                        }
                    }

                is PoleListUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Tuyến này chưa có cột đèn nào",
                        subtitle = "Liên hệ Kỹ sư bảo trì nếu bạn cho rằng đây là nhầm lẫn.",
                        action = {},
                    )

                is PoleListUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.ErrorOutline,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = "Không tải được danh sách cột",
                        subtitle = state.message,
                        action = { PrimaryButton(text = "Thử lại", onClick = onRetry) },
                    )
            }
        }
    }

    if (aiResultState != null) {
        PolePhotoResultBottomSheet(
            state = aiResultState,
            onDismiss = onDismissAiResult,
            onRetry = onRetryAiResult,
        )
    }
}

@Composable
private fun PoleRow(
    pole: PoleListRowState,
    onClick: () -> Unit,
    onViewAiResult: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val condition =
        when (pole.fixtureStatus) {
            "normal" -> AssetCondition.NORMAL
            "dim" -> AssetCondition.DIM
            "out" -> AssetCondition.OUT
            else -> AssetCondition.UNKNOWN
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = "Cột #${pole.position} — ${pole.poleId}", style = MaterialTheme.typography.titleMedium)
            StatusBadge(text = condition.label(), colors = condition.badgeColors(isDark))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pole.syncStatus == SyncStatus.DONE && pole.serverPhotoId != null) {
                IconButton(onClick = onViewAiResult) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "Xem kết quả AI",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (pole.syncStatus != null) {
                StatusBadge(text = pole.syncStatus.label(), colors = pole.syncStatus.badgeColors())
            } else {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Chụp ảnh",
                    modifier = Modifier.padding(Spacing.sm),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
