package com.luxmap.core.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
// user picks from. Shows every nearby device, not filtered by service - the Field Engineer can
// tell their own sensor apart by its advertised name.
//
// Uses classic Bluetooth discovery (BluetoothAdapter.startDiscovery()), not BLE scanning
// (BluetoothLeScanner) - a real-device check (2026-09-30) found the actual lux sensor
// (LuxMap_ESP32) connects over classic Bluetooth SPP, not BLE GATT: Android's own Bluetooth
// settings screen and a laptop both found it by name, but a BLE-only scan (tried with several
// ScanSettings variations - LOW_LATENCY, non-legacy/extended PHY) never did, because it was
// never advertising over BLE at all. See docs/contract-drift.md.
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
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            receivedContext: Context,
                            intent: Intent,
                        ) {
                            if (intent.action != BluetoothDevice.ACTION_FOUND) return
                            @Suppress("DEPRECATION")
                            val device =
                                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                    ?: return
                            trySend(LuxDevice(device.name, device.address))
                        }
                    }
                // ContextCompat.registerReceiver, not Context.registerReceiver directly: targetSdk
                // 35 requires RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED to be picked explicitly since
                // API 33, and this compiles the same way back to minSdk 26. ACTION_FOUND is a
                // system broadcast the Bluetooth stack sends to any listener, not just this app, so
                // RECEIVER_EXPORTED is correct here (RECEIVER_NOT_EXPORTED would silently drop it).
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(BluetoothDevice.ACTION_FOUND),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                // startDiscovery() refuses to run a second scan while one is already active - a
                // stale discovery from a previous screen visit that never got cancelled would make
                // every later scan on this screen silently fail to start.
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                adapter.startDiscovery()
                // timeoutMs used to be accepted and ignored, so a scan never stopped on its own.
                // Closing the flow after the window lets a collector tell "still scanning" apart
                // from "scan finished, here is everything found." Discovery itself already has a
                // platform timeout (~12s) but this keeps the app's own window authoritative.
                val timeoutJob =
                    launch {
                        delay(timeoutMs)
                        close()
                    }
                awaitClose {
                    timeoutJob.cancel()
                    adapter.cancelDiscovery()
                    context.unregisterReceiver(receiver)
                }
            }.distinctUntilChanged()

        companion object {
            // Well-known, standard Serial Port Profile UUID - not a placeholder, this exact value
            // is fixed across the whole Bluetooth Classic spec for SPP and is reused by
            // LuxSensorBleClient's RFCOMM connection.
            val SPP_UUID: java.util.UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        }
    }
