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
import kotlinx.coroutines.currentCoroutineContext
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

// connect()/disconnect() are separate from `samples` (review feedback) — Bước 0 calls connect()
// once a device is picked from LuxDeviceScanner; `samples` just keeps emitting for as long as the
// client is connected, and auto-reconnects on an unexpected drop without the caller doing anything.
//
// Uses classic Bluetooth SPP (BluetoothSocket over RFCOMM), not BLE GATT - a real-device check
// (2026-09-30) found the real lux sensor (LuxMap_ESP32) is a classic Bluetooth SPP device, not
// BLE, confirmed by its "(SPP)" profile tag in the phone's own bonded-device list and by the
// plain text line protocol it sends (see LuxPacketCodec). See docs/contract-drift.md.
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

        // BluetoothSocket.connect() and reading its InputStream are both blocking calls, unlike
        // the GATT API this replaced (which was callback-driven) - this scope runs that blocking
        // work off the caller's thread for as long as the client is alive.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // connect()/disconnect() can be called from the caller's thread (likely main) while the
        // read loop coroutine is running concurrently and can trigger its own reconnect at the
        // end. Without a lock, disconnect() could read a stale socket and close the wrong object
        // while a concurrent auto-reconnect keeps a fresh connection alive unmanaged. Same pattern
        // as NdjsonLogWriter's lock: every read/write of socket, readJob, lastDeviceAddress and
        // userInitiatedDisconnect goes through this one lock. synchronized is a Java monitor and is
        // reentrant per thread, so connect() calling itself from the read loop's own auto-reconnect
        // tail (inside this same lock) does not deadlock.
        private val lock = Any()
        private var socket: BluetoothSocket? = null
        private var readJob: Job? = null
        private var lastDeviceAddress: String? = null
        private var userInitiatedDisconnect = false

        // Locally counted per connection - see LuxSample.seq for why this cannot detect a real
        // dropped reading.
        private var seq = 0

        fun connect(deviceAddress: String) {
            synchronized(lock) {
                userInitiatedDisconnect = false
                lastDeviceAddress = deviceAddress
                seq = 0
                _connectionState.value = BleConnectionState.Connecting
                readJob?.cancel()
                readJob = scope.launch { runConnection(deviceAddress) }
            }
        }

        fun disconnect() {
            synchronized(lock) {
                userInitiatedDisconnect = true
                readJob?.cancel()
                readJob = null
                closeSocketLocked()
                _connectionState.value = BleConnectionState.Disconnected
            }
        }

        private fun closeSocketLocked() {
            runCatching { socket?.close() }
            socket = null
        }

        private suspend fun runConnection(deviceAddress: String) {
            try {
                // BluetoothAdapter.getDefaultAdapter() is deprecated since API 31 and can return
                // null. BluetoothManager.adapter is the supported replacement and is always
                // available here (BluetoothManager is a plain system service, not a new library
                // dependency).
                val adapter = context.getSystemService(BluetoothManager::class.java).adapter
                // An active discovery scan competes for the radio and is a well-known cause of
                // BluetoothSocket.connect() failing or hanging on real devices.
                adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(deviceAddress)
                val rfcommSocket = device.createRfcommSocketToServiceRecord(LuxDeviceScanner.SPP_UUID)
                rfcommSocket.connect()
                synchronized(lock) { socket = rfcommSocket }
                _connectionState.value = BleConnectionState.Connected

                rfcommSocket.inputStream.bufferedReader().use { reader ->
                    while (currentCoroutineContext().isActive) {
                        // readLine() returns null when the far end closed the stream - an
                        // ordinary disconnect, not an error.
                        val line = reader.readLine() ?: break
                        val now = SystemClock.elapsedRealtimeNanos()
                        val sample = LuxPacketCodec.decode(line, now, seq) ?: continue
                        seq++
                        _samples.tryEmit(sample)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (connectionLost: IOException) {
                // Connection never established, or dropped mid-read - fall through to the
                // reconnect logic below either way.
            } finally {
                synchronized(lock) { closeSocketLocked() }
            }

            _connectionState.value = BleConnectionState.Disconnected
            synchronized(lock) {
                if (!userInitiatedDisconnect) {
                    // Auto-reconnect (spec §11 — a session must not stop on a BLE drop).
                    lastDeviceAddress?.let { connect(it) }
                }
            }
        }
    }
