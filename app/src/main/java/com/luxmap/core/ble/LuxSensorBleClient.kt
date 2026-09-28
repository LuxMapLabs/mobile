package com.luxmap.core.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

// connect()/disconnect() are separate from `samples` (review feedback) — Bước 0 calls connect()
// once a device is picked from LuxDeviceScanner; `samples` just keeps emitting for as long as the
// client is connected, and auto-reconnects on an unexpected drop without the caller doing anything.
@Singleton
class LuxSensorBleClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

        private val _samples = MutableSharedFlow<LuxSample>(extraBufferCapacity = 64)
        val samples: SharedFlow<LuxSample> = _samples.asSharedFlow()

        // connect()/disconnect() can run on the caller's thread (Task 17d: SurveyCaptureService,
        // likely main thread) at the same time onConnectionStateChange runs on a Binder callback
        // thread and auto-reconnects. Without a lock, disconnect() could read a stale gatt and
        // close the wrong object while a concurrent auto-reconnect keeps a fresh connection alive
        // unmanaged. Same pattern as NdjsonLogWriter's lock: every read/write of gatt,
        // lastDeviceAddress and userInitiatedDisconnect goes through this one lock, so the
        // check-then-act sequence (check userInitiatedDisconnect, then connect()) is atomic, not
        // just individually volatile fields. synchronized is a Java monitor and is reentrant per
        // thread, so connect() calling itself from inside a synchronized block (the auto-reconnect
        // branch below) does not deadlock.
        private val lock = Any()
        private var gatt: BluetoothGatt? = null
        private var lastDeviceAddress: String? = null
        private var userInitiatedDisconnect = false

        fun connect(deviceAddress: String) {
            synchronized(lock) {
                userInitiatedDisconnect = false
                lastDeviceAddress = deviceAddress
                _connectionState.value = BleConnectionState.Connecting
                // BluetoothAdapter.getDefaultAdapter() is deprecated since API 31 and can return null.
                // BluetoothManager.adapter is the supported replacement and is always available here
                // (BluetoothManager is a plain system service, not a new library dependency).
                val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
                val device = bluetoothManager.adapter.getRemoteDevice(deviceAddress)
                gatt = device.connectGatt(context, false, gattCallback)
            }
        }

        fun disconnect() {
            synchronized(lock) {
                userInitiatedDisconnect = true
                gatt?.disconnect()
                gatt?.close()
                gatt = null
                _connectionState.value = BleConnectionState.Disconnected
            }
        }

        private val gattCallback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    connectedGatt: BluetoothGatt,
                    status: Int,
                    newState: Int,
                ) {
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        connectedGatt.discoverServices()
                    } else {
                        _connectionState.value = BleConnectionState.Disconnected
                        synchronized(lock) {
                            if (!userInitiatedDisconnect) {
                                // Close the old GATT client before reconnecting. connectGatt() always
                                // registers a new GATT client with the OS, and the platform has a small
                                // hard cap on how many can be open at once (historically ~30). Without
                                // this close(), repeated auto-reconnects (e.g. the sensor module losing
                                // power over and over in the field) would leak one client slot per
                                // reconnect and eventually make every further connect() attempt fail.
                                connectedGatt.close()
                                // Auto-reconnect (spec §11 — a session must not stop on a BLE drop).
                                lastDeviceAddress?.let { connect(it) }
                            }
                        }
                    }
                }

                override fun onServicesDiscovered(
                    connectedGatt: BluetoothGatt,
                    status: Int,
                ) {
                    val characteristic =
                        connectedGatt
                            .getService(LuxSensorBleContract.SERVICE_UUID)
                            ?.getCharacteristic(LuxSensorBleContract.LUX_CHARACTERISTIC_UUID)
                            ?: return
                    connectedGatt.setCharacteristicNotification(characteristic, true)
                    // setCharacteristicNotification() only flips a local flag — the peripheral is
                    // not told to start sending until the CCCD descriptor is written (a common BLE
                    // gotcha this review feedback specifically called out).
                    val descriptor =
                        characteristic.getDescriptor(LuxSensorBleContract.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.let {
                        @Suppress("DEPRECATION")
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        connectedGatt.writeDescriptor(it)
                    }
                    _connectionState.value = BleConnectionState.Connected
                }

                // Pre-API-33 callback — still invoked on those devices; characteristic.value holds the payload.
                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(
                    connectedGatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    emitSample(characteristic.value)
                }

                // API 33+ callback — the value is passed directly instead of read from the characteristic.
                override fun onCharacteristicChanged(
                    connectedGatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    emitSample(value)
                }
            }

        private fun emitSample(bytes: ByteArray) {
            val now = SystemClock.elapsedRealtimeNanos()
            _samples.tryEmit(LuxPacketCodec.decode(bytes, now))
        }
    }
