package com.luxmap.feature.survey.ui.polephoto

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.luxmap.core.theme.Blue500
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Navy900
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val PoleVideoPreviewBackground = Color(0xFF0B1526)
private val PoleVideoConfirmSavingColor = Color(0xFF3B5A8A)
private val PoleVideoButtonContentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.md)
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
    onDone: () -> Unit,
    viewModel: PoleVideoCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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

    fun closePreview() {
        previewFile?.delete()
        onDone()
    }

    if (previewFile != null) {
        BackHandler { closePreview() }
    }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureVideoLauncher.launch(uri)
    }

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

    LuxMapTheme(forceDark = true) {
        val fileToPreview = previewFile
        if (fileToPreview != null) {
            PoleVideoPreview(
                file = fileToPreview,
                poleId = poleId,
                isSaving = isSaving,
                onConfirm = {
                    isSaving = true
                    viewModel.onVideoCaptured(fileToPreview, onDone)
                },
                onRetake = {
                    fileToPreview.delete()
                    previewFile = null
                    launchCamera()
                },
                onClose = ::closePreview,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(PoleVideoPreviewBackground)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun PoleVideoPreview(
    file: File,
    poleId: String,
    isSaving: Boolean,
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

    Column(modifier = Modifier.fillMaxSize().background(PoleVideoPreviewBackground)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Navy900, PoleVideoPreviewBackground)))
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.xs, bottom = Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .alpha(if (isSaving) 0.4f else 1f)
                            .clickable(enabled = !isSaving, onClick = onClose)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(Radius.control)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng", tint = Color.White)
                }
                Column {
                    Text(text = "ID cột đèn", style = LabelTextStyle, color = Color.White.copy(alpha = 0.7f))
                    Text(text = poleId, style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(Radius.block)),
            )
        }
        if (isReady && !isValidForUpload) {
            Text(
                text =
                    if (!isValidDuration) {
                        "Clip quá ngắn hoặc quá dài (cần 3-30 giây), vui lòng quay lại"
                    } else {
                        "Clip quá nặng (tối đa 100MB), vui lòng quay lại với video ngắn hơn"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RetakeButton(enabled = !isSaving, onClick = onRetake, modifier = Modifier.weight(1f))
            ConfirmButton(
                isSaving = isSaving,
                enabled = isValidForUpload,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RetakeButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = PrimaryButtonHeight).alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(Radius.control),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = Color.White),
        contentPadding = PoleVideoButtonContentPadding,
    ) {
        Icon(imageVector = Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text("Quay lại")
    }
}

@Composable
private fun ConfirmButton(
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isSaving,
        modifier = modifier.heightIn(min = PrimaryButtonHeight),
        shape = RoundedCornerShape(Radius.control),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Blue500,
                contentColor = Color.White,
                disabledContainerColor = PoleVideoConfirmSavingColor,
                disabledContentColor = Color.White,
            ),
        contentPadding = PoleVideoButtonContentPadding,
    ) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(if (isSaving) "Đang lưu…" else "Dùng video này")
    }
}
