package com.luxmap.feature.survey.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CaptureScreen(
    surveySweepId: String,
    onSessionPackaged: (sessionId: String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // The foreground service keeps recording even if the screen is left, and nothing can reach a
    // still-running session again except starting a NEW CaptureViewModel — which would then send a
    // second ACTION_START to the same live service (see SurveyCaptureService's isRecording guard).
    // Block system Back while Recording so leaving mid-session is not possible from here at all.
    BackHandler(enabled = uiState is CaptureUiState.Recording) {}

    // Side effect lives here, reacting to state, instead of being called inline inside the `when`
    // branch during composition (review feedback) — LaunchedEffect only fires once per new sessionId.
    val packagedSessionId = (uiState as? CaptureUiState.Packaged)?.sessionId
    if (packagedSessionId != null) {
        LaunchedEffect(packagedSessionId) { onSessionPackaged(packagedSessionId) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            when (val state = uiState) {
                is CaptureUiState.AwaitingBleConnection ->
                    Text("Đang kết nối cảm biến ánh sáng...", style = MaterialTheme.typography.bodyLarge)

                is CaptureUiState.Ready ->
                    Button(onClick = {
                        // Same hardcoded address the ViewModel already used to connect() in init —
                        // tracked in docs/contract-drift.md.
                        viewModel.onStartRecording(
                            surveySweepId,
                            luxDeviceAddress = CaptureViewModel.KNOWN_LUX_DEVICE_ADDRESS,
                        )
                    }) { Text("Bắt đầu quay") }

                is CaptureUiState.Recording -> {
                    if (state.gpsSignalLost) {
                        Text(
                            "Mất tín hiệu GPS",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (state.bleGapDetected) {
                        Text(
                            "Mất kết nối cảm biến ánh sáng — vẫn tiếp tục quay",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Button(onClick = viewModel::onStopRecording) { Text("Dừng quay") }
                }

                is CaptureUiState.Packaging ->
                    Text("Đang đóng gói phiên khảo sát...", style = MaterialTheme.typography.bodyLarge)

                is CaptureUiState.Packaged ->
                    Text("Đã đóng gói xong", style = MaterialTheme.typography.bodyLarge)

                is CaptureUiState.PackagingFailed ->
                    Text(
                        "Đóng gói thất bại: ${state.reason}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
            }
        }
    }
}
