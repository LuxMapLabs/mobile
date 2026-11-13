package com.luxmap.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.TitleTextStyle
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
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Nav-graph entry point (F02/FM-16) — nối HomeViewModel, đưa HomeScreen thành stateless để dễ
// lái bằng dữ liệu cố định.
@Composable
fun HomeRoute(
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val username by viewModel.username.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
        username = username,
        onOpenWorkOrder = onOpenWorkOrder,
        onRetry = viewModel::retry,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

// F02 Việc hôm nay (Trang chủ) — điểm vào chính. Restyled per the "LuxMap-F02-Viec-Hom-Nay"
// Claude Design mockup: compact navy greeting header (no stats inside it - the global
// NotificationBellBar already floats over this header, see NavGraph.kt), a fixed
// summary-stats + task-kind filter row, then a flat list of work orders sorted
// overdue-first for the selected tab. Theo hệ thống (Light mặc định) — không thuộc danh
// sách Dark bắt buộc ở CLAUDE.md.
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    username: String?,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
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
            HomeHeader(username = username)
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
                            locallyCompletedWorkOrderIds = uiState.locallyCompletedWorkOrderIds,
                            onOpenWorkOrder = onOpenWorkOrder,
                        )
                    is HomeUiState.Empty -> EmptyState()
                    is HomeUiState.Error -> ErrorState(message = uiState.message, onRetry = onRetry)
                }
            }
        }
    }
}

// Fixed Navy700-gradient hero regardless of Light/Dark theme, same BrandHeroGradient as F01 -
// this block is meant to read as a distinct brand surface, not blend into colorScheme.background.
// No stats/sync pill here anymore (moved into the white sheet below, per the new mockup) - the
// global NotificationBellBar (NavGraph.kt) already floats a bell over this header's top-right
// corner, so no bell/avatar is drawn here either.
@Composable
private fun HomeHeader(username: String?) {
    val today =
        remember {
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi")),
            )
        }
    val greetingName = username?.takeIf { it.isNotBlank() } ?: "bạn"
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient)
                .statusBarsPadding()
                // Top padding matches NotificationBellBar's own top offset (statusBarsPadding +
                // Spacing.xs) so the title lines up level with the floating bell.
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xs, bottom = Spacing.xxxl),
    ) {
        Text(
            text = "Xin chào, $greetingName · ${today.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi")) }}",
            style = LabelTextStyle,
            color = Color.White.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(text = "Việc hôm nay", style = DisplayTextStyle, color = Color.White)
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    isStale: Boolean,
    lastSyncedAt: Instant?,
    locallyCompletedWorkOrderIds: Set<String>,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
) {
    // No "Tất cả" tab anymore - defaults to Sửa chữa (repair is this screen's primary work kind)
    // rather than Khảo sát, which would always open on an empty state since survey items never
    // flow into this list (see allVisibleItems()).
    var selectedTab by remember { mutableStateOf(TaskFilterTab.REPAIR) }
    val counts = data.taskFilterTabCounts()
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed, does not scroll with the list below - matches the mockup's own
        // flex-shrink:0 summary+filter block.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SummaryStatsRow(
                total = data.allVisibleItems().size,
                overdue = data.metrics.overdueCount,
                inProgress = data.metrics.inProgressCount,
            )
            TaskFilterTabs(selected = selectedTab, counts = counts, onSelect = { selectedTab = it })
        }
        val visibleItems = data.filteredSortedItems(selectedTab)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.xs, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (isStale) {
                item {
                    InlineBanner(
                        message = "Đang xem dữ liệu đã lưu, cập nhật lúc ${DateFormatUtils.formatInstant(
                            lastSyncedAt,
                        )}",
                        tone = InlineBannerTone.OFFLINE,
                    )
                }
            }
            if (visibleItems.isEmpty()) {
                item { TabEmptyState(tab = selectedTab) }
            } else {
                items(visibleItems, key = { it.workOrderId }) { item ->
                    TaskRow(
                        item = item,
                        clusterLabel = data.clusterLabelFor(item.workOrderId),
                        isLocallyCompleted = item.workOrderId in locallyCompletedWorkOrderIds,
                        onClick = { onOpenWorkOrder(item.workOrderId) },
                    )
                }
            }
        }
    }
}

// No "Tất cả" tab - reuses WorkOrderTaskKind directly so the tab set never drifts from the
// badge/icon mapping used elsewhere.
private enum class TaskFilterTab(val label: String, val kind: WorkOrderTaskKind) {
    INSPECTION("Kiểm tra", WorkOrderTaskKind.INSPECTION),
    REPAIR("Sửa chữa", WorkOrderTaskKind.REPAIR),
    POLE_GPS_UPDATE("Cập nhật GPS", WorkOrderTaskKind.POLE_GPS_UPDATE),
}

