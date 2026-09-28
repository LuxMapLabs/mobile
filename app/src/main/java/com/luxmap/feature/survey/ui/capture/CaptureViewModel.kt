package com.luxmap.feature.survey.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.location.GpsSignalState
import com.luxmap.feature.survey.capture.PackageResult
import com.luxmap.feature.survey.capture.SurveyCaptureController
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val captureController: SurveyCaptureController,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.AwaitingBleConnection)
        val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

        private var sessionId: String = ""

        init {
            // Bước 0 (spec) needs someone to call connect() once a device is picked. Nothing else
            // in the app did this (SurveyCaptureService only connects when a session actually
            // starts), so the screen would sit on AwaitingBleConnection forever with no way to
            // reach Ready. The ViewModel is the natural owner: it is created when the user enters
            // this screen, matching "connect once the screen is entered".
            luxClient.connect(KNOWN_LUX_DEVICE_ADDRESS)

            viewModelScope.launch {
                luxClient.connectionState.collect { state ->
                    when (val current = _uiState.value) {
                        // Only advances AwaitingBleConnection -> Ready.
                        is CaptureUiState.AwaitingBleConnection ->
                            if (state == BleConnectionState.Connected) _uiState.value = CaptureUiState.Ready

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
        }

        fun onStartRecording(
            surveySweepId: String,
            luxDeviceAddress: String,
        ) {
            if (_uiState.value != CaptureUiState.Ready) return
            sessionId = UUID.randomUUID().toString()
            captureController.startSession(sessionId, surveySweepId, luxDeviceAddress)
            _uiState.value = CaptureUiState.Recording()
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

        companion object {
            // BLE lux device address hardcoded pending a scan/pairing UI (LuxDeviceScanner, Task
            // 17c, is not wired into this screen yet) — tracked in docs/contract-drift.md. Lives
            // here (not CaptureScreen) because the ViewModel is the one that must call connect().
            const val KNOWN_LUX_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        }
    }
