package com.luxmap.feature.workorder.polegps.ui.measure

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.survey.ui.polephoto.PoleMediaPreviewFrame
import com.luxmap.feature.workorder.polegps.data.CorridorCheck
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.ROUTE_CORRIDOR_METRES
import com.luxmap.feature.workorder.polegps.ui.corridorPreviewLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SAMPLE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

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
    var photoSource by rememberSaveable { mutableStateOf<PhotoSource?>(null) }
    var galleryError by rememberSaveable { mutableStateOf<String?>(null) }
    val galleryFix by viewModel.galleryFix.collectAsState()
    val galleryRejection by viewModel.galleryRejection.collectAsState()
    val scope = rememberCoroutineScope()
    val fromGallery = photoSource == PhotoSource.GALLERY

    val openDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                onBack()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val file = viewModel.outputFile(context.filesDir)
                val copied =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)!!.use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                        }.isSuccess
                    }
                if (copied) {
                    previewPath = file.absolutePath
                    viewModel.onGalleryPhotoPicked(file, onBack)
                } else {
                    file.delete()
                    galleryError = "Không đọc được ảnh đã chọn. Hãy chọn ảnh khác."
                }
            }
        }

    val mediaLocationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openDocumentLauncher.launch(arrayOf("image/*"))
            } else {
                galleryError = "Cần cho phép LuxMap đọc vị trí trong ảnh để lấy GPS từ thư viện."
            }
        }

    fun launchGallery() {
        galleryError = null
        val needsPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            mediaLocationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            openDocumentLauncher.launch(arrayOf("image/*"))
        }
    }

    fun pickAnotherGalleryPhoto() {
        viewModel.dismissGalleryRejection()
        viewModel.dismissWarning()
        previewFile?.delete()
        previewPath = null
        launchGallery()
    }

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

    val fileToPreview = previewFile
    if (fileToPreview != null) {
        val shownFix = if (fromGallery) galleryFix else currentFix
        PhotoPreview(
            file = fileToPreview,
            poleLabel = (state as? PoleGpsMeasureUiState.Success)?.poleLabel ?: "Cột đèn",
            onClose = ::discardPhoto,
            isSaving = isSaving,
            fix = shownFix,
            onConfirm = {
                if (fromGallery) {
                    viewModel.confirmGalleryPhoto()
                } else {
                    hasLocationPermission =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    viewModel.onPhotoCaptured(fileToPreview, hasLocationPermission, onBack)
                }
            },
            onRetake = {
                if (fromGallery) {
                    pickAnotherGalleryPhoto()
                } else {
                    fileToPreview.delete()
                    previewPath = null
                    viewModel.dismissWarning()
                    launchCamera()
                }
            },
            corridor = shownFix?.let(viewModel::corridorFor),
            previousDistanceM = shownFix?.let(viewModel::distanceFromPrevious),
            retakeLabel = if (fromGallery) "Chọn ảnh khác" else "Chụp lại",
            fromGallery = fromGallery,
        )
        galleryRejection?.let { reason ->
            LuxConfirmDialog(
                title = "Không dùng được ảnh này",
                message = reason,
                confirmLabel = "Chọn ảnh khác",
                onConfirm = ::pickAnotherGalleryPhoto,
                onDismiss = {
                    viewModel.dismissGalleryRejection()
                    discardPhoto()
                },
                dismissLabel = "Bỏ ảnh",
            )
        }
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
                onDismissRequest = viewModel::dismissWarning,
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
                onDismiss = {
                    if (fromGallery) {
                        pickAnotherGalleryPhoto()
                    } else {
                        fileToPreview.delete()
                        previewPath = null
                        viewModel.dismissWarning()
                        launchCamera()
                    }
                },
                dismissLabel = if (fromGallery) "Chọn ảnh khác" else "Chụp lại",
                onDismissRequest = viewModel::dismissWarning,
            )
        }
        if (locationMissing && !fromGallery) {
            LuxConfirmDialog(
                title = "Chưa lấy được vị trí",
                message = "Đo vị trí cột cần GPS thật. Bật định vị rồi thử lại, hoặc bỏ ảnh này.",
                confirmLabel = "Thử lại",
                onConfirm = ::retryLocation,
                onDismiss = ::discardPhoto,
                dismissLabel = "Bỏ ảnh",
            )
        }
    } else if (photoSource == null) {
        PhotoSourceChooser(
            onBack = onBack,
            onCamera = {
                photoSource = PhotoSource.CAMERA
                requestPermissionsAndStart()
            },
            onGallery = {
                photoSource = PhotoSource.GALLERY
                viewModel.onPermissionGranted()
                launchGallery()
            },
        )
    } else {
        PoleGpsMeasureContent(
            state = state,
            onBack = onBack,
            onRetryPermission = if (fromGallery) ::launchGallery else ::retryPermissions,
            openingLabel = if (fromGallery) "Đang mở thư viện ảnh..." else "Đang mở camera...",
        )
        galleryError?.let { message ->
            LuxConfirmDialog(
                title = "Chưa chọn được ảnh",
                message = message,
                confirmLabel = "Thử lại",
                onConfirm = ::launchGallery,
                onDismiss = onBack,
                dismissLabel = "Quay lại",
            )
        }
    }
}

