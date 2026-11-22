package com.luxmap.feature.workorder.polegps.ui.measure

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.LuxActionPair
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.workorder.polegps.data.CorridorCheck
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.ROUTE_CORRIDOR_METRES
import com.luxmap.feature.workorder.polegps.ui.corridorPreviewLabel
import com.luxmap.feature.workorder.polegps.ui.previousDistanceLabel
import java.io.File
import java.util.Locale

@Composable
fun PoleGpsMeasureRoute(
    onBack: () -> Unit,
    viewModel: PoleGpsMeasureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var previewPath by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingFile = pendingPath?.let(::File)
    val previewFile = previewPath?.let(::File)
    var hasLocationPermission by rememberSaveable { mutableStateOf(false) }
    val isSaving by viewModel.isSaving.collectAsState()
    val currentFix by viewModel.currentFix.collectAsState()
    val confirmation by viewModel.pendingConfirmation.collectAsState()
    val corridorWarning by viewModel.pendingCorridor.collectAsState()
    LocationWatchingEffect(hasLocationPermission, viewModel)
    val locationMissing by viewModel.locationMissing.collectAsState()
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                previewPath = file.absolutePath
            } else {
                onBack()
            }
        }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingPath = file.absolutePath
        if (hasLocationPermission) viewModel.startWatchingLocation()
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePictureLauncher.launch(uri)
        } catch (_: Exception) {
            pendingPath = null
            viewModel.onCameraFailure()
        }
    }

    // CAMERA is a hard requirement here - some OEM camera apps (confirmed on a real Samsung
    // device) deny the ACTION_IMAGE_CAPTURE intent with a SecurityException, crashing the whole
    // process, when the calling app does not hold this permission itself. ACCESS_FINE_LOCATION
    // is requested too; without a GPS fix the photo is held for a retry instead of saved.
    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (result[Manifest.permission.CAMERA] == true && hasLocationPermission) {
                viewModel.onPermissionGranted()
                launchCamera()
            } else {
                viewModel.onPermissionDenied()
            }
        }

    fun requestPermissionsAndStart() {
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        when {
            cameraGranted && hasLocationPermission -> {
                viewModel.onPermissionGranted()
                launchCamera()
            }
            hasRequestedPermissions -> viewModel.onPermissionDenied()
            else -> {
                hasRequestedPermissions = true
                permissionsLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            }
        }
    }

    fun retryPermissions() {
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (cameraGranted && hasLocationPermission) {
            viewModel.onPermissionGranted()
            launchCamera()
        } else {
            permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    val retryLocationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasLocationPermission = granted
            if (granted) viewModel.startWatchingLocation()
            previewFile?.let { viewModel.onPhotoCaptured(it, granted, onBack) }
        }

    fun retryLocation() {
        val file = previewFile ?: return
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) {
            viewModel.onPhotoCaptured(file, true, onBack)
        } else {
            retryLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun discardPhoto() {
        previewFile?.delete()
        onBack()
    }

    LaunchedEffect(Unit) {
        if (pendingPath == null && previewPath == null) requestPermissionsAndStart()
    }

    val fileToPreview = previewFile
    if (fileToPreview != null) {
        PhotoPreview(
            file = fileToPreview,
            isSaving = isSaving,
            fix = currentFix,
            onConfirm = {
                hasLocationPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                viewModel.onPhotoCaptured(fileToPreview, hasLocationPermission, onBack)
            },
            onRetake = {
                fileToPreview.delete()
                previewPath = null
                launchCamera()
            },
            corridor = currentFix?.let(viewModel::corridorFor),
            previousDistanceM = currentFix?.let(viewModel::distanceFromPrevious),
        )
        confirmation?.let { candidate ->
            LowAccuracyConfirmation(
                fix = candidate,
                onConfirm = {
                    val granted =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.confirmLowAccuracy()
                    } else {
                        hasLocationPermission = false
                        viewModel.onPhotoCaptured(fileToPreview, false, onBack)
                    }
                },
                onRetry = viewModel::retryMeasurement,
            )
        }
        corridorWarning?.let { outside ->
            LuxConfirmDialog(
                title = "Vị trí ngoài hành lang tuyến",
                message =
                    "Vị trí đo cách tuyến %.1f m, vượt %.0f m. ".format(
                        Locale.ROOT,
                        outside.distanceM,
                        ROUTE_CORRIDOR_METRES,
                    ) + "Quản lý có thể trả lại báo cáo. Hãy đứng sát cột rồi đo lại nếu có thể.",
                confirmLabel = "Vẫn lưu vị trí này",
                onConfirm = viewModel::confirmOutsideCorridor,
                onDismiss = viewModel::retryMeasurement,
                dismissLabel = "Đo lại",
            )
        }
        if (locationMissing) {
            LuxConfirmDialog(
                title = "Chưa lấy được vị trí",
                message = "Đo vị trí cột cần GPS thật. Bật định vị rồi thử lại, hoặc bỏ ảnh này.",
                confirmLabel = "Thử lại",
                onConfirm = ::retryLocation,
                onDismiss = ::discardPhoto,
                dismissLabel = "Bỏ ảnh",
            )
        }
    } else {
        PoleGpsMeasureContent(state = state, onBack = onBack, onRetryPermission = ::retryPermissions)
    }
}

