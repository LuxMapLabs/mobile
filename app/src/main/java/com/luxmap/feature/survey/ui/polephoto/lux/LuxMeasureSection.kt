package com.luxmap.feature.survey.ui.polephoto.lux

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.luxmap.feature.profile.ui.luxmeter.LuxMeterPairingSheet
import com.luxmap.feature.profile.ui.luxmeter.LuxMeterPairingViewModel
import com.luxmap.feature.profile.ui.luxmeter.hasLuxMeterPermissions
import com.luxmap.feature.profile.ui.luxmeter.luxMeterPermissions

@Composable
fun LuxMeasureSection(
    viewModel: LuxCardViewModel,
    pairingViewModel: LuxMeterPairingViewModel,
    isEnabled: Boolean,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val pairingState by pairingViewModel.uiState.collectAsState()
    val cardPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            viewModel.onPermissionResult(results.values.all { it })
        }
    val pairingPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            pairingViewModel.onPermissionResult(results.values.all { it })
        }
    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.retryConnect()
            pairingViewModel.refresh()
        }
    val enableBluetooth = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }

    LuxMeasureCard(
        state = state,
        isEnabled = isEnabled,
        onExpand = { viewModel.expand(hasPermission = context.hasLuxMeterPermissions()) },
        onCollapse = viewModel::collapse,
        onPair = { pairingViewModel.openSheet(hasPermission = context.hasLuxMeterPermissions()) },
        onRequestPermission = { cardPermissionLauncher.launch(luxMeterPermissions()) },
        onEnableBluetooth = enableBluetooth,
        onRetryConnect = viewModel::retryConnect,
        onRecord = viewModel::record,
        onRemeasure = viewModel::remeasure,
    )
    if (pairingState.isSheetOpen) {
        LuxMeterPairingSheet(
            state = pairingState,
            onDismiss = pairingViewModel::dismissSheet,
            onRequestPermission = { pairingPermissionLauncher.launch(luxMeterPermissions()) },
            onEnableBluetooth = enableBluetooth,
            onScan = pairingViewModel::scan,
            onSelect = { device ->
                pairingViewModel.select(device)
                viewModel.onPermissionResult(context.hasLuxMeterPermissions())
            },
            onUnpair = pairingViewModel::unpair,
        )
    }
}
