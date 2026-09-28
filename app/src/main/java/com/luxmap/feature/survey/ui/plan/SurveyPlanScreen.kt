package com.luxmap.feature.survey.ui.plan

import android.Manifest
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult

// The only 2 permissions the F03 readiness checklist can fix in-app (camera + fine location, see
// SurveyReadinessInputProvider). Exposure-lock support, storage and battery are device state, not
// something a permission prompt can change.
private val CAPTURE_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun SurveyPlanScreen(
    onEnterCaptureMode: (surveySweepId: String) -> Unit,
    viewModel: SurveyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

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
            // Camera and GPS permission are the only checklist items this screen can fix without
            // leaving the app (spec gap: there was no in-app way to grant them before, so a user
            // who never granted them could never reach F04 at all).
            if (!readiness.cameraPermissionGranted || !readiness.gpsAvailable) {
                PrimaryButton(text = "Cấp quyền", onClick = onRequestPermissions)
            }
            PrimaryButton(text = "Vào chế độ khảo sát", onClick = onEnterCaptureMode, enabled = readiness.isReady)
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
