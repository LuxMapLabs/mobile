package com.luxmap.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.common.DateFormatUtils
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
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.DueTodayColor
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

    // Home's own ViewModel survives navigating into another screen (work order detail, survey...)
    // and back - its data would otherwise go stale after an action there changes a work order's
    // status (e.g. the engineer submits, or a manager verifies/returns it from the web). Refetch
    // every time this screen's back stack entry becomes the visible one again, not just on first
    // load - but skip the very first ON_RESUME, since init{} already covers that one and retrying
    // too would just flash the loading state right after it.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFirstResume by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (isFirstResume) isFirstResume = false else viewModel.retry()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            // Overlaps the hero's bottom edge by Spacing.xl, same -24px margin-top the mockup
            // uses, so the rounded top corners read as a sheet floating over the navy hero
            // instead of a hard seam. Needs its own opaque background (not just Scaffold's) so
            // the overlap actually occludes the hero gradient underneath it. The clip (not just
            // background) matters once the list scrolls - background alone paints the rounded
            // shape but never cuts the LazyColumn's own square corners, so a scrolled row's edge
            // shows past the rounding at the top instead of following it.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset(y = -Spacing.xl)
                        .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                        .background(MaterialTheme.colorScheme.background),
            ) {
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
            item {
                NextUpCard(
                    item = nextUp,
                    clusterLabel = data.clusterLabelFor(nextUp.workOrderId),
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
                        clusterLabelOf = data::clusterLabelFor,
                        onOpenWorkOrder = onOpenWorkOrder,
                    )
                }
            }
        }
    }
}

private fun HomeData.clusterLabelFor(workOrderId: String): String =
    clusters.first { cluster -> cluster.items.any { it.workOrderId == workOrderId } }.clusterLabel

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

// White elevated card (theme-aware surface, not a fixed navy block anymore) - the F02 redesign
// mockup moved the "TIẾP THEO" eyebrow outside the card as its own section label, and the card
// itself now matches the look of a normal elevated card like the rest of the list.
@Composable
private fun NextUpCard(
    item: WorkOrderSummaryItem,
    clusterLabel: String,
    onClick: () -> Unit,
) {
    val status = workOrderStatusFromWire(item.woStatus)
    val kind = workOrderTaskKindFromWire(item.taskKind)
    val due = dueDateLabel(item.dueDate)
    // Same 3-tier convention WorkOrderDetailScreen.kt already uses for due-date color, reused
    // here instead of inventing a separate rule for this card.
    val dueColor =
        when {
            due.isOverdue -> MaterialTheme.colorScheme.error
            due.isToday -> DueTodayColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = "TIẾP THEO", style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .shadow(elevation = NextUpCardElevation, shape = RoundedCornerShape(Radius.block), clip = false)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.block))
                    .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(text = kind.label(), colors = kind.badgeColors(), icon = kind.icon())
                StatusBadge(text = status.label(), colors = status.badgeColors())
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = item.workOrderId, style = HeadlineTextStyle, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Địa bàn $clusterLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(imageVector = Icons.Filled.EventAvailable, contentDescription = null, tint = dueColor)
                    Text(text = due.text, style = MaterialTheme.typography.bodyMedium, color = dueColor)
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
                shape = RoundedCornerShape(Radius.control),
                colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Color.White),
            ) {
                Text(text = "Mở lệnh")
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private val NextUpCardElevation = 6.dp

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
                .padding(horizontal = Spacing.lg)
                // Top padding matches NotificationBellBar's own top offset (statusBarsPadding +
                // Spacing.xs) so the title lines up level with the floating bell, same
                // align-items:flex-start row the mockup uses - SyncPill moved below the title in
                // a separate row, so nothing here needs extra clearance from the bell anymore.
                .padding(top = Spacing.xs, bottom = Spacing.xxxl),
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
        MetricProgressBar(metrics = metrics)
        Spacer(Modifier.height(Spacing.sm))
        MetricLegendRow(metrics = metrics)
    }
}

