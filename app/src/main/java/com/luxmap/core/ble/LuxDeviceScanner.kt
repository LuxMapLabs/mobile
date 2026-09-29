package com.luxmap.core.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LuxDevice(
    val name: String?,
    val address: String,
)

// Replaces Bước 0's old hardcoded device address (spec's own open point) with a real scan the
// user picks from. Shows every nearby BLE device, not just ones matching
// LuxSensorBleContract.SERVICE_UUID - the project chose this over a ScanFilter since the UUID is
// still an unconfirmed placeholder (see docs/contract-drift.md), and the Field Engineer can tell
// their own sensor apart by its advertised name.
class LuxDeviceScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun scan(timeoutMs: Long = 10_000L): Flow<LuxDevice> =
            callbackFlow {
                // Context.getSystemService(BluetoothAdapter::class.java) is not registered by the
                // platform and returns null — BluetoothManager is the real system service, and its
                // adapter property is the supported, non-deprecated way to reach BluetoothAdapter
                // (same fix as LuxSensorBleClient.connect(), applied here for the same reason).
                val adapter = context.getSystemService(BluetoothManager::class.java).adapter
                val scanner = adapter.bluetoothLeScanner
                val callback =
                    object : ScanCallback() {
                        override fun onScanResult(
                            callbackType: Int,
                            result: ScanResult,
                        ) {
                            trySend(LuxDevice(result.device.name, result.device.address))
                        }
                    }
                scanner.startScan(callback)
                // timeoutMs used to be accepted and ignored, so a scan never stopped on its own.
                // Closing the flow after the window lets a collector tell "still scanning" apart
                // from "scan finished, here is everything found."
                val timeoutJob =
                    launch {
                        delay(timeoutMs)
                        close()
                    }
                awaitClose {
                    timeoutJob.cancel()
                    scanner.stopScan(callback)
                }
            }.distinctUntilChanged()
    }
