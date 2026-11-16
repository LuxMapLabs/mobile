package com.luxmap.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.TitleTextStyle
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.LuxSegment
import com.luxmap.core.ui.components.LuxSegmented
import com.luxmap.core.ui.components.LuxTabTopBar
import com.luxmap.core.ui.components.MessageState
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.workorder.data.CompletionSyncState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Nav-graph entry point (F02/FM-16) — nối HomeViewModel, đưa HomeScreen thành stateless để dễ
// lái bằng dữ liệu cố định.
@Composable
fun HomeRoute(
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
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
        unreadNotificationCount = unreadNotificationCount,
        onOpenNotifications = onOpenNotifications,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

private val VI = Locale.forLanguageTag("vi")

internal fun homeGreeting(
    username: String?,
    today: LocalDate,
): String {
    val date = today.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", VI)).replaceFirstChar { it.titlecase(VI) }
    val name = username?.takeIf { it.isNotBlank() }
    return if (name != null) "Xin chào, $name · $date" else "Xin chào · $date"
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    username: String?,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    onRetry: () -> Unit,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val today = remember { LocalDate.now() }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LuxTabTopBar(
                title = "Việc hôm nay",
                subtitle = homeGreeting(username, today),
                unreadNotificationCount = unreadNotificationCount,
                onNotificationsClick = onOpenNotifications,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is HomeUiState.Loading -> LoadingSkeletonState()
                is HomeUiState.Success ->
                    HomeContent(
                        data = uiState.data,
                        isStale = uiState.isStale,
                        lastSyncedAt = uiState.lastSyncedAt,
                        completionSyncStates = uiState.completionSyncStates,
                        onOpenWorkOrder = onOpenWorkOrder,
                    )
                is HomeUiState.Empty -> EmptyState()
                is HomeUiState.Error -> ErrorState(message = uiState.message, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    isStale: Boolean,
    lastSyncedAt: Instant?,
    completionSyncStates: Map<String, CompletionSyncState>,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTaskTab.REPAIR) }
    val today = remember { LocalDate.now() }
    val tasks = remember(data) { data.visibleTasks() }
    val summary = remember(tasks, today) { tasks.homeSummary(today) }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SummaryStatsRow(summary = summary)
            LuxSegmented(
                segments = HomeTaskTab.entries.map { LuxSegment(it, "${it.label} · ${tasks.countFor(it)}") },
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
        }
        val visibleItems = remember(tasks, selectedTab, today) { tasks.sortedFor(selectedTab, today) }
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
                    HomeTaskCard(
                        item = item,
                        syncState = completionSyncStates[item.workOrderId],
                        today = today,
                        onClick = { onOpenWorkOrder(item.workOrderId) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryStatsRow(summary: HomeSummary) {
    val overdueColor =
        if (summary.overdue > 0) LuxTheme.colors.dueOverdue else MaterialTheme.colorScheme.onSurface
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SummaryStatCell(
            label = "Tổng nhiệm vụ",
            value = summary.total,
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
        SummaryStatDivider()
        SummaryStatCell(label = "Quá hạn", value = summary.overdue, valueColor = overdueColor)
        SummaryStatDivider()
        SummaryStatCell(
            label = "Đang làm",
            value = summary.inProgress,
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SummaryStatCell(
    label: String,
    value: Int,
    valueColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            modifier = Modifier.widthIn(min = 24.dp),
            textAlign = TextAlign.End,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryStatDivider() {
    Box(
        modifier =
            Modifier
                .padding(vertical = Spacing.xs)
                .width(1.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

// Per-kind empty copy matches the mockup's own TYPE[...].empty entries - distinct from the
// screen's own 4-state Empty (that one means no data at all, not a filter with nothing in it).
@Composable
private fun TabEmptyState(tab: HomeTaskTab) {
    val (icon, iconTint, title, body) =
        when (tab) {
            HomeTaskTab.INSPECTION ->
                EmptyContent(
                    WorkOrderTaskKind.INSPECTION.icon(),
                    WorkOrderTaskKind.INSPECTION.badgeColors().text,
                    "Không có nhiệm vụ kiểm tra",
                    "Lệnh kiểm tra cột đèn và thiết bị được giao sẽ hiện ở đây.",
                )
            HomeTaskTab.REPAIR ->
                EmptyContent(
                    WorkOrderTaskKind.REPAIR.icon(),
                    WorkOrderTaskKind.REPAIR.badgeColors().text,
                    "Không có nhiệm vụ sửa chữa",
                    "Lệnh sửa chữa và bảo trì được giao sẽ hiện ở đây.",
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
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.card)),
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
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.card))
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
                SkeletonBar(widthFraction = 0.45f, height = 14.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Thử lại")
        }
    }
}
