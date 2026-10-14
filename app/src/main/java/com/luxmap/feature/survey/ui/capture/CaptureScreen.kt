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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.theme.Amber500
import com.luxmap.core.theme.Danger600
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.CaptureWarning
import com.luxmap.core.ui.components.HudChip
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.gpsAccuracyLabel
import com.luxmap.feature.survey.domain.GpsAccuracyGate
import kotlinx.coroutines.launch

@Composable
private fun RecPill(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(Dimens.radiusPill))
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.size(10.dp).background(Danger600, CircleShape))
        Text("REC", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

private data class CaptureHudValues(
    val gpsAccuracyMeters: Float?,
    val gpsSignalLost: Boolean,
    val latestLuxValue: Float?,
    val freeStorageBytes: Long?,
)

@Composable
fun CaptureScreen(
    surveySweepId: String,
    workOrderId: String,
    onClose: () -> Unit,
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

            // One top-aligned column instead of several independently Box-aligned overlays, so the
            // close button row / HUD chips / REC pill stack instead of drawing on top of each other.
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // The close button is only shown where system Back already works today (see
                // BackHandler above) - same window, same meaning, just reachable without a gesture.
                if (!isCameraSessionLive) {
                    IconButton(
                        onClick = onClose,
                        modifier =
                            Modifier
                                .align(Alignment.Start)
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.68f), CircleShape),
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng", tint = Color.White)
                    }
                }

                if (uiState is CaptureUiState.Recording) {
                    RecPill()
                }

                // HUD chips float over the full-bleed preview for every pre-packaging state, not
                // only while actually recording - Ready already has a live GPS reading worth
                // showing before the user even presses "Bat dau quay".
                val showHud =
                    uiState is CaptureUiState.Ready ||
                        uiState is CaptureUiState.StartingRecording ||
                        uiState is CaptureUiState.Recording
                if (showHud) {
                    val (gpsAccuracyMeters, gpsSignalLost, latestLuxValue, freeStorageBytes) =
                        when (val state = uiState) {
                            is CaptureUiState.Ready -> CaptureHudValues(state.gpsAccuracyMeters, false, null, null)
                            is CaptureUiState.Recording ->
                                CaptureHudValues(
                                    state.gpsAccuracyMeters,
                                    state.gpsSignalLost,
                                    state.latestLuxValue,
                                    state.freeStorageBytes,
                                )
                            else -> CaptureHudValues(null, false, null, null)
                        }
                    CaptureHudRow(
                        gpsAccuracyMeters = gpsAccuracyMeters,
                        gpsSignalLost = gpsSignalLost,
                        latestLuxValue = latestLuxValue,
                        freeStorageBytes = freeStorageBytes,
                    )
                }
            }

            CaptureBottomPanelContent(
                state = uiState,
                surveySweepId = surveySweepId,
                workOrderId = workOrderId,
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

// This state is also reached when the camera never started, not only when a real recording
// failed to package - so PackagingFailed's label does not name the packaging step. Its button is
// the only way to reach ACTION_STOP on that path, and without it the foreground service would be
// left running.
@Composable
internal fun CaptureBottomPanelContent(
    state: CaptureUiState,
    surveySweepId: String,
    workOrderId: String,
    viewModel: CaptureViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.background,
                    RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
                ).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is CaptureUiState.Connecting -> {
                val label =
                    state.deviceName?.let { "Đang kết nối tới $it..." }
                        ?: "Đang kết nối cảm biến ánh sáng..."
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TextButton(onClick = viewModel::onChangeDevice) { Text("Đổi thiết bị khác") }
            }

            is CaptureUiState.Scanning ->
                if (state.devices.isEmpty()) {
                    Text("Đang tìm cảm biến ánh sáng gần đây...", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("Chọn cảm biến ánh sáng:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(Spacing.sm))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(state.devices) { device ->
                            DeviceRow(device = device, onClick = { viewModel.onDeviceSelected(device) })
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Chưa kết nối ổn định cảm biến thì chưa quay được.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            is CaptureUiState.ScanTimedOut -> {
                Text(
                    "Không tìm thấy cảm biến ánh sáng nào gần đây. Kiểm tra pin và khoảng cách rồi thử lại.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrimaryButton(text = "Quét lại", onClick = viewModel::onRetryScan, modifier = Modifier.fillMaxWidth())
            }

            is CaptureUiState.Ready -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Cảm biến ánh sáng đã kết nối", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = viewModel::onChangeDevice) { Text("Đổi") }
                }
                if (!state.gpsReadyToRecord) {
                    val accuracyLabel =
                        state.gpsAccuracyMeters?.let {
                            "Độ chính xác hiện tại: %.0fm (cần ≤%.0fm, giữ ổn định %ds)"
                                .format(
                                    it,
                                    GpsAccuracyGate.PROPOSED_THRESHOLD_METERS,
                                    GpsAccuracyGate.PROPOSED_HOLD_DURATION_MS / 1000,
                                )
                        } ?: "Đang chờ tín hiệu GPS..."
                    Text(accuracyLabel, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(Spacing.sm))
                StartRecordingButton(
                    enabled = state.gpsReadyToRecord,
                    onClick = { viewModel.onStartRecording(surveySweepId, workOrderId) },
                )
                Spacer(Modifier.height(Spacing.sm))
                Text("Bắt đầu quay", style = MaterialTheme.typography.bodyLarge)
            }

            is CaptureUiState.StartingRecording -> {
                Text("Đang mở camera và khoá exposure...", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Giữ máy cố định. Ghi hình bắt đầu ngay khi khoá xong.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            is CaptureUiState.Recording -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("● REC", color = Danger600, style = MaterialTheme.typography.labelLarge)
                    Text(formatDuration(state.durationSeconds), style = MaterialTheme.typography.labelLarge)
                    Text("%.2f km".format(state.distanceMeters / 1000f), style = MaterialTheme.typography.labelLarge)
                }
                if (state.gpsSignalLost) {
                    CaptureWarning(
                        message = "Mất tín hiệu GPS",
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    )
                }
                if (state.bleGapDetected) {
                    CaptureWarning(
                        message = "Mất kết nối cảm biến ánh sáng — vẫn tiếp tục quay",
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    )
                }
                HoldToStopButton(
                    onHoldComplete = viewModel::onStopRecording,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }

            is CaptureUiState.Packaging -> {
                Text("Đang đóng gói phiên khảo sát...", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Video, log GPS + lux và manifest được ghi vào thiết bị trước khi tải lên.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            is CaptureUiState.Packaged ->
                Text("Đã đóng gói xong", style = MaterialTheme.typography.bodyLarge)

            is CaptureUiState.PackagingFailed -> {
                Text(
                    "Không thể hoàn tất phiên khảo sát: ${state.reason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrimaryButton(
                    text = "Dừng và đóng phiên",
                    onClick = viewModel::onDismissFailure,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

internal const val HOLD_TO_STOP_DURATION_MS = 900L
internal const val HOLD_TO_STOP_BUTTON_TAG = "holdToStopButton"

// Press-and-hold instead of a plain tap (spec §6): avoids triggering a stop from an accidental
// bump while holding the phone on a moving vehicle. onHoldComplete fires once, exactly when the
// hold reaches HOLD_TO_STOP_DURATION_MS - releasing early cancels the in-flight animation job and
// snaps the ring back to empty, so a short tap never leaves the button stuck mid-progress.
@Composable
internal fun HoldToStopButton(
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val label = if (progress.value > 0f) "Tiếp tục giữ…" else "Nhấn giữ để dừng quay"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                modifier
                    .size(84.dp)
                    .testTag(HOLD_TO_STOP_BUTTON_TAG)
                    .pointerInput(onHoldComplete) {
                        awaitEachGesture {
                            awaitFirstDown()
                            val job =
                                scope.launch {
                                    progress.animateTo(
                                        1f,
                                        animationSpec = tween(HOLD_TO_STOP_DURATION_MS.toInt(), easing = LinearEasing),
                                    )
                                    onHoldComplete()
                                }
                            waitForUpOrCancellation()
                            job.cancel()
                            scope.launch { progress.snapTo(0f) }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = Danger600,
                strokeWidth = 4.dp,
                trackColor = Danger600.copy(alpha = 0.25f),
            )
            val innerCornerRadius by
                animateDpAsState(targetValue = if (progress.value > 0f) 8.dp else 32.dp, label = "stopShape")
            Box(modifier = Modifier.size(56.dp).background(Danger600, RoundedCornerShape(innerCornerRadius)))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun CaptureHudRow(
    gpsAccuracyMeters: Float?,
    latestLuxValue: Float?,
    freeStorageBytes: Long?,
    gpsSignalLost: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        HudChip(
            text = if (gpsSignalLost) "Mất GPS" else gpsAccuracyLabel(gpsAccuracyMeters),
            textColor = if (gpsSignalLost) Amber500 else Color.White,
        )
        HudChip(text = latestLuxValue?.let { "%.1f lux".format(it) } ?: "Chưa có dữ liệu lux")
        HudChip(text = freeStorageBytes?.let { "${it / 1_000_000} MB trống" } ?: "— MB trống")
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            imageVector = Icons.Filled.Sensors,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = device.name ?: device.address,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

// Tap-to-start counterpart to HoldToStopButton below, same 84dp ring + filled center motif so the
// two opposite actions (start/stop) read as the same control language. Dimmed and disabled while
// GPS accuracy has not yet held steady long enough (gpsReadyToRecord), same gate PrimaryButton's
// `enabled` used to show before this replaced it.
@Composable
private fun StartRecordingButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = if (enabled) Color.White else Color.White.copy(alpha = 0.3f)
    Box(
        modifier =
            modifier
                .size(84.dp)
                .border(4.dp, ringColor, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(60.dp)
                    .background(if (enabled) Danger600 else Danger600.copy(alpha = 0.4f), CircleShape),
        )
    }
}
