package com.luxmap.feature.map.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.map.data.PoleDetail
import com.luxmap.feature.map.data.PoleDetailFault
import com.luxmap.feature.map.data.PoleDetailFrame
import com.luxmap.feature.map.data.PoleLuminancePoint
import com.luxmap.feature.map.data.PoleRuntimePoint
import kotlinx.coroutines.launch
import java.util.Locale

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
        onStartSurvey = showNotImplemented,
        onCreateWorkOrder = showNotImplemented,
        onViewOnMap = showNotImplemented,
        onReportFault = showNotImplemented,
        onViewFullHistory = showNotImplemented,
        onOpenFault = { showNotImplemented() },
        onViewAllFrames = showNotImplemented,
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
    onStartSurvey: () -> Unit = {},
    onCreateWorkOrder: () -> Unit = {},
    onViewOnMap: () -> Unit = {},
    onReportFault: () -> Unit = {},
    onViewFullHistory: () -> Unit = {},
    onOpenFault: (faultId: String) -> Unit = {},
    onViewAllFrames: () -> Unit = {},
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
                is PoleDetailUiState.Success ->
                    PoleDetailContent(
                        detail = uiState.detail,
                        onStartSurvey = onStartSurvey,
                        onCreateWorkOrder = onCreateWorkOrder,
                        onViewOnMap = onViewOnMap,
                        onReportFault = onReportFault,
                        onViewFullHistory = onViewFullHistory,
                        onOpenFault = onOpenFault,
                        onViewAllFrames = onViewAllFrames,
                    )
                is PoleDetailUiState.Empty -> MessageState(text = "Không tìm thấy cột đèn này")
                is PoleDetailUiState.Error -> MessageState(text = uiState.message)
            }
        }
    }
}

