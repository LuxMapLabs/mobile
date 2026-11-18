package com.luxmap.feature.workorder.ui.completion

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxConfirmDialog
import java.io.File

@Composable
fun EvidenceCaptureRoute(
    onDone: () -> Unit,
    viewModel: EvidenceCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var launchError by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingPath?.let(::File)
            pendingPath = null
            if (success && file != null) {
                viewModel.onPhotoCaptured(file, onDone)
            } else {
                file?.delete()
            }
        }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        try {
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingPath = null
            launchError = "Không mở được camera."
        } catch (_: SecurityException) {
            pendingPath = null
            launchError = "Không có quyền dùng camera."
        }
    }

    val cameraPermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.CAMERA] == true) launchCamera() else launchError = "Cần quyền dùng camera."
        }
    val locationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.useCurrentLocation()
        }

    fun close() {
        if (state.busy) return
        viewModel.skipPhotosWithoutLocation()
        onDone()
    }

    BackHandler(enabled = true) { close() }
    Scaffold(topBar = { LuxChildTopBar(title = "Thêm ảnh", onBack = { close() }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val available = !state.busy && state.awaitingLocation == 0 && pendingPath == null
            OutlinedButton(
                onClick = {
                    launchError = null
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        launchCamera()
                    } else {
                        cameraPermissions.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                enabled = available,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Chụp ảnh") }
            if (state.busy) {
                CircularProgressIndicator()
                Text("Đang chuẩn bị ảnh…")
            }
            launchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.added > 0) Text("Đã thêm ${state.added} ảnh.")
            if (state.failed > 0) Text("Không thêm được ${state.failed} ảnh.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { close() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Xong") }
        }
    }

    if (state.awaitingLocation > 0 && !state.busy) {
        LuxConfirmDialog(
            title = "Ảnh thiếu vị trí",
            message =
                if (state.locationUnavailable) {
                    "Chưa lấy được vị trí hiện tại. Bật định vị hoặc bỏ qua các ảnh này."
                } else {
                    "Chưa lấy được vị trí cho ảnh vừa chụp. Bật định vị rồi thử lấy lại vị trí hiện tại."
                },
            dismissLabel = "Bỏ qua ảnh này",
            confirmLabel = "Dùng vị trí hiện tại",
            onDismiss = viewModel::skipPhotosWithoutLocation,
            onConfirm = {
                val granted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                if (granted == PackageManager.PERMISSION_GRANTED) {
                    viewModel.useCurrentLocation()
                } else {
                    locationPermission.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
            },
        )
    }
}
