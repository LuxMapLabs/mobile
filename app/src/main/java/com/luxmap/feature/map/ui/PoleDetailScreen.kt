package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.map.data.PoleDetail
import com.luxmap.feature.map.data.PoleDetailFault
import com.luxmap.feature.map.data.PoleDetailFrame
import com.luxmap.feature.map.data.PoleLuminancePoint
import com.luxmap.feature.map.data.PoleRuntimePoint
import kotlinx.coroutines.launch

// Nav-graph entry point — collects PoleDetailViewModel's state and delegates to the stateless
// PoleDetailScreen below, which stays easy to drive with fixed data in @Preview. Also owns the
// SnackbarHostState: actions that have no destination screen yet (menu, CTA, "xem lịch sử đầy
// đủ"...) call showNotImplemented instead of doing nothing — the callback params on
// PoleDetailScreen stay public so a caller can override any one of them once its target screen
// exists, without touching this wiring.
@Composable
fun PoleDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PoleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showNotImplemented: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar("Chức năng đang được hoàn thiện") }
    }
    PoleDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenMenu = showNotImplemented,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

// FM-27 — chi tiết cột đèn mở từ bản đồ (mở rộng từ PoleQuickViewBottomSheet), tách khỏi
// FM-17 vì không cần Work Order gán trước.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoleDetailScreen(
    uiState: PoleDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMenu: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết cột đèn") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Thêm")
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is PoleDetailUiState.Loading -> LoadingState()
                is PoleDetailUiState.Success -> PoleDetailContent(detail = uiState.detail)
                is PoleDetailUiState.Empty -> MessageState(text = "Không tìm thấy cột đèn này")
                is PoleDetailUiState.Error -> MessageState(text = uiState.message)
            }
        }
    }
}

@Composable
private fun PoleDetailContent(
    detail: PoleDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        PoleHeaderSection(detail)
        PriorityAlertCard(detail)
        FixtureInfoSection(detail)
        if (detail.openFaults.isNotEmpty()) {
            OpenFaultsSection(detail.openFaults)
        }
        LuminanceHistorySection(detail.luminanceHistory)
        if (detail.runtimeHistory.isNotEmpty()) {
            RuntimeHistorySection(detail.runtimeHistory)
        }
        if (detail.recentFrames.isNotEmpty()) {
            RecentFramesSection(detail.recentFrames)
        }
    }
}

