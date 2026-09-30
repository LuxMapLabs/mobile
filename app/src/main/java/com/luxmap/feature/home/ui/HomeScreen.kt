package com.luxmap.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.ErrorBanner
import com.luxmap.core.ui.components.OfflineBanner
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.WorkOrderCard
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Nav-graph entry point (F02/FM-16) — nối HomeViewModel, đưa HomeScreen thành stateless để dễ
// lái bằng dữ liệu cố định. onOpenWorkOrder/onStartSurvey dẫn tới F09/F03, chưa có route thật
// (feature/workorder, feature/survey chưa code) nên tạm báo qua snackbar, đúng cách PoleDetailRoute
// đang làm với showNotImplemented — không thêm route giả vào NavGraph ở task này.
@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showNotImplemented: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar("Chức năng đang được hoàn thiện") }
    }
    HomeScreen(
        uiState = uiState,
        onOpenWorkOrder = { showNotImplemented() },
        onStartSurvey = showNotImplemented,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

// F02 Việc hôm nay (Trang chủ) — điểm vào chính, 4 thẻ số liệu + danh sách lệnh sửa chữa gom
// theo cụm địa lý (BE-25 làm ở server, xem RealHomeRepository.toHomeData cho tới khi có API gom
// cụm thật — hiện nhóm tạm theo commune_id thô). Theo hệ thống (Light mặc định) — không thuộc
// danh sách Dark bắt buộc ở CLAUDE.md.
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    onStartSurvey: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .statusBarsPadding(),
        ) {
            when (uiState) {
                is HomeUiState.Loading -> LoadingState()
                is HomeUiState.Success ->
                    HomeContent(
                        data = uiState.data,
                        isStale = uiState.isStale,
                        lastSyncedAt = uiState.lastSyncedAt,
                        onOpenWorkOrder = onOpenWorkOrder,
                        onStartSurvey = onStartSurvey,
                    )
                is HomeUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        text = "Không có việc nào cho hôm nay",
                    )
                is HomeUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.Warning,
                        text = uiState.message,
                    )
            }
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    isStale: Boolean,
    lastSyncedAt: Instant?,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    onStartSurvey: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { HomeHeader() }
        item { MetricCardRow(metrics = data.metrics) }
        if (data.metrics.overdueCount > 0) {
            item {
                ErrorBanner(message = "${data.metrics.overdueCount} lệnh đã quá hạn xử lý")
            }
        }
        if (isStale) {
            item { OfflineBanner(lastSyncedAt = lastSyncedAt) }
        }
        data.clusters.forEach { cluster ->
            item { ClusterHeader(label = cluster.clusterLabel, count = cluster.items.size) }
            items(cluster.items, key = { it.workOrderId }) { item ->
                WorkOrderCard(
                    workOrderId = item.workOrderId,
                    woStatus = item.woStatus,
                    dueDate = item.dueDate,
                    priorityScore = item.priorityScore,
                    onClick = { onOpenWorkOrder(item.workOrderId) },
                )
            }
        }
        item {
            PrimaryButton(
                text = "Bắt đầu đợt khảo sát mới",
                onClick = onStartSurvey,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Tiêu đề + ngày hôm nay, thay cho chữ trơn trước đây — cho màn hình cảm giác như một trang tổng
// quan thật, không chỉ một danh sách.
@Composable
private fun HomeHeader() {
    val today =
        remember {
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi")),
            )
        }
    Column {
        Text(text = "Việc hôm nay", style = MaterialTheme.typography.titleLarge)
        Text(
            text = today.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi")) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClusterHeader(
    label: String,
    count: Int,
) {
    Text(
        text = "$label ($count)",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

// 4 thẻ theo đúng F02: lệnh được giao / đang xử lý / quá hạn / đợt khảo sát đã lên kế hoạch.
// Mỗi thẻ có icon + màu nhấn theo ngữ nghĩa số liệu, viền nhẹ thay vì chỉ nền phẳng.
@Composable
private fun MetricCardRow(metrics: HomeMetrics) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MetricCard(
                icon = Icons.Filled.Assignment,
                label = "Được giao",
                value = metrics.assignedCount,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                icon = Icons.Filled.Autorenew,
                label = "Đang xử lý",
                value = metrics.inProgressCount,
                accent = AMBER,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MetricCard(
                icon = Icons.Filled.Warning,
                label = "Quá hạn",
                value = metrics.overdueCount,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            // plannedSweepCount is null when the backend has no data source for it yet (see
            // HomeMetrics) - shown as a muted placeholder, not a misleading 0.
            MetricCard(
                icon = Icons.Filled.CameraAlt,
                label = "Đợt khảo sát",
                value = metrics.plannedSweepCount,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: Int?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(Spacing.xl))
        val valueColor =
            if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = value?.toString() ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor,
        )
        Text(
            text = if (value != null) label else "$label (chưa có dữ liệu)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Not part of the semantic theme (no "in-progress" token exists) - same primitive amber the
// Design System already uses for medium-severity warnings elsewhere (Amber500).
private val AMBER = Color(0xFFE9A23B)

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun BoxScope.MessageState(
    icon: ImageVector,
    text: String,
) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Spacing.xxxl),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
