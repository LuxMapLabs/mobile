package com.luxmap.feature.survey.ui.polephoto

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.feature.profile.ui.luxmeter.LuxMeterPairingViewModel
import com.luxmap.feature.survey.ui.polephoto.lux.LuxCardViewModel
import com.luxmap.feature.survey.ui.polephoto.lux.LuxMeasureSection
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PolePhotoCaptureRoute(
    poleId: String,
    source: PolePhotoCaptureSource = PolePhotoCaptureSource.CAMERA,
    onDone: () -> Unit,
    onNextPole: (poleId: String) -> Unit = {},
    viewModel: PolePhotoCaptureViewModel = hiltViewModel(),
    luxCardViewModel: LuxCardViewModel = hiltViewModel(),
    luxPairingViewModel: LuxMeterPairingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }
    val locationMissing by viewModel.locationMissing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    var observation by remember { mutableStateOf<Observation?>(null) }
    var showRoutePrompt by rememberSaveable { mutableStateOf(false) }
    var showSkipReasons by rememberSaveable { mutableStateOf(false) }

    fun finish(nextPoleId: String?) {
        if (nextPoleId != null) onNextPole(nextPoleId) else onDone()
    }

    // Leaving the camera or the preview without a photo: in route mode stay on this pole and
    // offer retry/skip/stop instead of dropping the engineer back on the list.
    fun leaveWithoutPhoto() {
        if (viewModel.isRouteMode) showRoutePrompt = true else onDone()
    }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                // Show the photo back to the field engineer first instead of saving right away -
                // a bad shot (blur, wrong pole, auto-exposure washed it out) should not go to
                // Room/sync_queue at all, so retake can simply overwrite this file.
                previewFile = file
            } else {
                leaveWithoutPhoto()
            }
        }

    val pickMediaLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                onDone()
            } else {
                val file = viewModel.outputFile(context.filesDir)
                scope.launch {
                    val copied = copyGalleryImageAsJpeg(context.contentResolver.openInputStream(uri), file)
                    if (copied) {
                        previewFile = file
                    } else {
                        file.delete()
                        onDone()
                    }
                }
            }
        }

    fun closePreview() {
        previewFile?.delete()
        previewFile = null
        leaveWithoutPhoto()
    }

    fun launchCamera() {
        showRoutePrompt = false
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    fun launchGalleryPicker() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // CAMERA is a hard requirement here - some OEM camera apps (confirmed on a real Samsung
    // device) deny the ACTION_IMAGE_CAPTURE intent with a SecurityException, crashing the whole
    // process, when the calling app does not hold this permission itself. ACCESS_FINE_LOCATION
    // is requested here too; without a GPS fix the photo is held for a retry instead of saved.
    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                launchCamera()
            } else {
                onDone()
            }
        }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            launchGalleryPicker()
        }

    val retryLocationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            val file = previewFile
            val picked = observation
            if (file != null && picked != null) {
                viewModel.onPhotoCaptured(file, picked.wireValue, luxCardViewModel.recordedReading, ::finish)
            }
        }

    fun retryLocation() {
        val file = previewFile ?: return
        val picked = observation ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onPhotoCaptured(file, picked.wireValue, luxCardViewModel.recordedReading, ::finish)
        } else {
            retryLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        // Restored onto the route prompt (rotation, process death): wait for the engineer's choice.
        if (showRoutePrompt) return@LaunchedEffect
        when (source) {
            PolePhotoCaptureSource.CAMERA -> {
                val cameraGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                when {
                    cameraGranted -> launchCamera()
                    hasRequestedPermissions -> onDone()
                    else -> {
                        hasRequestedPermissions = true
                        permissionsLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
                        )
                    }
                }
            }
            PolePhotoCaptureSource.GALLERY -> {
                if (hasRequestedPermissions) {
                    launchGalleryPicker()
                } else {
                    hasRequestedPermissions = true
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    // Design doc (mã 4d) is an all-dark review card - night-readability here matters more than
    // following the system theme, and this screen is reached straight from the camera intent.
    LuxMapTheme(forceDark = true) {
        val fileToPreview = previewFile
        if (showRoutePrompt && fileToPreview == null) {
            RoutePolePrompt(
                title = routeTitle(poleId, progress),
                onCapture = ::launchCamera,
                onSkip = { showSkipReasons = true },
                onStop = onDone,
            )
            if (showSkipReasons) {
                SkipReasonSheet(
                    poleId = poleId,
                    onDismiss = { showSkipReasons = false },
                    onPick = { reason ->
                        showSkipReasons = false
                        viewModel.onSkip(reason, ::finish)
                    },
                )
            }
        } else if (fileToPreview != null) {
            PolePhotoPreview(
                file = fileToPreview,
                title = routeTitle(poleId, progress),
                footer =
                    if (source == PolePhotoCaptureSource.CAMERA) {
                        { LuxMeasureSection(luxCardViewModel, luxPairingViewModel, isEnabled = !isSaving) }
                    } else {
                        null
                    },
                isSaving = isSaving && !locationMissing,
                retakeLabel = if (source == PolePhotoCaptureSource.GALLERY) "Chọn ảnh khác" else "Chụp lại",
                onObserve = { picked ->
                    observation = picked
                    isSaving = true
                    viewModel.onPhotoCaptured(
                        fileToPreview,
                        picked.wireValue,
                        luxCardViewModel.recordedReading,
                        ::finish,
                    )
                },
                onRetake = {
                    fileToPreview.delete()
                    previewFile = null
                    when (source) {
                        PolePhotoCaptureSource.CAMERA -> launchCamera()
                        PolePhotoCaptureSource.GALLERY -> launchGalleryPicker()
                    }
                },
                onClose = ::closePreview,
            )
            if (locationMissing) {
                LuxConfirmDialog(
                    title = "Chưa lấy được vị trí",
                    message = "Ảnh cột cần vị trí GPS thật. Bật định vị rồi thử lại, hoặc bỏ ảnh này.",
                    confirmLabel = "Thử lại",
                    onConfirm = ::retryLocation,
                    onDismiss = {
                        isSaving = false
                        closePreview()
                    },
                    dismissLabel = "Bỏ ảnh",
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                LuxLoadingRegion(description = "Đang mở camera", modifier = Modifier.align(Alignment.Center)) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PolePhotoPreview(
    file: File,
    title: String,
    footer: (@Composable () -> Unit)?,
    isSaving: Boolean,
    retakeLabel: String,
    onObserve: (Observation) -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
) {
    PoleMediaPreviewScaffold(
        poleId = title,
        isSaving = isSaving,
        footer = footer,
        onClose = onClose,
        actions = { ObservationActions(isSaving, retakeLabel, onObserve, onRetake) },
        media = {
            AsyncImage(
                model = file,
                contentDescription = "Ảnh cột đèn vừa chụp",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}