@Composable
fun PoleGpsMeasureContent(
    state: PoleGpsMeasureUiState,
    onBack: () -> Unit,
    onRetryPermission: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val title = if (state is PoleGpsMeasureUiState.Success) state.poleLabel else "Chụp ảnh cột đèn"
        AppTopBar(title = title, onBack = onBack)
        when (state) {
            PoleGpsMeasureUiState.Loading -> CenteredMessage("Đang chuẩn bị chụp ảnh...", showProgress = true)
            PoleGpsMeasureUiState.Empty ->
                CenteredMessage("Cột này không thuộc lệnh này.", actionLabel = "Quay lại", onAction = onBack)
            is PoleGpsMeasureUiState.Error ->
                CenteredMessage(state.message, actionLabel = "Thử lại", onAction = onRetryPermission)
            is PoleGpsMeasureUiState.Success ->
                CenteredMessage("Đang mở camera...", showProgress = true)
        }
    }
}

@Composable
internal fun PhotoPreview(
    file: File,
    isSaving: Boolean,
    fix: GpsFix?,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    corridor: CorridorCheck? = null,
    previousDistanceM: Double? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        AsyncImage(
            model = file,
            contentDescription = "Ảnh cột đèn vừa chụp",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(Radius.card)),
        )
        fix?.let {
            Text("Sai số GPS: ±%.1f m".format(Locale.ROOT, it.accuracyM), style = MaterialTheme.typography.bodyLarge)
            Text("Thời gian mẫu: ${it.capturedAt}", style = MaterialTheme.typography.bodyMedium)
        }
        corridor?.let {
            Text(
                corridorPreviewLabel(it),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (it is CorridorCheck.Outside) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
        previousDistanceLabel(previousDistanceM)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CaptureActions(enabled = !isSaving, isSaving = isSaving, onRetake = onRetake, onConfirm = onConfirm)
    }
}

@Composable
private fun CaptureActions(
    enabled: Boolean,
    isSaving: Boolean,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    LuxActionPair(
        modifier = Modifier.navigationBarsPadding(),
        secondary = { actionModifier ->
            OutlinedButton(
                onClick = onRetake,
                enabled = enabled,
                modifier = actionModifier.heightIn(min = Dimens.minTouchTarget),
                shape = RoundedCornerShape(Radius.control),
            ) { Text("Chụp lại") }
        },
        primary = { actionModifier ->
            Button(
                onClick = onConfirm,
                enabled = enabled,
                modifier = actionModifier.heightIn(min = Dimens.minTouchTarget),
                shape = RoundedCornerShape(Radius.control),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Dùng ảnh này")
                }
            }
        },
    )
}

@Composable
private fun CenteredMessage(
    text: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (showProgress) {
            Spacer(Modifier.height(Spacing.md))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun LocationWatchingEffect(
    fineLocationGranted: Boolean,
    viewModel: PoleGpsMeasureViewModel,
) {
    LaunchedEffect(fineLocationGranted, viewModel) {
        if (fineLocationGranted) viewModel.startWatchingLocation() else viewModel.stopWatchingLocation()
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.stopWatchingLocation() }
    }
}

@Composable
internal fun LowAccuracyConfirmation(
    fix: GpsFix,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
) {
    LuxConfirmDialog(
        title = "Sai số GPS lớn",
        message =
            "Sai số GPS: ±%.1f m\nThời gian mẫu: %s\n".format(Locale.ROOT, fix.accuracyM, fix.capturedAt) +
                "Có thể chờ tín hiệu tốt hơn và đo lại, hoặc xác nhận lưu vị trí này.",
        confirmLabel = "Vẫn lưu vị trí này",
        onConfirm = onConfirm,
        onDismiss = onRetry,
        dismissLabel = "Đo lại",
    )
}
