package com.luxmap.feature.polelocation.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.polelocation.data.PoleLocationUpdateStatus
import com.luxmap.feature.polelocation.data.PoleMissingLocationItem

@Composable
fun PoleLocationListRoute(
    onCapturePole: (poleId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: PoleLocationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    PoleLocationListScreen(
        uiState = uiState,
        onCapturePole = onCapturePole,
        onRetry = viewModel::onRetry,
        onBack = onBack,
    )
}

@Composable
fun PoleLocationListScreen(
    uiState: PoleLocationListUiState,
    onCapturePole: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Cột thiếu GPS", onBack = onBack)
        Box(Modifier.fillMaxSize()) {
            when (uiState) {
                is PoleLocationListUiState.Loading -> LoadingState()
                is PoleLocationListUiState.Success ->
                    PoleLocationItemList(items = uiState.items, onCapturePole = onCapturePole)
                is PoleLocationListUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.LocationOn,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Không còn cột nào thiếu GPS",
                        subtitle = "Mọi cột trong địa bàn của bạn đã có toạ độ.",
                        action = {},
                    )
                is PoleLocationListUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = "Không tải được danh sách",
                        subtitle = uiState.message,
                        action = { PrimaryButton(text = "Thử lại", onClick = onRetry) },
                    )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PoleLocationItemList(
    items: List<PoleMissingLocationItem>,
    onCapturePole: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items, key = { it.poleId }) { item ->
            PoleLocationRow(item = item, onClick = { onCapturePole(item.poleId) })
        }
    }
}

@Composable
private fun PoleLocationRow(
    item: PoleMissingLocationItem,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.card))
                .clickable(onClick = onClick)
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = item.externalRef ?: item.poleId, style = MaterialTheme.typography.titleMedium)
            if (item.note != null) {
                Text(text = item.note, style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.communeName != null) {
                Text(
                    text = item.communeName,
                    style = LabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (item.localStatus) {
            PoleLocationUpdateStatus.CONFLICT ->
                StatusBadge(text = "Lệch tuyến · đo lại", colors = SyncStatus.CONFLICT.badgeColors())
            PoleLocationUpdateStatus.FAILED ->
                StatusBadge(text = "Gửi lỗi · đo lại", colors = SyncStatus.FAILED.badgeColors())
            else -> Icon(imageVector = Icons.Filled.LocationOn, contentDescription = null)
        }
    }
}
