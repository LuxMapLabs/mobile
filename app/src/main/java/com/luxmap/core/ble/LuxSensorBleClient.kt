package com.luxmap.core.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState

    data object Connecting : BleConnectionState

    data object Connected : BleConnectionState
}

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
        private var socket: BluetoothSocket? = null
        private var connectionJob: Job? = null

        fun connect(deviceAddress: String) {
            synchronized(lock) {
                connectionJob?.cancel()
                closeSocketLocked()
                _connectionState.value = BleConnectionState.Connecting
                connectionJob = scope.launch { connectionLoop(deviceAddress) }
            }
        }

        fun disconnect() {
            synchronized(lock) {
                connectionJob?.cancel()
                connectionJob = null
                closeSocketLocked()
                _connectionState.value = BleConnectionState.Disconnected
            }
        }

        private fun closeSocketLocked() {
            runCatching { socket?.close() }
            socket = null
        }

        private suspend fun CoroutineScope.connectionLoop(deviceAddress: String) {
            while (isActive) {
                _connectionState.value = BleConnectionState.Connecting
                readUntilDisconnected(deviceAddress)
                if (!isActive) return
                _connectionState.value = BleConnectionState.Disconnected
                delay(RECONNECT_DELAY_MS)
            }
        }

        private suspend fun CoroutineScope.readUntilDisconnected(deviceAddress: String) {
            try {
                val adapter = context.getSystemService(BluetoothManager::class.java).adapter
                adapter.cancelDiscovery()
                val rfcommSocket =
                    adapter.getRemoteDevice(deviceAddress).createRfcommSocketToServiceRecord(LuxDeviceScanner.SPP_UUID)
                synchronized(lock) { socket = rfcommSocket }
                rfcommSocket.connect()
                _connectionState.value = BleConnectionState.Connected
                rfcommSocket.inputStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val sample = LuxLineParser.parse(line, SystemClock.elapsedRealtimeNanos()) ?: continue
                        _samples.tryEmit(sample)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IOException) {
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            } finally {
                synchronized(lock) { closeSocketLocked() }
            }
        }

        private companion object {
            const val RECONNECT_DELAY_MS = 2_000L
        }
    }
