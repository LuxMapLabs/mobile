package com.luxmap.feature.survey.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult

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

        is SurveyPlanUiState.Success ->
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
                    )
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
) {
    Column(modifier = Modifier.padding(Spacing.md)) {
        Text(route.surveySweepId, style = MaterialTheme.typography.titleMedium)
        Text("Giao bởi: ${route.assignedByName}", style = MaterialTheme.typography.bodyMedium)
        Text("Ngày dự kiến: ${route.plannedDate}", style = MaterialTheme.typography.bodyMedium)
        Text("${route.roadSegments.size} đoạn đường", style = MaterialTheme.typography.bodySmall)
        if (!isSelected) {
            Button(onClick = onClick) { Text("Kiểm tra sẵn sàng") }
        } else if (readiness == null) {
            Text("Đang kiểm tra thiết bị...", style = MaterialTheme.typography.bodySmall)
        } else {
            ReadinessChecklist(readiness)
            Button(onClick = onEnterCaptureMode, enabled = readiness.isReady) { Text("Vào chế độ khảo sát") }
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
