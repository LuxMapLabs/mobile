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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
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
    PoleListScreen(
        state = uiState,
        onCapturePole = onCapturePole,
        onDone = onDone,
        onRetry = viewModel::onRetry,
    )
}

@Composable
fun PoleListScreen(
    state: PoleListUiState,
    onCapturePole: (poleId: String) -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.statusBarsPadding().padding(Spacing.lg)) {
            Text(text = "Chụp ảnh cột đèn", style = DisplayTextStyle, color = MaterialTheme.colorScheme.onBackground)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                is PoleListUiState.Loading -> CircularProgressIndicator()

                is PoleListUiState.Success ->
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            items(state.poles) { pole ->
                                PoleRow(pole = pole, onClick = { onCapturePole(pole.poleId) })
                            }
                        }
                        PrimaryButton(
                            text = "Hoàn tất",
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        )
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
}

@Composable
private fun PoleRow(
    pole: PoleListRowState,
    onClick: () -> Unit,
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
            Text(text = "Cột #${pole.position} — ${pole.poleId}", style = MaterialTheme.typography.titleSmall)
            StatusBadge(text = condition.label(), colors = condition.badgeColors(isDark))
        }
        if (pole.isCaptured) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Đã chụp",
                tint = MaterialTheme.colorScheme.primary,
            )
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