// Segmented bar replaces the old 3-box MetricRow, matching the F02 v2 mockup. Same 3 raw
// counts as before - overdueCount is not a mutually exclusive 4th bucket on the backend (it's
// "overdue among assigned/in_progress"), so this just reshapes the same numbers, not a new stat.
@Composable
private fun MetricProgressBar(metrics: HomeMetrics?) {
    val placeholderColor = Color.White.copy(alpha = 0.16f)
    val segments =
        listOfNotNull(
            metrics?.overdueCount?.takeIf { it > 0 }?.let { it to ErrorDark },
            metrics?.inProgressCount?.takeIf { it > 0 }?.let { it to Green400 },
            metrics?.assignedCount?.takeIf { it > 0 }?.let { it to Color.White.copy(alpha = 0.35f) },
        )
    Row(
        modifier = Modifier.fillMaxWidth().height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (segments.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(placeholderColor, RoundedCornerShape(4.dp)),
            )
        } else {
            segments.forEach { (count, color) ->
                Box(
                    modifier =
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Composable
private fun MetricLegendRow(metrics: HomeMetrics?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val cells =
            listOf(
                Triple("Quá hạn", metrics?.overdueCount, ErrorDark),
                Triple("Đang làm", metrics?.inProgressCount, Green400),
                Triple("Chưa bắt đầu", metrics?.assignedCount, Color.White.copy(alpha = 0.5f)),
            )
        cells.forEach { (label, value, dotColor) ->
            MetricLegendCell(label = label, value = value, dotColor = dotColor, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricLegendCell(
    label: String,
    value: Int?,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(Radius.chip))
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
            Text(
                text = value?.toString() ?: "—",
                style = MetricTextStyle.copy(fontSize = 20.sp, lineHeight = 24.sp),
                color = Color.White,
            )
        }
        Text(text = label, style = LabelTextStyle, color = Color.White.copy(alpha = 0.75f))
    }
}

private val TaskKindAvatarSize = 32.dp

@Composable
private fun TaskKindSection(
    kind: WorkOrderTaskKind,
    items: List<WorkOrderSummaryItem>,
    clusterLabelOf: (workOrderId: String) -> String,
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(TaskKindAvatarSize)
                            .background(Navy700, RoundedCornerShape(Radius.chip)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = kind.icon(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(text = kind.label(), style = MaterialTheme.typography.titleMedium)
            }
            StatusBadge(text = "${items.size} việc", colors = WorkOrderStatus.ASSIGNED.badgeColors())
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items.forEach { item ->
                TaskRow(
                    item = item,
                    clusterLabel = clusterLabelOf(item.workOrderId),
                    onClick = { onOpenWorkOrder(item.workOrderId) },
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    item: WorkOrderSummaryItem,
    clusterLabel: String,
    onClick: () -> Unit,
) {
    val status = workOrderStatusFromWire(item.woStatus)
    val due = dueDateLabel(item.dueDate)
    // Same 3-tier due-date color convention as NextUpCard/WorkOrderDetailScreen.kt.
    val dueColor =
        when {
            due.isOverdue -> MaterialTheme.colorScheme.error
            due.isToday -> DueTodayColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = item.workOrderId, style = MaterialTheme.typography.titleMedium)
            Text(text = "$clusterLabel · ${due.text}", style = LabelTextStyle, color = dueColor)
        }
        StatusBadge(text = status.label(), colors = status.badgeColors())
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

// Shaped like the real content below it (NextUpCardSkeleton + 3 TaskRowSkeleton) instead of 3
// generic rows, so the loading state does not jump/resize once real data replaces it.
@Composable
private fun LoadingSkeletonState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBar(width = 96.dp, height = 14.dp, color = MaterialTheme.colorScheme.outline)
        NextUpCardSkeleton()
        repeat(3) { TaskRowSkeleton() }
    }
}

@Composable
private fun NextUpCardSkeleton() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.block))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SkeletonBar(width = 84.dp, height = 22.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            SkeletonBar(width = 64.dp, height = 22.dp, color = MaterialTheme.colorScheme.surfaceVariant)
        }
        SkeletonBar(widthFraction = 0.6f, height = 24.dp, color = MaterialTheme.colorScheme.outline)
        SkeletonBar(widthFraction = 0.85f, height = 14.dp, color = MaterialTheme.colorScheme.surfaceVariant)
        SkeletonBar(
            widthFraction = 1f,
            height = Dimens.minTouchTarget,
            color = WorkOrderStatus.ASSIGNED.badgeColors().background,
            shape = RoundedCornerShape(Radius.control),
        )
    }
}

@Composable
private fun TaskRowSkeleton() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SkeletonBar(widthFraction = 0.4f, height = 16.dp, color = MaterialTheme.colorScheme.outline)
        SkeletonBar(widthFraction = 0.7f, height = 12.dp, color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun SkeletonBar(
    height: Dp,
    color: Color,
    width: Dp? = null,
    widthFraction: Float? = null,
    shape: RoundedCornerShape = RoundedCornerShape(Dimens.radiusSmall),
) {
    val widthModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth(widthFraction ?: 1f)
    Box(modifier = widthModifier.height(height).background(color, shape))
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
