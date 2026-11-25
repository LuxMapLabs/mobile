package com.luxmap.feature.survey.ui.polephoto.lux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.ble.LuxDevicePreferences
import com.luxmap.core.ble.LuxDeviceScanner
import com.luxmap.core.ble.LuxSamplingWindow
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.ble.LuxWindowState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class LuxCardViewModel
    @Inject
    constructor(
        private val client: LuxSensorBleClient,
        private val preferences: LuxDevicePreferences,
        private val scanner: LuxDeviceScanner,
    ) : ViewModel() {
        private data class Flags(
            val expanded: Boolean = false,
            val hasPermission: Boolean = false,
            val bluetoothOn: Boolean = true,
            val recorded: LuxCardUiState.Recorded? = null,
            val connectRequested: Boolean = false,
            val failedAttempts: Int = 0,
            val moduleId: String? = null,
        )

        private val window = LuxSamplingWindow(size = REQUIRED_SAMPLES)
        private val flags = MutableStateFlow(Flags())
        private val windowState = MutableStateFlow(window.state())

        val uiState: StateFlow<LuxCardUiState> =
            combine(flags, preferences.pairedDevice, client.connectionState, windowState, ::toUiState)
                .stateIn(viewModelScope, SharingStarted.Eagerly, LuxCardUiState.Collapsed)

        val recordedReading: LuxCardUiState.Recorded?
            get() = flags.value.recorded

        init {
            viewModelScope.launch {
                client.samples.collect { sample ->
                    val current = flags.value
                    if (current.expanded && current.recorded == null) windowState.value = window.add(sample.lux)
                }
            }
            viewModelScope.launch {
                var previous: BleConnectionState = client.connectionState.value
                client.connectionState.collect { state ->
                    if (state != BleConnectionState.Connected) windowState.value = window.reset()
                    when {
                        state == BleConnectionState.Connected -> flags.update { it.copy(failedAttempts = 0) }
                        previous == BleConnectionState.Connecting && state == BleConnectionState.Disconnected &&
                            flags.value.connectRequested ->
                            flags.update { it.copy(failedAttempts = it.failedAttempts + 1) }
                    }
                    previous = state
                }
            }
            viewModelScope.launch {
                preferences.pairedDevice.collect { device -> if (device != null) connectIfReady() }
            }
        }

        fun expand(hasPermission: Boolean) {
            flags.update { it.copy(expanded = true, hasPermission = hasPermission) }
            connectIfReady()
        }

        fun collapse() {
            client.disconnect()
            windowState.value = window.reset()
            flags.update { Flags() }
        }

        fun onPermissionResult(granted: Boolean) {
            flags.update { it.copy(hasPermission = granted) }
            connectIfReady()
        }

        fun retryConnect() {
            flags.update { it.copy(failedAttempts = 0) }
            connectIfReady()
        }

        fun record() {
            val window = windowState.value
            val stableLux = window.stableLux ?: return
            client.disconnect()
            flags.update {
                it.copy(
                    recorded = LuxCardUiState.Recorded(stableLux, Instant.now(), window.samples, it.moduleId),
                    connectRequested = false,
                )
            }
        }

        fun remeasure() {
            windowState.value = window.reset()
            flags.update { it.copy(recorded = null) }
            connectIfReady()
        }

        private fun connectIfReady() {
            viewModelScope.launch {
                val current = flags.value
                if (!current.expanded || current.recorded != null || !current.hasPermission) return@launch
                val bluetoothOn = runCatching { scanner.isBluetoothEnabled() }.getOrDefault(false)
                flags.update { it.copy(bluetoothOn = bluetoothOn) }
                val device = preferences.pairedDevice.first() ?: return@launch
                if (bluetoothOn) {
                    windowState.value = window.reset()
                    flags.update { it.copy(connectRequested = true, moduleId = device.address) }
                    client.connect(device.address)
                }
            }
        }

        override fun onCleared() {
            client.disconnect()
        }

        private fun toUiState(
            current: Flags,
            paired: LuxDevice?,
            connection: BleConnectionState,
            samples: LuxWindowState,
        ): LuxCardUiState =
            when {
                !current.expanded -> LuxCardUiState.Collapsed
                current.recorded != null -> current.recorded
                paired == null -> LuxCardUiState.NotPaired
                !current.hasPermission -> LuxCardUiState.PermissionMissing
                !current.bluetoothOn -> LuxCardUiState.BluetoothOff
                connection == BleConnectionState.Connected ->
                    LuxCardUiState.Live(
                        latestLux = samples.latestLux,
                        sampleCount = samples.sampleCount,
                        requiredSamples = REQUIRED_SAMPLES,
                        stableLux = samples.stableLux,
                    )
                current.failedAttempts >= UNREACHABLE_AFTER_FAILURES -> LuxCardUiState.Unreachable
                connection == BleConnectionState.Connecting -> LuxCardUiState.Connecting
                current.connectRequested -> LuxCardUiState.Disconnected
                else -> LuxCardUiState.Connecting
            }

        private companion object {
            const val REQUIRED_SAMPLES = 12
            const val UNREACHABLE_AFTER_FAILURES = 2
        }
    }
