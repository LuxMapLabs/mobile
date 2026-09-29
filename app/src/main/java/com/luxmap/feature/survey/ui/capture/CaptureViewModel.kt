package com.luxmap.feature.survey.ui.capture

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.ble.LuxDevicePreferences
import com.luxmap.core.ble.LuxDeviceScanner
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.location.GpsSignalState
import com.luxmap.feature.survey.capture.PackageResult
import com.luxmap.feature.survey.capture.RecordingStartResult
import com.luxmap.feature.survey.capture.SurveyCaptureController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel
    @Inject
    constructor(
        private val luxClient: LuxSensorBleClient,
        private val luxDeviceScanner: LuxDeviceScanner,
        private val luxDevicePreferences: LuxDevicePreferences,
        private val captureController: SurveyCaptureController,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Scanning())
        val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

        private var sessionId: String = ""

        // The device connect() was last called for - used to save it to LuxDevicePreferences once
        // the connectionState collector below observes Connected, and to show its name while
        // Connecting. Null while scanning or once a recording starts (nothing new to save then).
        private var pendingDevice: LuxDevice? = null
        private var scanJob: Job? = null

        // The surveySweepId passed to onStartRecording, held until the preview surface is ready
        // and startSession() can actually be called (Cach A - see the plan this came from).
        private var pendingSurveySweepId: String = ""

        // onPreviewSurfaceReady can legitimately fire twice in one recording: once to start the
        // session (StartingRecording), and again later if the TextureView's SurfaceTexture is
        // destroyed and recreated (an ordinary Activity stop, not only rotation - see
        // onPreviewSurfaceLost). This guards only the StartingRecording branch against firing a
        // SECOND time before the first call has resolved to Recording.
        private var startingSessionRequested = false

        init {
            viewModelScope.launch {
                val remembered = luxDevicePreferences.lastDevice()
                if (remembered != null) {
                    connectTo(remembered)
                } else {
                    startScan()
                }
            }

            viewModelScope.launch {
                luxClient.connectionState.collect { state ->
                    when (val current = _uiState.value) {
                        // Only advances Connecting -> Ready.
                        is CaptureUiState.Connecting ->
                            if (state == BleConnectionState.Connected) {
                                pendingDevice?.let { luxDevicePreferences.saveLastDevice(it) }
                                _uiState.value = CaptureUiState.Ready
                            }

                        // Does not regress out of Recording on a mid-session drop (spec §11) — just
                        // raises the warning flag on the existing Recording state.
                        is CaptureUiState.Recording ->
                            _uiState.value = current.copy(bleGapDetected = state == BleConnectionState.Disconnected)

                        else -> Unit
                    }
                }
            }
            viewModelScope.launch {
                captureController.gpsSignalState.collect { state ->
                    val current = _uiState.value
                    if (current is CaptureUiState.Recording) {
                        _uiState.value = current.copy(gpsSignalLost = state == GpsSignalState.Lost)
                    }
                }
            }
            viewModelScope.launch {
                captureController.recordingStartResult.collect { result ->
                    if (_uiState.value !is CaptureUiState.StartingRecording) return@collect
                    when (result) {
                        // Seed the warning flags from what is already known right now instead of
                        // always starting at false - a GPS or BLE issue that happened while the
                        // camera was still opening must not be silently dropped the instant the
                        // screen reaches Recording.
                        RecordingStartResult.Ready ->
                            _uiState.value =
                                CaptureUiState.Recording(
                                    gpsSignalLost = captureController.gpsSignalState.value == GpsSignalState.Lost,
                                    bleGapDetected = luxClient.connectionState.value == BleConnectionState.Disconnected,
                                )

                        is RecordingStartResult.Failed -> _uiState.value = CaptureUiState.PackagingFailed(result.reason)
                        null -> Unit
                    }
                }
            }
        }

        fun onDeviceSelected(device: LuxDevice) {
            if (_uiState.value !is CaptureUiState.Scanning) return
            scanJob?.cancel()
            connectTo(device)
        }

        // Available from Connecting/Scanning/ScanTimedOut/Ready - lets the user back out of a
        // remembered device that is not answering, or pick a different sensor than the one saved
        // from a past session. Not available during Recording: the foreground service owns the
        // connection then, and CaptureScreen's BackHandler already keeps the user on that screen.
        fun onChangeDevice() {
            if (_uiState.value is CaptureUiState.Recording) return
            scanJob?.cancel()
            pendingDevice = null
            luxClient.disconnect()
            startScan()
        }

        fun onRetryScan() {
            if (_uiState.value !is CaptureUiState.ScanTimedOut) return
            startScan()
        }

        fun onStartRecording(surveySweepId: String) {
            if (_uiState.value != CaptureUiState.Ready) return
            pendingSurveySweepId = surveySweepId
            _uiState.value = CaptureUiState.StartingRecording
        }

        fun onPreviewSurfaceReady(surface: Surface) {
            when (_uiState.value) {
                is CaptureUiState.StartingRecording -> {
                    if (startingSessionRequested) return
                    // pendingDevice is the device the connectionState collector already confirmed
                    // Connected to reach Ready - it cannot be null here, but a session cannot start
                    // without an address either way, so this is checked rather than assumed with !!.
                    val luxDeviceAddress = pendingDevice?.address ?: return
                    startingSessionRequested = true
                    sessionId = UUID.randomUUID().toString()
                    captureController.startSession(sessionId, pendingSurveySweepId, luxDeviceAddress, surface)
                }

                // The screen came back after an ordinary Activity stop and the TextureView built a
                // new SurfaceTexture. Hand the new Surface to the running session so the preview
                // shows again; the recording never stopped.
                is CaptureUiState.Recording -> captureController.updatePreviewSurface(surface)
                else -> Unit
            }
        }

        // The camera keeps recording through this - only the preview output is detached inside
        // VideoCaptureSession, the encoder is never touched. Does nothing during StartingRecording:
        // the camera has not opened yet, so there is no live preview target to drop.
        //
        // Packaging counts too, not only Recording: the camera session is still being torn down
        // there (VideoCaptureSession.stop() calls stopRepeating() and then waits for the encoder
        // tail), which is the same reason CaptureScreen keeps the preview mounted through
        // Packaging. A Surface dropped in that window still needs detaching.
        fun onPreviewSurfaceLost() {
            val state = _uiState.value
            if (state is CaptureUiState.Recording || state is CaptureUiState.Packaging) {
                captureController.updatePreviewSurface(null)
            }
        }

        fun onStopRecording() {
            if (_uiState.value !is CaptureUiState.Recording) return
            _uiState.value = CaptureUiState.Packaging
            viewModelScope.launch {
                when (val result = captureController.stopSession().first()) {
                    is PackageResult.Success -> _uiState.value = CaptureUiState.Packaged(sessionId)
                    is PackageResult.Failure -> _uiState.value = CaptureUiState.PackagingFailed(result.reason)
                }
            }
        }

        // Available from PackagingFailed for all 3 ways this screen can reach it (the camera
        // never started, a real recording's packaging step failed, or the service reported a
        // mid-session failure on its own) - guarantees ACTION_STOP reaches the service even when
        // the camera never started and onStopRecording() (Recording-only) was never reachable.
        // Without it the foreground service is left running for the rest of the process's life.
        // Safe to call more than once: SurveyCaptureService's ACTION_STOP handling is idempotent.
        fun onDismissFailure() {
            if (_uiState.value !is CaptureUiState.PackagingFailed) return
            viewModelScope.launch { captureController.stopSession().first() }
        }

        private fun connectTo(device: LuxDevice) {
            pendingDevice = device
            _uiState.value = CaptureUiState.Connecting(device.name)
            luxClient.connect(device.address)
        }

        private fun startScan() {
            scanJob?.cancel()
            _uiState.value = CaptureUiState.Scanning()
            scanJob =
                viewModelScope.launch {
                    val found = mutableListOf<LuxDevice>()
                    luxDeviceScanner.scan().collect { device ->
                        if (found.none { it.address == device.address }) {
                            found += device
                            _uiState.value = CaptureUiState.Scanning(found.toList())
                        }
                    }
                    // The scan window closed with nothing found at all - a device that showed up
                    // and is just waiting to be tapped is left as-is, not turned into a timeout.
                    if (found.isEmpty() && _uiState.value is CaptureUiState.Scanning) {
                        _uiState.value = CaptureUiState.ScanTimedOut
                    }
                }
        }

        // Leaving the screen before a recording ever started (Connecting/Scanning/ScanTimedOut/
        // Ready/Packaging/Packaged/PackagingFailed) should give the GATT connection back, same
        // reasoning as the BackHandler in CaptureScreen. Recording is the one exception: the
        // foreground service owns the connection at that point (it keeps running after the screen
        // is gone), so disconnecting here would cut off a live recording out from under it.
        // scanJob and the collectors above are cancelled automatically with viewModelScope.
        override fun onCleared() {
            if (_uiState.value !is CaptureUiState.Recording) {
                luxClient.disconnect()
            }
        }
    }
