package com.luxmap.feature.survey.ui.plan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.CameraReadinessPanel
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.ReadinessCheckItem
import com.luxmap.core.ui.components.ReadinessCheckStatus
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult
import kotlinx.coroutines.delay

// Camera + fine location are the 2 permissions the F03 readiness checklist can fix in-app (see
// SurveyReadinessInputProvider). Exposure-lock support, storage and battery are device state, not
// something a permission prompt can change.
//
// BLUETOOTH_SCAN/BLUETOOTH_CONNECT are added on API 31+ only: connectGatt() (LuxSensorBleClient,
// called from CaptureViewModel right after this screen hands off to F04) needs them at runtime on
// Android 12+, where they are dangerous permissions - the manifest already declares them with no
// maxSdkVersion. Below API 31, BLUETOOTH/BLUETOOTH_ADMIN cover this through the manifest alone, no
// runtime request needed (and the 31+ constants should not be requested pre-31).
private val CAPTURE_PERMISSIONS: Array<String> =
    buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

// BLUETOOTH_SCAN/BLUETOOTH_CONNECT are not part of SurveyReadinessResult (that model is
// WP2/WP5/Tasks 3/11/12 territory, out of scope for this fix), so this screen tracks them
// separately, only to decide whether "Cấp quyền" still needs to show - CaptureViewModel calls
// LuxSensorBleClient.connect() right after this screen hands off, and connectGatt() needs these on
// API 31+ or it throws a SecurityException.
private fun hasBlePermissions(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        )

private const val TOTAL_READINESS_CHECKS = 7

// F03 is a night-planning screen (Design System v2.0 Dark Mode table), so it forces dark mode
// no matter the system setting, same rule as F04 Capture Mode. One work order per screen - this
// is reached by tapping a single assigned survey work order, not by browsing a list.
@Composable
fun SurveyPlanScreen(
    onBack: () -> Unit,
    onEnterCaptureMode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SurveyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LuxMapTheme(forceDark = true) {
        SurveyPlanContent(
            state = uiState,
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onPermissionsChecked = viewModel::checkReadiness,
            onEnterCaptureMode = onEnterCaptureMode,
            modifier = modifier,
        )
    }
}