@Composable
private fun PoleDetailContent(
    detail: PoleDetail,
    onStartSurvey: () -> Unit,
    onCreateWorkOrder: () -> Unit,
    onViewOnMap: () -> Unit,
    onReportFault: () -> Unit,
    onViewFullHistory: () -> Unit,
    onOpenFault: (faultId: String) -> Unit,
    onViewAllFrames: () -> Unit,
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
        ActionButtonsRow(
            detail = detail,
            onStartSurvey = onStartSurvey,
            onCreateWorkOrder = onCreateWorkOrder,
            onViewOnMap = onViewOnMap,
            onReportFault = onReportFault,
        )
        LuminanceTrendCard(detail = detail, onViewFullHistory = onViewFullHistory)
        FixtureInfoSection(detail)
        if (detail.openFaults.isNotEmpty()) {
            OpenFaultsSection(faults = detail.openFaults, onOpenFault = onOpenFault)
        }
        // Always shown, even when empty — a small empty state instead of skipping the
        // section entirely (Design System v3.0.1: "không để khoảng trống lớn").
        RecentFramesSection(frames = detail.recentFrames, onViewAllFrames = onViewAllFrames)
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

// Primary CTA label/action follows fixture status; secondary is "Xem trên bản đồ" except for
// NORMAL/UNKNOWN, where "Báo sự cố" is more useful than a map link once nothing looks wrong.
@Composable
private fun ActionButtonsRow(
    detail: PoleDetail,
    onStartSurvey: () -> Unit,
    onCreateWorkOrder: () -> Unit,
    onViewOnMap: () -> Unit,
    onReportFault: () -> Unit,
) {
    val primaryLabel: String
    val onPrimaryClick: () -> Unit
    val secondaryLabel: String
    val onSecondaryClick: () -> Unit
    when (detail.fixtureStatus) {
        AssetCondition.OUT -> {
            primaryLabel = "Tạo lệnh sửa chữa"
            onPrimaryClick = onCreateWorkOrder
            secondaryLabel = "Xem trên bản đồ"
            onSecondaryClick = onViewOnMap
        }
        AssetCondition.DIM -> {
            primaryLabel = "Bắt đầu khảo sát"
            onPrimaryClick = onStartSurvey
            secondaryLabel = "Xem trên bản đồ"
            onSecondaryClick = onViewOnMap
        }
        AssetCondition.NORMAL, AssetCondition.UNKNOWN -> {
            primaryLabel = "Bắt đầu khảo sát"
            onPrimaryClick = onStartSurvey
            secondaryLabel = "Báo sự cố"
            onSecondaryClick = onReportFault
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PrimaryButton(
            text = primaryLabel,
            onClick = onPrimaryClick,
            modifier = Modifier.fillMaxWidth().height(PRIMARY_CTA_HEIGHT),
        )
        OutlinedButton(
            onClick = onSecondaryClick,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Dimens.radiusMedium),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        ) {
            Text(text = secondaryLabel, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// Design System v3.0.1 "Thông tin nhanh" — exactly these 5 rows (no fixture_type/"Loại đèn"
// row, unlike the previous version). Card container matches LuminanceTrendCard's style
// (surface + outline border) for consistency between the two cards on this screen.
@Composable
private fun FixtureInfoSection(detail: PoleDetail) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.lg),
    ) {
        Text(text = "Thông tin nhanh", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        InfoRow(icon = Icons.Filled.Bolt, label = "Công suất", value = "${detail.lampWatt}W")
        InfoRow(
            icon = if (detail.powerSource == "solar") Icons.Filled.WbSunny else Icons.Filled.Power,
            label = "Nguồn điện",
            value = if (detail.powerSource == "solar") "Năng lượng mặt trời" else "Lưới điện",
        )
        InfoRow(
            icon = Icons.Filled.Wifi,
            label = "IoT",
            value = if (detail.hasIotNode) detail.iotNodeStatus?.toIotStatusLabel() ?: "-" else "Không có",
        )
        InfoRow(icon = Icons.Filled.VerifiedUser, label = "Bảo hành", value = "Đến ${detail.warrantyExpiry}")
        InfoRow(
            icon = Icons.Filled.LocationOn,
            label = "Vị trí",
            value = "%.4f, %.4f".format(Locale.US, detail.lat, detail.lng),
        )
    }
}

private fun String.toIotStatusLabel(): String =
    when (this) {
        "online" -> "Online"
        "offline" -> "Mất kết nối"
        else -> this
    }

// Icon (left) + label (small, secondary) above value (bold) — matches the "Thông tin nhanh"
// mockup, different from the label/value-on-one-line style used elsewhere on this screen.
@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Design System v3.0.1: each fault is its own tappable card (icon + translated title +
// translated "severity · status" + chevron), title carries the count. Translation functions
// have an `else -> this` fallback since the full backend enum isn't known yet.
@Composable
private fun OpenFaultsSection(
    faults: List<PoleDetailFault>,
    onOpenFault: (faultId: String) -> Unit,
) {
    Column {
        Text(text = "Sự cố đang mở (${faults.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            faults.forEach { fault ->
                FaultCard(fault = fault, onClick = { onOpenFault(fault.faultId) })
            }
        }
    }
}

@Composable
private fun FaultCard(
    fault: PoleDetailFault,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    // Reuse OUT's badge colors for the icon circle — a fault card is always in the "something's
    // wrong" tone regardless of the pole's overall fixture_status.
    val colors = AssetCondition.OUT.badgeColors(isDark)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusMedium))
                .clickable(onClick = onClick)
                .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(FAULT_ICON_SIZE).background(colors.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = fault.faultType.toFaultTypeLabel(), style = MaterialTheme.typography.labelLarge)
            val severityLabel = fault.severity.toSeverityLabel()
            val statusLabel = fault.faultStatus.toFaultStatusLabel()
            Text(
                text = "$severityLabel · $statusLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Mở chi tiết sự cố",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun String.toFaultTypeLabel(): String =
    when (this) {
        "runtime_decline" -> "Suy giảm thời gian chiếu sáng"
        "no_light_output" -> "Mất tín hiệu ánh sáng"
        else -> this
    }

private fun String.toSeverityLabel(): String =
    when (this) {
        "low" -> "Mức thấp"
        "medium" -> "Mức trung bình"
        "high" -> "Mức cao"
        else -> this
    }

private fun String.toFaultStatusLabel(): String =
    when (this) {
        "detected" -> "Đã phát hiện"
        "confirmed" -> "Đã xác nhận"
        "resolved" -> "Đã xử lý"
        else -> this
    }

// Always rendered, even with 0 frames — shows a small empty state instead of the section
// disappearing entirely (Design System v3.0.1: "không để khoảng trống lớn").
@Composable
private fun RecentFramesSection(
    frames: List<PoleDetailFrame>,
    onViewAllFrames: () -> Unit,
) {
    Column {
        Text(text = "Ảnh khảo sát gần đây", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        if (frames.isEmpty()) {
            Text(
                text = "Chưa có ảnh khảo sát",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(frames) { frame -> FrameThumbnail(frame) }
                item { ViewMoreFramesTile(onClick = onViewAllFrames) }
            }
        }
    }
}

@Composable
private fun FrameThumbnail(frame: PoleDetailFrame) {
    Box(modifier = Modifier.size(FRAME_THUMBNAIL_SIZE).clip(RoundedCornerShape(Dimens.radiusSmall))) {
        // Not tappable yet — whether tapping should open a full-size image needs BE-20's real
        // thumbnail_url behavior confirmed first (see FM-27 note).
        AsyncImage(
            model = frame.thumbnailUrl,
            contentDescription = "Ảnh khảo sát ${frame.capturedAt}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(FrameDateOverlayColor)
                    .padding(horizontal = Spacing.xs, vertical = 4.dp),
        ) {
            Text(
                text = DateFormatUtils.formatIsoDateOnly(frame.capturedAt),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ViewMoreFramesTile(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(FRAME_THUMBNAIL_SIZE)
                .clip(RoundedCornerShape(Dimens.radiusSmall))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Xem thêm",
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

private val FRAME_THUMBNAIL_SIZE = 96.dp
private val PRIMARY_CTA_HEIGHT = 52.dp
private val FAULT_ICON_SIZE = 36.dp
private val FrameDateOverlayColor = Color.Black.copy(alpha = 0.55f)

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