@Composable
private fun PoleHeaderSection(detail: PoleDetail) {
    val isDark = isSystemInDarkTheme()
    Column {
        // Design System v3.0.1: pole code is the biggest, boldest text on the screen — field
        // crew must recognize which pole this is in under 2 seconds.
        Text(text = detail.poleId, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = detail.segmentName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        StatusBadge(
            text = detail.fixtureStatus.label().uppercase(),
            colors = detail.fixtureStatus.badgeColors(isDark),
        )
        Spacer(Modifier.height(Spacing.sm))
        val updatedAt = DateFormatUtils.formatIsoInstant(detail.determinedAt)
        val sourceLabel = detail.sourceChannel.toSourceChannelLabel()
        Text(
            text = "Cập nhật $updatedAt · Phát hiện từ $sourceLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun String.toSourceChannelLabel(): String =
    when (this) {
        "cv" -> "AI/CV"
        "iot" -> "Cảm biến IoT"
        else -> this
    }

// Design System v3.0.1: priority alert card, colored/worded by fixture status. Hidden for
// NORMAL and UNKNOWN — nothing urgent to surface, so no card is emitted at all (not an empty
// one), which also means PoleDetailContent's spacedBy() adds no extra gap for it.
@Composable
private fun PriorityAlertCard(detail: PoleDetail) {
    val isDark = isSystemInDarkTheme()
    val title: String
    val body: String
    when (detail.fixtureStatus) {
        AssetCondition.DIM -> {
            title = "Cần kiểm tra trong ca làm việc"
            body = dimAlertBody(detail)
        }
        AssetCondition.OUT -> {
            title = "Cần xử lý ngay — đèn đang tắt"
            body = outAlertBody(detail)
        }
        AssetCondition.NORMAL, AssetCondition.UNKNOWN -> return
    }
    val colors = detail.fixtureStatus.badgeColors(isDark)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(Dimens.radiusMedium))
                .border(1.dp, colors.text.copy(alpha = 0.35f), RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = colors.text)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.text)
    }
}

// baseline_ratio of the latest luminance point — same value the trend chart's endpoint dot
// shows — falls back to a percentage-free sentence when there is no history yet.
private fun dimAlertBody(detail: PoleDetail): String {
    val latestPercent = detail.luminanceHistory.lastOrNull()?.let { (it.baselineRatio * 100).toInt() }
    return if (latestPercent != null) {
        "Độ sáng còn $latestPercent% so với mức chuẩn. Đèn có dấu hiệu suy giảm quang thông."
    } else {
        "Đèn có dấu hiệu suy giảm quang thông so với mức chuẩn, cần kiểm tra."
    }
}

private fun outAlertBody(detail: PoleDetail): String {
    val since = DateFormatUtils.formatIsoInstant(detail.determinedAt)
    return "Không phát sáng từ $since. Ảnh hưởng an toàn giao thông ban đêm."
}

@Composable
private fun FixtureInfoSection(detail: PoleDetail) {
    Column {
        Text(text = "Thông số kỹ thuật", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        InfoRow(label = "Loại đèn", value = detail.fixtureType)
        InfoRow(
            label = "Nguồn điện",
            value = if (detail.powerSource == "solar") "Năng lượng mặt trời" else "Lưới điện",
        )
        InfoRow(label = "Công suất", value = "${detail.lampWatt}W")
        InfoRow(label = "Ngày lắp đặt", value = detail.installDate)
        InfoRow(label = "Hết bảo hành", value = detail.warrantyExpiry)
        InfoRow(
            label = "Node IoT",
            value = if (detail.hasIotNode) detail.iotNodeStatus ?: "-" else "Không có",
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OpenFaultsSection(faults: List<PoleDetailFault>) {
    Column {
        Text(text = "Sự cố đang mở", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        faults.forEach { fault ->
            Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                Text(text = fault.faultType, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${fault.severity} · ${fault.faultStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Placeholder list, not a chart yet — charting approach (custom Canvas vs. a new library) is
// still open, decide when this section gets wired to real rendering.
@Composable
private fun LuminanceHistorySection(history: List<PoleLuminancePoint>) {
    Column {
        Text(text = "Lịch sử độ sáng", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        if (history.isEmpty()) {
            Text(
                text = "Chưa có dữ liệu",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            history.takeLast(MAX_HISTORY_ROWS).reversed().forEach { point ->
                InfoRow(
                    label = point.observedAt,
                    value = "${(point.baselineRatio * 100).toInt()}% (${point.classifiedAs.label()})",
                )
            }
        }
    }
}

@Composable
private fun RuntimeHistorySection(history: List<PoleRuntimePoint>) {
    Column {
        Text(text = "Lịch sử thời gian chiếu sáng", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        history.takeLast(MAX_HISTORY_ROWS).reversed().forEach { point ->
            InfoRow(label = point.nightOf, value = "${point.runtimeHours}h")
        }
    }
}

@Composable
private fun RecentFramesSection(frames: List<PoleDetailFrame>) {
    Column {
        Text(text = "Ảnh khảo sát gần đây", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(frames) { frame ->
                // Not tappable yet — whether tapping should open a full-size image needs
                // BE-20's real thumbnail_url behavior confirmed first (see FM-27 note).
                AsyncImage(
                    model = frame.thumbnailUrl,
                    contentDescription = "Ảnh khảo sát ${frame.capturedAt}",
                    modifier =
                        Modifier
                            .size(FRAME_THUMBNAIL_SIZE)
                            .clip(RoundedCornerShape(Dimens.radiusSmall)),
                )
            }
        }
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

private val FRAME_THUMBNAIL_SIZE = 96.dp
private const val MAX_HISTORY_ROWS = 10

private fun sampleDetail() =
    PoleDetail(
        poleId = "POLE-0047",
        segmentId = "SEG-002",
        segmentName = "Tuyến B - đường liên xã",
        lat = 10.964558,
        lng = 106.495788,
        fixtureType = "solar_all_in_one",
        powerSource = "solar",
        lampWatt = 60,
        installDate = "2023-01-04",
        warrantyExpiry = "2028-01-04",
        fixtureStatus = AssetCondition.DIM,
        statusConfidence = 0.81,
        determinedAt = "2026-08-19T12:00:00Z",
        sourceChannel = "cv",
        hasIotNode = true,
        iotNodeStatus = "online",
        luminanceHistory =
            listOf(
                PoleLuminancePoint("2026-08-18T20:00:00Z", 0.706, AssetCondition.DIM),
                PoleLuminancePoint("2026-08-19T20:00:00Z", 0.733, AssetCondition.DIM),
            ),
        runtimeHistory =
            listOf(
                PoleRuntimePoint("2026-08-18", 6.98),
                PoleRuntimePoint("2026-08-19", 6.65),
            ),
        openFaults =
            listOf(
                PoleDetailFault("FAULT-0027", "runtime_decline", "medium", "detected"),
            ),
        recentFrames =
            listOf(
                PoleDetailFrame("FRM-88213", "2026-08-19T12:00:00Z", "/api/v1/frames/FRM-88213/thumbnail"),
            ),
    )

@Preview(showBackground = true)
@Composable
private fun PoleDetailScreenLoadingPreview() {
    PoleDetailScreen(uiState = PoleDetailUiState.Loading, onBack = {})
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun PoleDetailScreenSuccessPreview() {
    PoleDetailScreen(uiState = PoleDetailUiState.Success(detail = sampleDetail()), onBack = {})
}

@Preview(showBackground = true)
@Composable
private fun PoleDetailScreenEmptyPreview() {
    PoleDetailScreen(uiState = PoleDetailUiState.Empty, onBack = {})
}

@Preview(showBackground = true)
@Composable
private fun PoleDetailScreenErrorPreview() {
    PoleDetailScreen(uiState = PoleDetailUiState.Error(message = "Không tải được dữ liệu"), onBack = {})
}
