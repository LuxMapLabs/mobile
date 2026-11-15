package com.luxmap.feature.polelocation.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import java.io.File

@Composable
fun PoleLocationCaptureRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: PoleLocationCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val errorMessage by viewModel.errorMessage.collectAsState()
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                previewFile = file
            } else {
                onBack()
            }
        }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    // CAMERA is a hard requirement (same OEM crash risk documented on PoleGpsMeasureScreen).
    // ACCESS_FINE_LOCATION stays best-effort, but unlike that other screen, missing it here
    // means onPhotoCaptured will refuse to submit at all - this feature writes the map directly.
    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (hasLocationPermission) viewModel.startWatchingLocation()
            if (result[Manifest.permission.CAMERA] == true) {
                launchCamera()
            } else {
                onBack()
            }
        }

    fun requestPermissionsAndStart() {
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) viewModel.startWatchingLocation()
        when {
            cameraGranted -> launchCamera()
            hasRequestedPermissions -> onBack()
            else -> {
                hasRequestedPermissions = true
                permissionsLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            }
        }
    }

    LaunchedEffect(Unit) { requestPermissionsAndStart() }
    LaunchedEffect(errorMessage) { if (errorMessage != null) isSaving = false }

    val fileToPreview = previewFile
    if (fileToPreview != null) {
        PoleLocationPhotoPreview(
            file = fileToPreview,
            isSaving = isSaving,
            errorMessage = errorMessage,
            onConfirm = {
                isSaving = true
                viewModel.onPhotoCaptured(fileToPreview, hasLocationPermission, onDone)
            },
            onRetake = {
                fileToPreview.delete()
                previewFile = null
                viewModel.dismissError()
                launchCamera()
            },
        )
    }
}

@Composable
private fun PoleLocationPhotoPreview(
    file: File,
    isSaving: Boolean,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        AsyncImage(
            model = file,
            contentDescription = "Ảnh cột đèn vừa chụp",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(Radius.card)),
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
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
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedButton(
            onClick = onRetake,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
        ) { Text("Chụp lại") }
        Button(
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Dùng ảnh này")
            }
        }
    }
}
