package com.luxmap.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.ErrorBanner
import com.luxmap.core.ui.components.OfflineBanner
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.WorkOrderCard
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderSummaryItem
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
    onOpenWorkOrder: (workOrderId: String) -> Unit,
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
        onOpenWorkOrder = onOpenWorkOrder,
        onStartSurvey = showNotImplemented,
        onRetry = viewModel::retry,
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            val heroStats =
                when (uiState) {
                    is HomeUiState.Loading -> HeroStats.Loading
                    is HomeUiState.Success -> HeroStats.Known(uiState.data.metrics)
                    is HomeUiState.Empty -> HeroStats.Known(EMPTY_METRICS)
                    is HomeUiState.Error -> HeroStats.Unavailable
                }
            HomeHeroHeader(stats = heroStats, onRefresh = onRetry)
            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState) {
                    is HomeUiState.Loading -> LoadingSkeletonState()
                    is HomeUiState.Success ->
                        HomeContent(
                            data = uiState.data,
                            isStale = uiState.isStale,
                            lastSyncedAt = uiState.lastSyncedAt,
                            onOpenWorkOrder = onOpenWorkOrder,
                            onStartSurvey = onStartSurvey,
                        )
                    is HomeUiState.Empty -> EmptyState(onStartSurvey = onStartSurvey)
                    is HomeUiState.Error -> ErrorState(message = uiState.message, onRetry = onRetry)
                }
            }
        }
    }
}

private val EMPTY_METRICS =
    HomeMetrics(assignedCount = 0, inProgressCount = 0, overdueCount = 0, plannedSweepCount = null)

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
            item { ClusterCard(items = cluster.items, onOpenWorkOrder = onOpenWorkOrder) }
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

private sealed interface HeroStats {
    data object Loading : HeroStats

    data class Known(val metrics: HomeMetrics) : HeroStats

    data object Unavailable : HeroStats
}

@Composable
private fun HomeHeroHeader(
    stats: HeroStats,
    onRefresh: () -> Unit,
) {
    val today =
        remember {
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi")),
            )
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient)
                .statusBarsPadding()
                .padding(Spacing.xl),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = today.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi")) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                )
                Text(text = "Việc hôm nay", style = MaterialTheme.typography.displayLarge, color = Color.White)
            }
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Sync, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(Dimens.radiusLarge))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(Dimens.radiusLarge))
                    .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroStat(
                label = "Được giao",
                value = stats.valueFor { it.assignedCount },
                loading = stats is HeroStats.Loading,
                valueColor = Color.White,
                modifier = Modifier.weight(1f),
            )
            HeroStatDivider()
            HeroStat(
                label = "Đang xử lý",
                value = stats.valueFor { it.inProgressCount },
                loading = stats is HeroStats.Loading,
                valueColor = AssetCondition.DIM.badgeColors(isDark = true).text,
                modifier = Modifier.weight(1f),
            )
            HeroStatDivider()
            HeroStat(
                label = "Quá hạn",
                value = stats.valueFor { it.overdueCount },
                loading = stats is HeroStats.Loading,
                valueColor = AssetCondition.OUT.badgeColors(isDark = true).text,
                modifier = Modifier.weight(1f),
            )
            HeroStatDivider()
            HeroStat(
                label = "Đợt khảo sát",
                value = stats.valueFor { it.plannedSweepCount },
                loading = stats is HeroStats.Loading,
                valueColor = Color.White,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun HeroStats.valueFor(select: (HomeMetrics) -> Int?): Int? =
    when (this) {
        is HeroStats.Known -> select(metrics)
        HeroStats.Loading, HeroStats.Unavailable -> null
    }

@Composable
private fun HeroStat(
    label: String,
    value: Int?,
    loading: Boolean,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (loading) {
            Box(
                modifier =
                    Modifier
                        .width(28.dp)
                        .height(24.dp)
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(Dimens.radiusSmall)),
            )
        } else {
            Text(
                text = value?.toString() ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                color = if (value != null) valueColor else Color.White.copy(alpha = 0.5f),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RowScope.HeroStatDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.14f)),
    )
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

@Composable
private fun ClusterCard(
    items: List<WorkOrderSummaryItem>,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusLarge)),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            WorkOrderCard(
                workOrderId = item.workOrderId,
                woStatus = item.woStatus,
                dueDate = item.dueDate,
                priorityScore = item.priorityScore,
                onClick = { onOpenWorkOrder(item.workOrderId) },
            )
        }
    }
}

@Composable
private fun LoadingSkeletonState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = Spacing.sm)
                    .width(96.dp)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusSmall)),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusLarge)),
        ) {
            repeat(3) { index ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                SkeletonRow()
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.radiusMedium)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusSmall)),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.radiusSmall)),
            )
        }
    }
}

@Composable
private fun BoxScope.EmptyState(onStartSurvey: () -> Unit) {
    MessageState(
        icon = Icons.Filled.EventAvailable,
        iconColors = SyncStatus.DONE.badgeColors(),
        title = "Không có việc nào cho hôm nay",
        subtitle = "Khi có lệnh sửa chữa mới được giao, danh sách sẽ hiển thị tại đây.",
    ) {
        PrimaryButton(
            text = "Bắt đầu đợt khảo sát mới",
            onClick = onStartSurvey,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BoxScope.ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    MessageState(
        icon = Icons.Filled.CloudOff,
        iconColors = SyncStatus.FAILED.badgeColors(),
        title = message,
        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
    ) {
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Thử lại")
        }
    }
}

@Composable
private fun BoxScope.MessageState(
    icon: ImageVector,
    iconColors: BadgeColors,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .background(iconColors.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColors.text, modifier = Modifier.size(34.dp))
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        action()
    }
}