// Stateless so UI tests can drive every SurveyPlanUiState branch directly, without a ViewModel or
// Hilt in the loop.
@Composable
fun SurveyPlanContent(
    state: SurveyPlanUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onPermissionsChecked: () -> Unit = {},
    onEnterCaptureMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasBlePermissions by remember { mutableStateOf(hasBlePermissions(context)) }

    Column(modifier.fillMaxSize()) {
        AppTopBar(
            title = "Chuẩn bị khảo sát",
            onBack = onBack,
            subtitle = (state as? SurveyPlanUiState.Success)?.route?.workOrderId,
        )
        when (state) {
            is SurveyPlanUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().testTag("survey_plan_loading"),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

            is SurveyPlanUiState.Success -> {
                val permissionLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                        hasBlePermissions = hasBlePermissions(context)
                        onPermissionsChecked()
                    }
                Column(Modifier.weight(1f)) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        if (state.rejectionReason != null) {
                            InlineBanner(message = state.rejectionReason, tone = InlineBannerTone.ERROR, isDark = true)
                        }
                        Column {
                            Text(state.route.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "${state.route.communeId} · Dự kiến ${state.route.scheduledDate ?: "chưa có"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val readiness = state.readiness
                        if (readiness == null) {
                            Text("Đang kiểm tra thiết bị...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            ReadinessSection(readiness, hasBlePermissions)
                            // Camera, GPS, and (on API 31+) Bluetooth scan/connect are the
                            // permissions this screen can fix without leaving the app (spec gap:
                            // there was no in-app way to grant them before, so a user who never
                            // granted them could never reach F04 at all).
                            if (!readiness.cameraPermissionGranted || !readiness.gpsAvailable || !hasBlePermissions) {
                                PrimaryButton(
                                    text = "Cấp quyền",
                                    onClick = { permissionLauncher.launch(CAPTURE_PERMISSIONS) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Text(
                                text =
                                    "Exposure được khoá cho cả phiên quay. " +
                                        "Không thể bắt đầu nếu thiết bị chưa khoá được exposure.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // hasBlePermissions is also required here (not just readiness.isReady), not
                    // only shown as a hint through the button above - otherwise a user could enter
                    // F04 without it and hit a SecurityException from connectGatt() on API 31+.
                    state.readiness?.let { readiness ->
                        BottomActionBar(
                            ctaText = "Vào chế độ khảo sát",
                            onCtaClick = onEnterCaptureMode,
                            ctaEnabled = readiness.isReady && hasBlePermissions,
                            hint =
                                if (!readiness.isReady || !hasBlePermissions) {
                                    "Còn mục chưa đạt — chưa thể khảo sát"
                                } else {
                                    null
                                },
                        )
                    }
                }
            }

            is SurveyPlanUiState.Empty ->
                Box(Modifier.fillMaxSize()) {
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Không tìm thấy lệnh khảo sát này",
                        subtitle = "Lệnh có thể đã được giao lại hoặc huỷ — quay lại danh sách khảo sát.",
                        action = {},
                    )
                }

            is SurveyPlanUiState.Error ->
                Box(Modifier.fillMaxSize()) {
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = state.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                    ) {
                        PrimaryButton(text = "Thử lại", onClick = onRetry, modifier = Modifier.fillMaxWidth())
                    }
                }
        }
    }
}

@Composable
private fun ReadinessSection(
    readiness: SurveyReadinessResult,
    hasBlePermissions: Boolean,
) {
    val items =
        listOf(
            ReadinessCheckItem("Quyền camera", readiness.cameraPermissionGranted.toStatus()),
            ReadinessCheckItem("Khoá exposure hỗ trợ", readiness.exposureLockSupported.toStatus()),
            ReadinessCheckItem(
                "Đồng hồ cảm biến REALTIME",
                readiness.timestampSourceRealtime.toStatus(),
                fixHint =
                    if (!readiness.timestampSourceRealtime) {
                        "Thiết bị này không được hỗ trợ khảo sát (đồng hồ cảm biến camera không đạt yêu cầu)"
                    } else {
                        null
                    },
            ),
            ReadinessCheckItem("GPS", readiness.gpsAvailable.toStatus()),
            // Heading is intentionally not shown here anymore (project owner decision, 2026-10-02):
            // a missing heading sensor must not block or worry the user at F03 - see the comment on
            // SurveyReadinessResult.isReady for the matching change on the gating side.
            ReadinessCheckItem(
                "Quyền Bluetooth",
                hasBlePermissions.toStatus(),
                fixHint = if (!hasBlePermissions) "Cần quyền Bluetooth để kết nối cảm biến lux" else null,
            ),
            ReadinessCheckItem(
                "Đủ dung lượng trống",
                (readiness.freeStorageBytes >= readiness.requiredStorageBytes).toStatus(),
            ),
            ReadinessCheckItem(
                "Pin đủ (>= 20%)",
                (readiness.batteryPercent >= SurveyReadinessResult.MIN_BATTERY_PERCENT).toStatus(),
            ),
        )
    val passCount = items.count { it.status == ReadinessCheckStatus.PASS }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Kiểm tra thiết bị",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$passCount/$TOTAL_READINESS_CHECKS đạt",
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (passCount == TOTAL_READINESS_CHECKS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        RevealedReadinessChecklist(items)
    }
}

@Composable
private fun RevealedReadinessChecklist(items: List<ReadinessCheckItem>) {
    var revealedCount by remember { mutableStateOf(0) }
    var hasRevealedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        if (hasRevealedOnce) {
            revealedCount = items.size
            return@LaunchedEffect
        }
        items.indices.forEach { index ->
            revealedCount = index + 1
            if (index < items.lastIndex) delay(CHECKLIST_ROW_DELAY_MS)
        }
        hasRevealedOnce = true
    }
    val displayedItems =
        items.mapIndexed { index, item ->
            if (index < revealedCount) item else item.copy(status = ReadinessCheckStatus.CHECKING, fixHint = null)
        }
    CameraReadinessPanel(displayedItems)
}

private const val CHECKLIST_ROW_DELAY_MS = 350L

private fun Boolean.toStatus() = if (this) ReadinessCheckStatus.PASS else ReadinessCheckStatus.FAIL
