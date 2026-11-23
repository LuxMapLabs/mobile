package com.luxmap.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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

@SuppressLint("MissingPermission")
class LuxDeviceScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun isBluetoothEnabled(): Boolean =
            context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true

        fun scan(timeoutMs: Long = 10_000L): Flow<LuxDevice> =
            callbackFlow {
                val scanner = context.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
                if (scanner == null) {
                    close()
                    return@callbackFlow
                }
                val callback =
                    object : ScanCallback() {
                        override fun onScanResult(
                            callbackType: Int,
                            result: ScanResult,
                        ) {
                            val name = result.scanRecord?.deviceName ?: result.device.name
                            if (!name.isNullOrBlank()) trySend(LuxDevice(name, result.device.address))
                        }

                        override fun onScanFailed(errorCode: Int) {
                            close()
                        }
                    }
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                scanner.startScan(null, settings, callback)
                val timeoutJob =
                    launch {
                        delay(timeoutMs)
                        close()
                    }
                awaitClose {
                    timeoutJob.cancel()
                    runCatching { scanner.stopScan(callback) }
                }
            }.distinctUntilChanged()
    }
