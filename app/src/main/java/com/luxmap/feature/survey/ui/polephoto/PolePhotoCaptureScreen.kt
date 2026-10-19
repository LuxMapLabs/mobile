package com.luxmap.feature.survey.ui.polephoto

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton
import java.io.File

@Composable
fun PolePhotoCaptureRoute(
    onDone: () -> Unit,
    viewModel: PolePhotoCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                // Show the photo back to the field engineer first instead of saving right away -
                // a bad shot (blur, wrong pole, auto-exposure washed it out) should not go to
                // Room/sync_queue at all, so retake can simply overwrite this file.
                previewFile = file
            } else {
                onDone()
            }
        }

    if (previewFile != null) {
        BackHandler {
            previewFile?.delete()
            onDone()
        }
    }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    // CAMERA is a hard requirement here - some OEM camera apps (confirmed on a real Samsung
    // device) deny the ACTION_IMAGE_CAPTURE intent with a SecurityException, crashing the whole
    // process, when the calling app does not hold this permission itself. ACCESS_FINE_LOCATION
    // stays best-effort like before (missing GPS is not fatal, just means lat/lng 0,0).
    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                launchCamera()
            } else {
                onDone()
            }
        }

    LaunchedEffect(Unit) {
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

    val fileToPreview = previewFile
    if (fileToPreview != null) {
        PolePhotoPreview(
            file = fileToPreview,
            isSaving = isSaving,
            onConfirm = {
                isSaving = true
                viewModel.onPhotoCaptured(fileToPreview, onDone)
            },
            onRetake = {
                fileToPreview.delete()
                previewFile = null
                launchCamera()
            },
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun PolePhotoPreview(
    file: File,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
        AsyncImage(
            model = file,
            contentDescription = "Ảnh cột đèn vừa chụp",
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(Radius.card)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(
                onClick = onRetake,
                enabled = !isSaving,
                modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
            ) {
                Text("Chụp lại")
            }
            PrimaryButton(
                text = "Dùng ảnh này",
                onClick = onConfirm,
                isLoading = isSaving,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
