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
    }
