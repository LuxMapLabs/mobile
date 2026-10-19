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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Amber500
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.ErrorDark
import com.luxmap.core.theme.Green400
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.MetricTextStyle
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.TopBarHeight
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.SyncPill
import com.luxmap.core.ui.components.dueDateLabel
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
            val metrics =
                when (uiState) {
                    is HomeUiState.Success -> uiState.data.metrics
                    is HomeUiState.Empty -> EMPTY_METRICS
                    is HomeUiState.Loading, is HomeUiState.Error -> null
                }
            val syncStatus =
                when (uiState) {
                    is HomeUiState.Loading -> SyncStatus.SYNCING
                    is HomeUiState.Success -> if (uiState.isStale) SyncStatus.QUEUED_OFFLINE else SyncStatus.DONE
                    is HomeUiState.Empty -> SyncStatus.DONE
                    is HomeUiState.Error -> SyncStatus.FAILED
                }
            val lastSyncedAt = (uiState as? HomeUiState.Success)?.lastSyncedAt
            HomeHeader(metrics = metrics, syncStatus = syncStatus, lastSyncedAt = lastSyncedAt, onRefresh = onRetry)
            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState) {
                    is HomeUiState.Loading -> LoadingSkeletonState()
                    is HomeUiState.Success ->
                        HomeContent(
                            data = uiState.data,
                            isStale = uiState.isStale,
                            lastSyncedAt = uiState.lastSyncedAt,
                            onOpenWorkOrder = onOpenWorkOrder,
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val nextUp = data.nextUp()
        if (data.metrics.overdueCount > 0) {
            item {
                InlineBanner(
                    message = "${data.metrics.overdueCount} lệnh đã quá hạn xử lý",
                    tone = InlineBannerTone.ERROR,
                )
            }
        }
        if (isStale) {
            item {
                InlineBanner(
                    message = "Đang xem dữ liệu đã lưu, cập nhật lúc ${DateFormatUtils.formatInstant(lastSyncedAt)}",
                    tone = InlineBannerTone.OFFLINE,
                )
            }
        }
        if (nextUp != null) {
            val clusterLabel =
                data.clusters
                    .first { cluster -> cluster.items.any { it.workOrderId == nextUp.workOrderId } }
                    .clusterLabel
            item {
                NextUpCard(
                    item = nextUp,
                    clusterLabel = clusterLabel,
                    onClick = { onOpenWorkOrder(nextUp.workOrderId) },
                )
            }
        }
        data.groupedByTaskKind().forEach { (kind, items) ->
            val remaining = items.filterNot { nextUp != null && it.workOrderId == nextUp.workOrderId }
            if (remaining.isNotEmpty()) {
                item {
                    TaskKindSection(
                        kind = kind,
                        items = remaining,
                        onOpenWorkOrder = onOpenWorkOrder,
                    )
                }
            }
        }
    }
}

// Spec order is "overdue > urgent > due today > priority_score", but WorkOrderSummaryItem has
// no severity/urgent field (only a raw priorityScore) - the urgent tier is dropped, not guessed.
private fun HomeData.nextUp(): WorkOrderSummaryItem? {
    val today = LocalDate.now()
    val all = clusters.flatMap { it.items }

    fun dueDateOf(item: WorkOrderSummaryItem): LocalDate? = item.dueDate?.let(LocalDate::parse)
    return all.maxWithOrNull(
        compareBy(
            { item -> dueDateOf(item)?.let { it < today } == true },
            { item -> dueDateOf(item)?.let { it == today } == true },
            { item -> item.priorityScore ?: 0.0 },
        ),
    )
}

// Survey work orders are deliberately left out - the dedicated Khảo sát tab (SurveyHubScreen)
// already lists them with richer, survey-specific actions (readiness check, rejected/AI-pending
// states) that this plain task row can't offer, so showing them here too would just duplicate it.
private val TASK_KIND_SECTION_ORDER = listOf(WorkOrderTaskKind.REPAIR, WorkOrderTaskKind.INSPECTION)

private fun HomeData.groupedByTaskKind(): List<Pair<WorkOrderTaskKind, List<WorkOrderSummaryItem>>> {
    val all = clusters.flatMap { it.items }
    return TASK_KIND_SECTION_ORDER.mapNotNull { kind ->
        val items = all.filter { workOrderTaskKindFromWire(it.taskKind) == kind }
        if (items.isEmpty()) null else kind to items
    }
}