private enum class PhotoSource { CAMERA, GALLERY }

@Composable
private fun PhotoSourceChooser(
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LuxChildTopBar(title = "Đo GPS cột", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Lấy vị trí cột từ ảnh chụp mới hoặc từ ảnh gốc có sẵn trong máy.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = "Chụp ảnh",
                onClick = onCamera,
                modifier = Modifier.fillMaxWidth(),
            )
            LuxSecondaryButton(
                text = "Chọn từ thư viện",
                onClick = onGallery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Rounded.PhotoLibrary,
            )
            Text(
                text = "Ảnh thư viện phải là ảnh gốc có vị trí GPS. Ảnh gửi qua ứng dụng chat thường đã mất vị trí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PoleGpsMeasureContent(
    state: PoleGpsMeasureUiState,
    onBack: () -> Unit,
    onRetryPermission: () -> Unit,
    openingLabel: String = "Đang mở camera...",
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val title = if (state is PoleGpsMeasureUiState.Success) state.poleLabel else "Chụp ảnh cột đèn"
        LuxChildTopBar(title = title, onBack = onBack)
        when (state) {
            PoleGpsMeasureUiState.Loading -> PreparingMessage("Đang chuẩn bị chụp ảnh...")
            PoleGpsMeasureUiState.Empty ->
                LuxEmptyState(
                    title = "Không tìm thấy cột",
                    message = "Cột này không thuộc lệnh này.",
                    actionLabel = "Quay lại",
                    onAction = onBack,
                )
            is PoleGpsMeasureUiState.Error ->
                LuxErrorState(
                    onRetry = onRetryPermission,
                    title = "Chưa chụp được ảnh",
                    message = state.message,
                )
            is PoleGpsMeasureUiState.Success -> PreparingMessage(openingLabel)
        }
    }
}

@Composable
private fun PreparingMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun PhotoPreview(
    file: File,
    poleLabel: String,
    isSaving: Boolean,
    fix: GpsFix?,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
    corridor: CorridorCheck? = null,
    previousDistanceM: Double? = null,
    retakeLabel: String = "Chụp lại",
    fromGallery: Boolean = false,
) {
    PoleMediaPreviewFrame(
        poleId = poleLabel,
        isSaving = isSaving,
        retakeLabel = retakeLabel,
        confirmLabel = "Dùng ảnh này",
        onClose = onClose,
        onRetake = onRetake,
        onConfirm = onConfirm,
        footer = { MeasurementSummary(fix, corridor, previousDistanceM, fromGallery) },
    ) {
        AsyncImage(
            model = file,
            contentDescription = if (fromGallery) "Ảnh cột đèn chọn từ thư viện" else "Ảnh cột đèn vừa chụp",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MeasurementSummary(
    fix: GpsFix?,
    corridor: CorridorCheck?,
    previousDistanceM: Double?,
    fromGallery: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (corridor is CorridorCheck.Outside) {
            LuxBanner(message = corridorPreviewLabel(corridor), tone = LuxBannerTone.WARNING)
        }
        LuxCard(contentPadding = PaddingValues(Spacing.md)) {
            fix?.let {
                if (fromGallery) {
                    MeasurementRow("Nguồn vị trí", "GPS trong ảnh")
                    MeasurementRow("Toạ độ", "%.6f, %.6f".format(Locale.ROOT, it.lat, it.lng))
                    MeasurementRow("Thời gian chụp", SAMPLE_TIME_FORMAT.format(it.capturedAt))
                } else {
                    it.accuracyM?.let { accuracy ->
                        MeasurementRow("Sai số GPS", "±%.1f m".format(Locale.ROOT, accuracy))
                    }
                    MeasurementRow("Thời gian mẫu", SAMPLE_TIME_FORMAT.format(it.capturedAt))
                }
            }
            if (corridor != null && corridor !is CorridorCheck.Outside) {
                MeasurementRow("Hành lang tuyến", corridorPreviewLabel(corridor))
            }
            previousDistanceM?.let {
                MeasurementRow("Lệch so với lần đo trước", "%.1f m".format(Locale.ROOT, it))
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
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
    onDismissRequest: () -> Unit = onRetry,
) {
    LuxConfirmDialog(
        title = "Sai số GPS lớn",
        message =
            "Sai số GPS: ±%.1f m\nThời gian mẫu: %s\n".format(Locale.ROOT, fix.accuracyM ?: 0.0, fix.capturedAt) +
                "Có thể chờ tín hiệu tốt hơn và đo lại, hoặc xác nhận lưu vị trí này.",
        confirmLabel = "Vẫn lưu vị trí này",
        onConfirm = onConfirm,
        onDismiss = onRetry,
        dismissLabel = "Đo lại",
        onDismissRequest = onDismissRequest,
    )
}
