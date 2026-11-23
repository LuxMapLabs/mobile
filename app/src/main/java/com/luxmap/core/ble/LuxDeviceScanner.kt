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
import java.util.UUID
import javax.inject.Inject

data class LuxDevice(
    val name: String?,
    val address: String,
)

class LuxDeviceScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun bondedDevices(): List<LuxDevice> =
            context.getSystemService(BluetoothManager::class.java).adapter
                .bondedDevices
                .orEmpty()
                .map { LuxDevice(it.name, it.address) }

        fun scan(timeoutMs: Long = 10_000L): Flow<LuxDevice> =
            callbackFlow {
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
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(BluetoothDevice.ACTION_FOUND),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                adapter.startDiscovery()
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
            val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        }
    }
