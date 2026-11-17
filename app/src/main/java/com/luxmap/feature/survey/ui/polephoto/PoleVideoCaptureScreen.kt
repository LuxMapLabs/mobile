package com.luxmap.feature.survey.ui.polephoto

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.LuxLoadingRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_DURATION_LIMIT_SECONDS = 30

// MediaStore.ACTION_VIDEO_CAPTURE via a custom contract, because the stock
// ActivityResultContracts.CaptureVideo() has no way to set EXTRA_DURATION_LIMIT. Same shape
// (createIntent/parseResult, FLAG_GRANT_*_URI_PERMISSION) as AOSP's own TakePicture() contract.
private class CaptureVideoWithDurationLimit(
    private val durationLimitSeconds: Int,
) : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(
        context: Context,
        input: Uri,
    ): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, durationLimitSeconds)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean = resultCode == Activity.RESULT_OK
}

internal fun readVideoDurationMs(file: File): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull() ?: 0L
    } finally {
        retriever.release()
    }
}

@Composable
fun PoleVideoCaptureRoute(
    poleId: String,
    source: PoleVideoCaptureSource = PoleVideoCaptureSource.CAMERA,
    onDone: () -> Unit,
    viewModel: PoleVideoCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

    val captureVideoLauncher =
        rememberLauncherForActivityResult(CaptureVideoWithDurationLimit(MAX_DURATION_LIMIT_SECONDS)) { success ->
            val file = pendingFile
            if (success && file != null) {
                previewFile = file
            } else {
                onDone()
            }
        }

    val pickMediaLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                onDone()
            } else {
                val file = viewModel.outputFile(context.filesDir)
                scope.launch {
                    val copied = copyGalleryImageToFile(context.contentResolver.openInputStream(uri), file)
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
        onDone()
    }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureVideoLauncher.launch(uri)
    }

    fun launchGalleryPicker() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

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

    LaunchedEffect(Unit) {
        when (source) {
            PoleVideoCaptureSource.CAMERA -> {
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
            PoleVideoCaptureSource.GALLERY -> {
                if (hasRequestedPermissions) {
                    launchGalleryPicker()
                } else {
                    hasRequestedPermissions = true
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    LuxMapTheme(forceDark = true) {
        val fileToPreview = previewFile
        if (fileToPreview != null) {
            PoleVideoPreview(
                file = fileToPreview,
                poleId = poleId,
                isSaving = isSaving,
                retakeLabel = if (source == PoleVideoCaptureSource.GALLERY) "Chọn video khác" else "Quay lại",
                onConfirm = {
                    isSaving = true
                    viewModel.onVideoCaptured(fileToPreview, onDone)
                },
                onRetake = {
                    fileToPreview.delete()
                    previewFile = null
                    when (source) {
                        PoleVideoCaptureSource.CAMERA -> launchCamera()
                        PoleVideoCaptureSource.GALLERY -> launchGalleryPicker()
                    }
                },
                onClose = ::closePreview,
            )
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
private fun PoleVideoPreview(
    file: File,
    poleId: String,
    isSaving: Boolean,
    retakeLabel: String,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var durationMs by remember(file) { mutableStateOf<Long?>(null) }
    var sizeBytes by remember(file) { mutableStateOf<Long?>(null) }
    LaunchedEffect(file) {
        durationMs = withContext(Dispatchers.IO) { readVideoDurationMs(file) }
        sizeBytes = withContext(Dispatchers.IO) { file.length() }
    }
    val isValidDuration = durationMs?.let(::isValidPoleVideoDuration) ?: false
    val isValidSize = sizeBytes?.let(::isValidPoleVideoSize) ?: false
    val isReady = durationMs != null && sizeBytes != null
    val isValidForUpload = isValidDuration && isValidSize

    val player =
        remember(file) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
            }
        }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    PoleMediaPreviewFrame(
        poleId = poleId,
        isSaving = isSaving,
        retakeLabel = retakeLabel,
        confirmLabel = "Dùng video này",
        onClose = onClose,
        onRetake = onRetake,
        onConfirm = onConfirm,
        confirmEnabled = isValidForUpload,
        warning =
            if (isReady && !isValidForUpload) {
                if (!isValidDuration) {
                    "Clip quá ngắn hoặc quá dài (cần 3-30 giây), vui lòng quay lại"
                } else {
                    "Clip quá nặng (tối đa 100MB), vui lòng quay lại với video ngắn hơn"
                }
            } else {
                null
            },
    ) {
        AndroidView(
            factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        )
    }
}
