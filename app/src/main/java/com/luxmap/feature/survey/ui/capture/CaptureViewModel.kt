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
                        RecordingStartResult.Ready -> _uiState.value = CaptureUiState.Recording()
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
            if (_uiState.value !is CaptureUiState.StartingRecording) return
            // pendingDevice is the device the connectionState collector already confirmed
            // Connected to reach Ready - it cannot be null here, but a session cannot start
            // without an address either way, so this is checked rather than assumed with !!.
            val luxDeviceAddress = pendingDevice?.address ?: return
            sessionId = UUID.randomUUID().toString()
            captureController.startSession(sessionId, pendingSurveySweepId, luxDeviceAddress, surface)
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
