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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult

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

@Composable
fun SurveyPlanScreen(
    onEnterCaptureMode: (surveySweepId: String) -> Unit,
    viewModel: SurveyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var hasBlePermissions by remember { mutableStateOf(hasBlePermissions(context)) }

    when (val state = uiState) {
        is SurveyPlanUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is SurveyPlanUiState.Success -> {
            // Re-check readiness after the permission prompt closes, no matter the result, so the
            // checklist shows the real state right away instead of needing the user to leave and
            // come back to this screen (onRouteSelected already re-runs
            // SurveyReadinessInputProvider.gather()).
            val permissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    hasBlePermissions = hasBlePermissions(context)
                    state.selectedSurveySweepId?.let { viewModel.onRouteSelected(it) }
                }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.routes) { route ->
                    SurveyRouteCard(
                        route = route,
                        isSelected = route.surveySweepId == state.selectedSurveySweepId,
                        readiness = if (route.surveySweepId == state.selectedSurveySweepId) state.readiness else null,
                        hasBlePermissions = hasBlePermissions,
                        onClick = { viewModel.onRouteSelected(route.surveySweepId) },
                        onEnterCaptureMode = { onEnterCaptureMode(route.surveySweepId) },
                        onRequestPermissions = { permissionLauncher.launch(CAPTURE_PERMISSIONS) },
                    )
                }
            }
        }

        is SurveyPlanUiState.Empty ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa có tuyến khảo sát nào được giao", style = MaterialTheme.typography.bodyLarge)
            }

        is SurveyPlanUiState.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
            }
    }
}

@Composable
private fun SurveyRouteCard(
    route: AssignedSurveyRoute,
    isSelected: Boolean,
    readiness: SurveyReadinessResult?,
    hasBlePermissions: Boolean,
    onClick: () -> Unit,
    onEnterCaptureMode: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.md)) {
        Text(route.surveySweepId, style = MaterialTheme.typography.titleMedium)
        Text("Giao bởi: ${route.assignedByName}", style = MaterialTheme.typography.bodyMedium)
        Text("Ngày dự kiến: ${route.plannedDate}", style = MaterialTheme.typography.bodyMedium)
        Text("${route.roadSegments.size} đoạn đường", style = MaterialTheme.typography.bodySmall)
        if (!isSelected) {
            PrimaryButton(text = "Kiểm tra sẵn sàng", onClick = onClick)
        } else if (readiness == null) {
            Text("Đang kiểm tra thiết bị...", style = MaterialTheme.typography.bodySmall)
        } else {
            ReadinessChecklist(readiness)
            // Camera, GPS, and (on API 31+) Bluetooth scan/connect are the permissions this screen
            // can fix without leaving the app (spec gap: there was no in-app way to grant them
            // before, so a user who never granted them could never reach F04 at all). Bluetooth is
            // not part of the readiness checklist model itself (out of scope for this fix), so it
            // is checked separately here, only to decide whether this button still needs to show.
            if (!readiness.cameraPermissionGranted || !readiness.gpsAvailable || !hasBlePermissions) {
                PrimaryButton(text = "Cấp quyền", onClick = onRequestPermissions)
            }
            // hasBlePermissions is also required here (not just readiness.isReady), not only shown
            // as a hint through the button above - otherwise a user could enter F04 without it and
            // hit a SecurityException from connectGatt() on API 31+.
            PrimaryButton(
                text = "Vào chế độ khảo sát",
                onClick = onEnterCaptureMode,
                enabled = readiness.isReady && hasBlePermissions,
            )
        }
    }
}

@Composable
private fun ReadinessChecklist(readiness: SurveyReadinessResult) {
    Column {
        ReadinessRow("Quyền camera", readiness.cameraPermissionGranted)
        ReadinessRow("Khoá exposure hỗ trợ", readiness.exposureLockSupported)
        ReadinessRow("Đồng hồ cảm biến REALTIME", readiness.timestampSourceRealtime)
        ReadinessRow("GPS", readiness.gpsAvailable)
        ReadinessRow("Đủ dung lượng trống", readiness.freeStorageBytes >= readiness.requiredStorageBytes)
        ReadinessRow("Pin đủ (>= 20%)", readiness.batteryPercent >= SurveyReadinessResult.MIN_BATTERY_PERCENT)
        if (!readiness.timestampSourceRealtime) {
            // Hard block, not just a warning (spec §10) — the label makes clear this is a device
            // support issue, not a transient check that will pass on retry.
            Text(
                "Thiết bị này không được hỗ trợ khảo sát (đồng hồ cảm biến camera không đạt yêu cầu)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReadinessRow(
    label: String,
    passed: Boolean,
) {
    Text(
        text = "${if (passed) "✓" else "✗"} $label",
        color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
