package com.luxmap.feature.profile.ui.luxmeter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.ble.LuxDevicePreferences
import com.luxmap.core.ble.LuxDeviceScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LuxMeterPairingViewModel
    @Inject
    constructor(
        private val preferences: LuxDevicePreferences,
        private val scanner: LuxDeviceScanner,
    ) : ViewModel() {
        private val sheet = MutableStateFlow(LuxMeterPairingUiState())
        private var scanJob: Job? = null

        val uiState: StateFlow<LuxMeterPairingUiState> =
            combine(preferences.pairedDevice, sheet) { paired, current -> current.copy(pairedDevice = paired) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LuxMeterPairingUiState())

        fun openSheet(hasPermission: Boolean) {
            sheet.update { it.copy(isSheetOpen = true, hasPermission = hasPermission) }
            refresh()
        }

        fun dismissSheet() {
            stopScan()
            sheet.update { LuxMeterPairingUiState() }
        }

        fun onPermissionResult(granted: Boolean) {
            sheet.update { it.copy(hasPermission = granted) }
            refresh()
        }

        fun refresh() {
            if (!sheet.value.hasPermission) return
            val bluetoothOn = runCatching { scanner.isBluetoothEnabled() }.getOrDefault(false)
            sheet.update { it.copy(isBluetoothOn = bluetoothOn) }
            if (bluetoothOn) scan()
        }

        fun scan() {
            val current = sheet.value
            if (!current.hasPermission || !current.isBluetoothOn || current.isScanning) return
            sheet.update { it.copy(isScanning = true, foundDevices = emptyList()) }
            scanJob =
                viewModelScope.launch {
                    scanner.scan()
                        .catch { }
                        .onCompletion { sheet.update { it.copy(isScanning = false) } }
                        .collect { device ->
                            sheet.update { state ->
                                if (state.foundDevices.any { it.address == device.address }) {
                                    state
                                } else {
                                    state.copy(foundDevices = state.foundDevices + device)
                                }
                            }
                        }
                }
        }

        fun select(device: LuxDevice) {
            stopScan()
            viewModelScope.launch {
                preferences.save(device)
                sheet.update { LuxMeterPairingUiState() }
            }
        }

        fun unpair() {
            viewModelScope.launch { preferences.clear() }
        }

        private fun stopScan() {
            scanJob?.cancel()
            scanJob = null
            sheet.update { it.copy(isScanning = false) }
        }
    }
