package com.luxmap.feature.home.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
// theo cụm địa lý (BE-25 làm ở server, xem FakeHomeRepository.clusterLabel cho tới khi có API
// thật). Theo hệ thống (Light mặc định) — không thuộc danh sách Dark bắt buộc ở CLAUDE.md.
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
                is HomeUiState.Empty -> MessageState(text = "Không có việc nào cho hôm nay")
                is HomeUiState.Error -> MessageState(text = uiState.message)
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
        item {
            Text(text = "Việc hôm nay", style = MaterialTheme.typography.titleLarge)
        }
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
            item {
                Text(
                    text = cluster.clusterLabel,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            items(cluster.items, key = { it.workOrderId }) { item ->
                WorkOrderCard(
                    workOrderId = item.workOrderId,
                    shortAddress = item.shortAddress,
                    faultTypeLabel = item.faultTypeLabel,
                    priority = item.priority,
                    slaDueAt = item.slaDueAt,
                    distanceMeters = item.distanceMeters,
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

// 4 thẻ theo đúng F02: lệnh được giao / đang xử lý / quá hạn / đợt khảo sát đã lên kế hoạch.
@Composable
private fun MetricCardRow(metrics: HomeMetrics) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MetricCard(label = "Được giao", value = metrics.assignedCount, modifier = Modifier.weight(1f))
            MetricCard(label = "Đang xử lý", value = metrics.inProgressCount, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MetricCard(label = "Quá hạn", value = metrics.overdueCount, modifier = Modifier.weight(1f))
            MetricCard(label = "Đợt khảo sát", value = metrics.plannedSweepCount, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.lg),
    ) {
        Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun BoxScope.MessageState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.Center).padding(Spacing.lg),
    )
}
