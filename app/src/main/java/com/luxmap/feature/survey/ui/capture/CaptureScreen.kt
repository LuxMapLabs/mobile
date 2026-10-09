package com.luxmap.feature.survey.ui.capture

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.theme.Danger600
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.GpsAccuracyIndicator
import com.luxmap.feature.survey.domain.GpsAccuracyGate

@Composable
fun CaptureScreen(
    surveySweepId: String,
    workOrderId: String,
    onSessionPackaged: (sessionId: String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    // Vibrate once per GPS-lost/BLE-gap transition (see CaptureViewModel.warningPulses) instead
    // of using Compose state for this, because a short haptic pulse is a one-shot side effect,
    // not something that should redraw the screen. LaunchedEffect(Unit) starts the collector once
    // and keeps it alive for the whole screen lifetime - it does not restart on recomposition.
    LaunchedEffect(Unit) {
        viewModel.warningPulses.collect {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // One shared flag for the whole window where a Camera2 session may be live: it opens in
    // StartingRecording, runs through Recording, and is still being torn down during Packaging -
    // VideoCaptureSession.stop() only calls stopRepeating() and then waits for the encoder tail
    // before it closes the session. The preview, the orientation lock, and BackHandler all key
    // off this same value, so none of them can drift apart from the others.
    val isCameraSessionLive =
        uiState is CaptureUiState.StartingRecording ||
            uiState is CaptureUiState.Recording ||
            uiState is CaptureUiState.Packaging

    // The foreground service keeps recording even if the screen is left, and nothing can reach a
    // still-running session again except starting a NEW CaptureViewModel — which would then send a
    // second ACTION_START to the same live service (see SurveyCaptureService's isRecording guard).
    // Block system Back for the whole isCameraSessionLive window, not only Recording, so leaving
    // mid-session is not possible from here at all.
    BackHandler(enabled = isCameraSessionLive) {}

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

    // Keep the screen on for the same window. On API 26-27 the live preview swap is not possible
    // at all (VideoCaptureSession.updatePreviewSurface is a no-op there), so stopping the screen
    // from timing out is the only thing that helps - it is a partial fix, since it does not stop
    // the Home button or an incoming call from backgrounding the app. On API 28+ it is still worth
    // setting: it is free, and it means the swap only has to run for a real interruption instead
    // of on every ordinary screen timeout during a 10-30 minute survey.
    DisposableEffect(isCameraSessionLive) {
        val window = (context as? Activity)?.window
        if (isCameraSessionLive) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (isCameraSessionLive) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Side effect lives here, reacting to state, instead of being called inline inside the `when`
    // branch during composition (review feedback) — LaunchedEffect only fires once per new sessionId.
    val packagedSessionId = (uiState as? CaptureUiState.Packaged)?.sessionId
    if (packagedSessionId != null) {
        LaunchedEffect(packagedSessionId) { onSessionPackaged(packagedSessionId) }
    }

    // F04 Capture Mode always uses dark mode, per Design System §Dark Mode theo ngữ cảnh - night
    // survey readability matters more here than following the system theme.
    LuxMapTheme(forceDark = true) {
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
                            // Kept so the Surface built below can be released when its SurfaceTexture
                            // goes away. Without this the Surface is only ever freed by the finalizer.
                            var attachedSurface: Surface? = null
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
                                        val surface = Surface(surfaceTexture)
                                        attachedSurface = surface
                                        viewModel.onPreviewSurfaceReady(surface)
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surfaceTexture: SurfaceTexture,
                                        width: Int,
                                        height: Int,
                                    ) = Unit

                                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                        // Ask the session to detach this Surface. This only POSTS the
                                        // work to the camera thread, so it is NOT ordered against the
                                        // release below: if that thread is busy for a moment, the
                                        // camera can still write into this BufferQueue right after it
                                        // is torn down. Returning true also hands the SurfaceTexture
                                        // back to the platform, which tears it down anyway, so the
                                        // release call below is not what opens that window.
                                        // Closing it properly means returning false (taking ownership)
                                        // and releasing from the camera thread once the detach has
                                        // really run, which needs a completion callback back through
                                        // the service and controller. Left for a follow-up - see the
                                        // real-device checklist for what to watch for.
                                        viewModel.onPreviewSurfaceLost()
                                        attachedSurface?.release()
                                        attachedSurface = null
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                                }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Thin live-status strip at the top, replacing a full Top Bar (Design System §5.1 - F04
            // uses a minimal overlay instead). Only shown while actually recording, since the other
            // states (scanning, connecting, packaging) have nothing live to report yet.
            if (uiState is CaptureUiState.Recording) {
                val state = uiState as CaptureUiState.Recording
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                            .padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GpsAccuracyIndicator(accuracyMeters = state.gpsAccuracyMeters)
                    Text(
                        text = state.latestLuxValue?.let { "%.1f lux".format(it) } ?: "Chưa có dữ liệu lux",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.freeStorageBytes?.let { "${it / 1_000_000} MB trống" } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Anchored to the bottom with an opaque scrim behind it, not centered directly over the
            // live camera feed - a plain Button/Text with no background can end up nearly the same
            // color as whatever the camera is pointed at (a light pole, a bright wall) and become
            // hard to read while recording.
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                        .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                        if (!state.gpsReadyToRecord) {
                            val accuracyLabel =
                                state.gpsAccuracyMeters?.let {
                                    "Độ chính xác hiện tại: %.0fm (cần ≤%.0fm)"
                                        .format(it, GpsAccuracyGate.PROPOSED_THRESHOLD_METERS)
                                } ?: "Đang chờ tín hiệu GPS..."
                            Text(accuracyLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                        Button(
                            onClick = { viewModel.onStartRecording(surveySweepId, workOrderId) },
                            enabled = state.gpsReadyToRecord,
                        ) { Text("Bắt đầu quay") }
                        TextButton(onClick = viewModel::onChangeDevice) { Text("Đổi thiết bị khác") }
                    }

                    is CaptureUiState.StartingRecording ->
                        Text("Đang mở camera...", style = MaterialTheme.typography.bodyLarge)

                    is CaptureUiState.Recording -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Text("● REC", color = Danger600, style = MaterialTheme.typography.labelLarge)
                            Text(formatDuration(state.durationSeconds), style = MaterialTheme.typography.labelLarge)
                            Text(
                                "%.2f km".format(state.distanceMeters / 1000f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        if (state.gpsSignalLost) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "Mất tín hiệu GPS",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        if (state.bleGapDetected) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "Mất kết nối cảm biến ánh sáng — vẫn tiếp tục quay",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        Button(
                            onClick = viewModel::onStopRecording,
                            modifier = Modifier.defaultMinSize(minHeight = 64.dp),
                        ) { Text("Dừng quay") }
                    }

                    is CaptureUiState.Packaging ->
                        Text("Đang đóng gói phiên khảo sát...", style = MaterialTheme.typography.bodyLarge)

                    is CaptureUiState.Packaged ->
                        Text("Đã đóng gói xong", style = MaterialTheme.typography.bodyLarge)

                    // This state is also reached when the camera never started, not only when a real
                    // recording failed to package - so the label does not name the packaging step. The
                    // button is the only way to reach ACTION_STOP on that path, and without it the
                    // foreground service would be left running.
                    is CaptureUiState.PackagingFailed -> {
                        Text(
                            "Không thể hoàn tất phiên khảo sát: ${state.reason}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = viewModel::onDismissFailure) { Text("Dừng và đóng phiên") }
                    }
                }
            }
        }
    }
}

// Formats a duration as mm:ss, e.g. 65 seconds -> "01:05". Minutes are not capped at 59 because a
// survey sweep can run well past an hour, so e.g. 3600 seconds correctly shows as "60:00".
private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
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
