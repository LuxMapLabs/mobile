package com.luxmap.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState

    data object Connecting : BleConnectionState

    data object Connected : BleConnectionState
}

@SuppressLint("MissingPermission")
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

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val lock = Any()
        private val lineBuffer = LuxLineBuffer()
        private var gatt: BluetoothGatt? = null
        private var targetAddress: String? = null
        private var reconnectJob: Job? = null

        fun connect(deviceAddress: String) {
            synchronized(lock) {
                reconnectJob?.cancel()
                closeGattLocked()
                targetAddress = deviceAddress
                openGattLocked(deviceAddress)
            }
        }

        fun disconnect() {
            synchronized(lock) {
                targetAddress = null
                reconnectJob?.cancel()
                reconnectJob = null
                closeGattLocked()
                _connectionState.value = BleConnectionState.Disconnected
            }
        }

        private fun openGattLocked(deviceAddress: String) {
            _connectionState.value = BleConnectionState.Connecting
            lineBuffer.clear()
            gatt =
                try {
                    val adapter = context.getSystemService(BluetoothManager::class.java).adapter
                    adapter.getRemoteDevice(deviceAddress)
                        .connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            if (gatt == null) scheduleReconnectLocked()
        }

        private fun closeGattLocked() {
            runCatching {
                gatt?.disconnect()
                gatt?.close()
            }
            gatt = null
        }

        private fun scheduleReconnectLocked() {
            _connectionState.value = BleConnectionState.Disconnected
            val address = targetAddress ?: return
            reconnectJob?.cancel()
            reconnectJob =
                scope.launch {
                    delay(RECONNECT_DELAY_MS)
                    synchronized(lock) {
                        if (targetAddress == address) openGattLocked(address)
                    }
                }
        }

        private fun onLinkLost(lostGatt: BluetoothGatt) {
            synchronized(lock) {
                if (lostGatt != gatt) return
                closeGattLocked()
                scheduleReconnectLocked()
            }
        }

        private fun onChunk(value: ByteArray) {
            val now = SystemClock.elapsedRealtimeNanos()
            val lines = synchronized(lock) { lineBuffer.append(value.toString(Charsets.UTF_8)) }
            lines.forEach { line -> LuxLineParser.parse(line, now)?.let { _samples.tryEmit(it) } }
        }

        private val callback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int,
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                        if (!gatt.discoverServices()) onLinkLost(gatt)
                    } else {
                        onLinkLost(gatt)
                    }
                }

                override fun onServicesDiscovered(
                    gatt: BluetoothGatt,
                    status: Int,
                ) {
                    val tx = gatt.getService(NUS_SERVICE)?.getCharacteristic(NUS_TX)
                    val cccd = tx?.getDescriptor(CCCD)
                    if (status != BluetoothGatt.GATT_SUCCESS || tx == null || cccd == null) {
                        onLinkLost(gatt)
                        return
                    }
                    gatt.setCharacteristicNotification(tx, true)
                    val written =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                                BluetoothGatt.GATT_SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(cccd)
                        }
                    if (!written) onLinkLost(gatt)
                }

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int,
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        _connectionState.value = BleConnectionState.Connected
                    } else {
                        onLinkLost(gatt)
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    if (characteristic.uuid == NUS_TX) onChunk(value)
                }

                @Deprecated("Used below API 33")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic.uuid == NUS_TX) {
                        @Suppress("DEPRECATION")
                        onChunk(characteristic.value ?: return)
                    }
                }
            }

        companion object {
            val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
            private val NUS_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
            private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            private const val RECONNECT_DELAY_MS = 2_000L
        }
    }
