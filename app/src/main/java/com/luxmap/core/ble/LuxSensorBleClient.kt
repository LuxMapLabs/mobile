package com.luxmap.core.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState

    data object Connecting : BleConnectionState

    data object Connected : BleConnectionState
}

// UUIDs are PROPOSALS pending confirmation with the firmware owner (spec §9) — see
// docs/contract-drift.md. Rename these two constants once real values are confirmed; nothing
// else in this class should need to change.
object LuxSensorBleContract {
    val SERVICE_UUID: UUID = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")
    val LUX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
}

@Singleton
class LuxSensorBleClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        val connectionState = _connectionState.asStateFlow()

        private var gatt: BluetoothGatt? = null

        // Connects, subscribes to notify on LUX_CHARACTERISTIC_UUID, and emits a LuxSample per
        // packet, stamped with SystemClock.elapsedRealtimeNanos() at the moment it is received
        // (spec §8 — the module's own clock, module_ms, never substitutes for the phone's).
        fun observeSamples(deviceAddress: String): Flow<LuxSample> =
            callbackFlow {
                val callback =
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            connectedGatt: BluetoothGatt,
                            status: Int,
                            newState: Int,
                        ) {
                            _connectionState.value =
                                if (newState == BluetoothGatt.STATE_CONNECTED) {
                                    BleConnectionState.Connected
                                } else {
                                    BleConnectionState.Disconnected
                                }
                        }

                        override fun onCharacteristicChanged(
                            connectedGatt: BluetoothGatt,
                            characteristic: android.bluetooth.BluetoothGattCharacteristic,
                        ) {
                            val now = android.os.SystemClock.elapsedRealtimeNanos()
                            trySend(LuxPacketCodec.decode(characteristic.value, now))
                        }
                    }

                _connectionState.value = BleConnectionState.Connecting
                val device = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)
                gatt = device.connectGatt(context, false, callback)

                awaitClose {
                    gatt?.disconnect()
                    gatt?.close()
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }
    }
