package com.luxmap.feature.profile.ui.luxmeter

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

fun luxMeterPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

fun Context.hasLuxMeterPermissions(): Boolean =
    luxMeterPermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun LuxMeterRoute(viewModel: LuxMeterPairingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            viewModel.onPermissionResult(results.values.all { it })
        }
    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refresh()
        }

    LuxMeterSection(
        pairedDevice = state.pairedDevice,
        onOpen = { viewModel.openSheet(hasPermission = context.hasLuxMeterPermissions()) },
    )
    if (state.isSheetOpen) {
        LuxMeterPairingSheet(
            state = state,
            onDismiss = viewModel::dismissSheet,
            onRequestPermission = { permissionLauncher.launch(luxMeterPermissions()) },
            onEnableBluetooth = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            onScan = viewModel::scan,
            onSelect = viewModel::select,
            onUnpair = viewModel::unpair,
        )
    }
}
