package com.luxmap.feature.workorder.ui.completion

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.PrimaryButton
import java.io.File

@Composable
fun EvidenceCaptureRoute(
    onDone: () -> Unit,
    viewModel: EvidenceCaptureViewModel = hiltViewModel(),
    autoStart: Boolean = true,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var launchError by rememberSaveable { mutableStateOf<String?>(null) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }

    fun close() {
        if (state.busy) return
        viewModel.skipPhotosWithoutLocation()
        onDone()
    }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingPath?.let(::File)
            pendingPath = null
            if (success && file != null) {
                viewModel.onPhotoCaptured(file, onDone)
            } else {
                file?.delete()
                close()
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

    fun startCapture() {
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
    }

    LaunchedEffect(Unit) {
        if (autoStart && !autoStarted) {
            autoStarted = true
            startCapture()
        }
    }

    val showControls =
        !autoStart || launchError != null || state.failed > 0 || state.awaitingLocation > 0 || state.added > 0
    BackHandler(enabled = true) { close() }
    Scaffold(
        topBar = { LuxChildTopBar(title = "Thêm ảnh", onBack = { close() }) },
        bottomBar = {
            if (showControls) {
                BottomActionBar(ctaText = "Xong", onCtaClick = { close() }, ctaEnabled = !state.busy)
            }
        },
    ) { padding ->
        if (!showControls) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
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
            LuxCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = "Chụp ảnh bằng chứng. Vị trí hiện tại được gắn vào từng ảnh.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryButton(
                        text = if (state.busy) "Đang chuẩn bị ảnh…" else "Chụp ảnh",
                        onClick = ::startCapture,
                        enabled = available,
                        isLoading = state.busy,
                        branded = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            launchError?.let { LuxBanner(message = it, tone = LuxBannerTone.ERROR) }
            if (state.added > 0) LuxBanner(message = "Đã thêm ${state.added} ảnh.", tone = LuxBannerTone.SUCCESS)
            if (state.failed > 0) {
                LuxBanner(message = "Không thêm được ${state.failed} ảnh.", tone = LuxBannerTone.ERROR)
            }
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