// Survey work orders are deliberately left out of this screen's own data (clusters never
// contain task_kind=survey items) - the dedicated Khảo sát tab (SurveyHubScreen) already lists
// them with richer, survey-specific actions (readiness check, rejected/AI-pending states) that
// this plain task row can't offer. There is no SURVEY entry in TaskFilterTab at all - it would
// always show a count of 0 and its own empty state, so it is left out rather than shown disabled.
internal val TASK_KIND_SECTION_ORDER: List<WorkOrderTaskKind> =
    listOf(WorkOrderTaskKind.REPAIR, WorkOrderTaskKind.INSPECTION, WorkOrderTaskKind.POLE_GPS_UPDATE)

private fun HomeData.allVisibleItems(): List<WorkOrderSummaryItem> =
    clusters.flatMap { it.items }.filter { TASK_KIND_SECTION_ORDER.contains(workOrderTaskKindFromWire(it.taskKind)) }

private fun HomeData.taskFilterTabCounts(): Map<TaskFilterTab, Int> {
    val all = allVisibleItems()
    return TaskFilterTab.entries.associateWith { tab ->
        all.count { workOrderTaskKindFromWire(it.taskKind) == tab.kind }
    }
}

// Sort order matches the mockup's rank (overdue first, then due today, then by priority score) -
// the mockup's separate "high priority" tier is dropped, same reasoning as before: the backend
// item shape has no severity/urgent flag, only a raw priorityScore.
private fun HomeData.filteredSortedItems(tab: TaskFilterTab): List<WorkOrderSummaryItem> {
    val filtered = allVisibleItems().filter { workOrderTaskKindFromWire(it.taskKind) == tab.kind }
    return filtered.sortedWith(
        compareByDescending<WorkOrderSummaryItem> { dueDateLabel(it.dueDate).isOverdue }
            .thenByDescending { dueDateLabel(it.dueDate).isToday }
            .thenByDescending { it.priorityScore ?: 0.0 },
    )
}

private fun HomeData.clusterLabelFor(workOrderId: String): String =
    clusters.first { cluster -> cluster.items.any { it.workOrderId == workOrderId } }.clusterLabel

