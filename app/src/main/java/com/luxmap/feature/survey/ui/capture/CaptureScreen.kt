package com.luxmap.feature.survey.ui.capture

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.ble.LuxDevice

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

    val context = LocalContext.current
    // One shared flag for the whole window where a Camera2 session may be live: it opens in
    // StartingRecording, runs through Recording, and is still being torn down during Packaging -
    // VideoCaptureSession.stop() only calls stopRepeating() and then waits for the encoder tail
    // before it closes the session. The preview below and the orientation lock both key off this
    // same value, so the two can never drift apart.
    val isCameraSessionLive =
        uiState is CaptureUiState.StartingRecording ||
            uiState is CaptureUiState.Recording ||
            uiState is CaptureUiState.Packaging
    // The preview Surface (below) is a live target on that camera session - a physical rotation
    // would otherwise destroy and recreate this Activity (no orientation lock or configChanges
    // declared for it), tearing that Surface down while the camera is still using it.
    // Use SCREEN_ORIENTATION_LOCKED, not SCREEN_ORIENTATION_PORTRAIT: LOCKED pins whatever
    // rotation the screen is in right now, while PORTRAIT asks for one specific rotation. If the
    // user starts recording while holding the phone in landscape, PORTRAIT would itself force a
    // configuration change and recreate the Activity - causing the very problem this code is here
    // to stop. LOCKED can never do that, and it still blocks every later rotation.
    // Locking only for this window, not the whole app, keeps every other screen unchanged.
    DisposableEffect(isCameraSessionLive) {
        val activity = context as? Activity
        if (isCameraSessionLive) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
        onDispose {
            if (isCameraSessionLive) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Side effect lives here, reacting to state, instead of being called inline inside the `when`
    // branch during composition (review feedback) — LaunchedEffect only fires once per new sessionId.
    val packagedSessionId = (uiState as? CaptureUiState.Packaged)?.sessionId
    if (packagedSessionId != null) {
        LaunchedEffect(packagedSessionId) { onSessionPackaged(packagedSessionId) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Shown for the whole isCameraSessionLive window from the SAME `if` branch, so Compose
        // keeps the same TextureView (and the same underlying Surface) alive the entire time
        // instead of tearing it down and building it again - the Camera2 capture session is built
        // once, against this exact Surface object, when the preview becomes ready. This must stay
        // up through Packaging too: dropping the view there would abandon the Surface while the
        // repeating request can still be producing into it, which some devices report as a
        // capture error and can hurt the tail of the recording.
        if (isCameraSessionLive) {
            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        surfaceTextureListener =
                            object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                    // Must match a size the camera can actually use as a second,
                                    // simultaneous stream alongside the 1920x1080 encoder surface -
                                    // reusing that same resolution is the one already known to
                                    // work. Whether every device accepts two streams at this size
                                    // together still needs a real-device check (see Review Focus).
                                    surfaceTexture.setDefaultBufferSize(1920, 1080)
                                    viewModel.onPreviewSurfaceReady(Surface(surfaceTexture))
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) = Unit

                                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture) = true

                                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                            }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column {
            when (val state = uiState) {
                is CaptureUiState.Connecting -> {
                    val label =
                        state.deviceName?.let { "Đang kết nối tới $it..." }
                            ?: "Đang kết nối cảm biến ánh sáng..."
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::onChangeDevice) { Text("Đổi thiết bị khác") }
                }

                is CaptureUiState.Scanning ->
                    if (state.devices.isEmpty()) {
                        Text("Đang tìm cảm biến ánh sáng gần đây...", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text("Chọn cảm biến ánh sáng:", style = MaterialTheme.typography.bodyLarge)
                        LazyColumn {
                            items(state.devices) { device ->
                                DeviceRow(device = device, onClick = { viewModel.onDeviceSelected(device) })
                            }
                        }
                    }

                is CaptureUiState.ScanTimedOut -> {
                    Text(
                        "Không tìm thấy cảm biến ánh sáng nào gần đây. Kiểm tra pin và khoảng cách rồi thử lại.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = viewModel::onRetryScan) { Text("Quét lại") }
                }

                is CaptureUiState.Ready -> {
                    Button(onClick = { viewModel.onStartRecording(surveySweepId) }) { Text("Bắt đầu quay") }
                    TextButton(onClick = viewModel::onChangeDevice) { Text("Đổi thiết bị khác") }
                }

                is CaptureUiState.StartingRecording ->
                    Text("Đang mở camera...", style = MaterialTheme.typography.bodyLarge)

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

@Composable
private fun DeviceRow(
    device: LuxDevice,
    onClick: () -> Unit,
) {
    Button(onClick = onClick) {
        Text(device.name ?: device.address)
    }
}