// Fixed Navy700 block regardless of Light/Dark theme, same hero-style pattern as
// BrandHeroGradient on F01 - this card is meant to stand out as the one most urgent item, not
// blend into the surrounding surface color.
@Composable
private fun NextUpCard(
    item: WorkOrderSummaryItem,
    clusterLabel: String,
    onClick: () -> Unit,
) {
    val status = workOrderStatusFromWire(item.woStatus)
    val due = dueDateLabel(item.dueDate)
    val mutedWhite = Color.White.copy(alpha = 0.7f)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Navy700, RoundedCornerShape(Radius.block))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "TIẾP THEO", style = OverlineTextStyle, color = mutedWhite)
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        Text(text = item.workOrderId, style = HeadlineTextStyle, color = Color.White)
        Text(
            text = "Địa bàn $clusterLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = mutedWhite,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(imageVector = Icons.Filled.EventAvailable, contentDescription = null, tint = Amber500)
            Text(text = due.text, style = MaterialTheme.typography.bodyMedium, color = Amber500)
        }
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy700),
        ) {
            Text(text = "Mở lệnh")
        }
    }
}

// Fixed Navy700-gradient hero regardless of Light/Dark theme, same BrandHeroGradient as F01 -
// this block is meant to read as a distinct brand surface, not blend into colorScheme.background.
@Composable
private fun HomeHeader(
    metrics: HomeMetrics?,
    syncStatus: SyncStatus,
    lastSyncedAt: Instant?,
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
                // Extra top space to clear NotificationBellBar, which floats over this screen
                // from NavGraph.kt's Scaffold instead of reserving its own layout space (see that
                // file's comment) - without this, SyncPill below sat directly under the bell and
                // lost its tap target.
                .padding(top = TopBarHeight)
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxxl),
    ) {
        Text(
            text = today.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi")) },
            style = LabelTextStyle,
            color = Color.White.copy(alpha = 0.75f),
        )
        Text(text = "Việc hôm nay", style = DisplayTextStyle, color = Color.White)
        Spacer(Modifier.height(Spacing.md))
        SyncPill(status = syncStatus, lastSyncedAt = lastSyncedAt, onClick = onRefresh, onDarkHero = true)
        Spacer(Modifier.height(Spacing.lg))
        MetricRow(metrics = metrics)
    }
}

// 1 combined translucent block with 3 columns divided by hairlines, instead of 3 separate
// cards - matches the F02 redesign mockup. No per-column tinted background for overdue
// anymore (the mockup only differs by text color, not container color).
@Composable
private fun MetricRow(metrics: HomeMetrics?) {
    val dividerColor = Color.White.copy(alpha = 0.12f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(Radius.card))
                .border(1.dp, dividerColor, RoundedCornerShape(Radius.card)),
    ) {
        val cells =
            listOf(
                Triple("Được giao", metrics?.assignedCount, Color.White),
                Triple("Đang xử lý", metrics?.inProgressCount, Green400),
                Triple("Quá hạn", metrics?.overdueCount, ErrorDark),
            )
        cells.forEachIndexed { index, (label, value, valueColor) ->
            if (index > 0) {
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(dividerColor))
            }
            MetricCell(label = label, value = value, valueColor = valueColor, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: Int?,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = Spacing.md, horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value?.toString() ?: "—",
            style = MetricTextStyle,
            color = if (value != null) valueColor else Color.White.copy(alpha = 0.5f),
        )
        Text(text = label, style = LabelTextStyle, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun TaskKindSection(
    kind: WorkOrderTaskKind,
    items: List<WorkOrderSummaryItem>,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = kind.icon(),
                    contentDescription = null,
                    tint = Navy700,
                    modifier = Modifier.size(20.dp),
                )
                Text(text = kind.label(), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "${items.size} việc",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items.forEach { item ->
                TaskRow(item = item, onClick = { onOpenWorkOrder(item.workOrderId) })
            }
        }
    }
}

private val TaskRowAccentWidth = 4.dp
private val TaskRowAccentHeight = 36.dp

@Composable
private fun TaskRow(
    item: WorkOrderSummaryItem,
    onClick: () -> Unit,
) {
    val status = workOrderStatusFromWire(item.woStatus)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .width(TaskRowAccentWidth)
                    .height(TaskRowAccentHeight)
                    .background(status.badgeColors().text, RoundedCornerShape(Radius.badge)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = item.workOrderId, style = MaterialTheme.typography.titleMedium)
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