// Simple 3-number row (Tổng nhiệm vụ / Quá hạn / Đang thực hiện), replacing the old progress-bar
// + legend - the new mockup's header has no stats at all, they moved into this plain row inside
// the white sheet. total comes from the actually-visible items list (same count the ALL tab
// shows), not HomeMetrics.assignedCount - overdueCount is not a mutually-exclusive subset of
// assigned/in_progress on the backend, so summing those three for a "total" would double count.
@Composable
private fun SummaryStatsRow(
    total: Int,
    overdue: Int,
    inProgress: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryStatCell(label = "Tổng nhiệm vụ", value = total, valueColor = MaterialTheme.colorScheme.onSurface)
        SummaryStatDivider()
        SummaryStatCell(
            label = "Quá hạn",
            value = overdue,
            valueColor = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        SummaryStatDivider()
        SummaryStatCell(label = "Đang thực hiện", value = inProgress, valueColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SummaryStatCell(
    label: String,
    value: Int,
    valueColor: Color,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = "$value", style = BodyStrongTextStyle, color = valueColor)
        Text(text = label, style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryStatDivider() {
    Box(modifier = Modifier.width(1.dp).height(16.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun TaskFilterTabs(
    selected: TaskFilterTab,
    counts: Map<TaskFilterTab, Int>,
    onSelect: (TaskFilterTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TaskFilterTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val contentColor =
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(Radius.chip))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab.label,
                    style = LabelTextStyle,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(text = "${counts[tab] ?: 0}", style = LabelTextStyle, color = contentColor)
            }
        }
    }
}

// Per-kind empty copy matches the mockup's own TYPE[...].empty entries - distinct from the
// screen's own 4-state Empty (that one means no data at all, not a filter with nothing in it).
@Composable
private fun TabEmptyState(tab: TaskFilterTab) {
    val (icon, iconTint, title, body) =
        when (tab) {
            TaskFilterTab.INSPECTION ->
                EmptyContent(
                    WorkOrderTaskKind.INSPECTION.icon(),
                    WorkOrderTaskKind.INSPECTION.badgeColors().text,
                    "Không có nhiệm vụ kiểm tra",
                    "Lệnh kiểm tra cột đèn và thiết bị được giao sẽ hiện ở đây.",
                )
            TaskFilterTab.REPAIR ->
                EmptyContent(
                    WorkOrderTaskKind.REPAIR.icon(),
                    WorkOrderTaskKind.REPAIR.badgeColors().text,
                    "Không có nhiệm vụ sửa chữa",
                    "Lệnh sửa chữa và bảo trì được giao sẽ hiện ở đây.",
                )
            TaskFilterTab.POLE_GPS_UPDATE ->
                EmptyContent(
                    WorkOrderTaskKind.POLE_GPS_UPDATE.icon(),
                    WorkOrderTaskKind.POLE_GPS_UPDATE.badgeColors().text,
                    "Không có nhiệm vụ cập nhật GPS",
                    "Lệnh đo lại vị trí cột đèn được giao sẽ hiện ở đây.",
                )
        }
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(
                top = Spacing.xxl,
                bottom = Spacing.lg,
                start = Spacing.xl,
                end = Spacing.xl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
        }
        Text(
            text = title,
            style = TitleTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = LabelTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private data class EmptyContent(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val body: String,
)

@Composable
private fun TaskRow(
    item: WorkOrderSummaryItem,
    clusterLabel: String,
    isLocallyCompleted: Boolean,
    onClick: () -> Unit,
) {
    val kind = workOrderTaskKindFromWire(item.taskKind)
    val status = workOrderStatusFromWire(item.woStatus)
    val due = dueDateLabel(item.dueDate)
    val kindColors = kind.badgeColors()
    val isDark = isSystemInDarkTheme()
    // Same 3-tier due-date color convention used across the work-order screens.
    val dueColor =
        when {
            due.isOverdue -> MaterialTheme.colorScheme.error
            due.isToday -> DueTodayColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val dueIcon =
        when {
            due.isOverdue -> Icons.Filled.EventBusy
            due.isToday -> Icons.Filled.Schedule
            else -> Icons.Filled.EventAvailable
        }
    val borderColor =
        if (due.isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, borderColor, RoundedCornerShape(Radius.card)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(kindColors.background, RoundedCornerShape(Radius.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = kind.icon(),
                    contentDescription = null,
                    tint = kindColors.text,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "${kind.label()} · ${item.workOrderId}",
                        style = LabelTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TaskStatusBadge(
                        status = status,
                        isOverdue = due.isOverdue,
                        isLocallyCompleted = isLocallyCompleted,
                        isDark = isDark,
                    )
                }
                Text(text = clusterLabel, style = BodyStrongTextStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(imageVector = dueIcon, contentDescription = null, tint = dueColor, modifier = Modifier.size(16.dp))
            Text(text = due.text, style = LabelTextStyle, color = dueColor, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// Overdue overrides the raw wo_status with its own "Quá hạn" chip, same as the mockup's
// derived "over" status entry - the backend's real wo_status enum has no "overdue" value
// (see CLAUDE.md), so this is purely a client-side display override, not a 7th wire status.
@Composable
private fun TaskStatusBadge(
    status: WorkOrderStatus,
    isOverdue: Boolean,
    isLocallyCompleted: Boolean,
    isDark: Boolean,
) {
    when {
        isLocallyCompleted ->
            StatusBadge(text = "Đã nộp · chờ đồng bộ", colors = WorkOrderStatus.ASSIGNED.badgeColors())
        isOverdue ->
            StatusBadge(
                text = "Quá hạn",
                colors = AssetCondition.OUT.badgeColors(isDark),
                icon = Icons.Filled.ErrorOutline,
            )
        else -> StatusBadge(text = status.label(), colors = status.badgeColors(), icon = status.icon())
    }
}

// Shaped like the real content below it (stats/tabs placeholder + 4 TaskRowSkeleton) instead
// of generic rows, so the loading state does not jump/resize once real data replaces it.
@Composable
private fun LoadingSkeletonState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBar(widthFraction = 1f, height = 24.dp, color = MaterialTheme.colorScheme.surfaceVariant)
        SkeletonBar(
            widthFraction = 1f,
            height = 44.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(Radius.card),
        )
        repeat(4) { TaskRowSkeleton() }
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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.chip)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonBar(widthFraction = 0.45f, height = 14.dp, color = MaterialTheme.colorScheme.outline)
                SkeletonBar(widthFraction = 0.7f, height = 10.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
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
private fun BoxScope.EmptyState() {
    MessageState(
        icon = Icons.Filled.EventAvailable,
        iconColors = SyncStatus.DONE.badgeColors(),
        title = "Không có việc nào cho hôm nay",
        subtitle = "Khi có lệnh sửa chữa mới được giao, danh sách sẽ hiển thị tại đây.",
        action = {},
    )
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
