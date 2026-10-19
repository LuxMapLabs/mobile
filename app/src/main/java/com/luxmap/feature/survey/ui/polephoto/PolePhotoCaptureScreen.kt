package com.luxmap.feature.survey.ui.polephoto

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.theme.Blue500
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Navy900
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import java.io.File

// Near-black navy, matches the design doc's own bg (#0B1526) - a bespoke tone for this immersive
// post-capture review card, distinct from the app's generic Dark950 background token.
private val PolePhotoPreviewBackground = Color(0xFF0B1526)

// Design doc's own saving-state tint for the confirm button, a darkened Blue500 - not the shared
// PrimaryButton (which follows colorScheme.primary, Green400 in this screen's forced-dark theme,
// not the blue this design calls for) and not an existing named token.
private val PolePhotoConfirmSavingColor = Color(0xFF3B5A8A)

// Tighter than ButtonDefaults.ButtonWithIconContentPadding (its 24dp end padding is sized for a
// standalone trailing icon, not a half-width icon+label button) - the default still wrapped
// "Dùng ảnh này" onto 2 lines on a real device, confirmed by a manual check.
private val PolePhotoButtonContentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.md)

@Composable
fun PolePhotoCaptureRoute(
    poleId: String,
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

    // Design doc (mã 4d) is an all-dark review card, same forceDark convention CaptureScreen
    // (F04) uses for its own night-survey screens - night-readability here matters more than
    // following the system theme, and this screen is reached straight from the camera intent.
    LuxMapTheme(forceDark = true) {
        val fileToPreview = previewFile
        if (fileToPreview != null) {
            PolePhotoPreview(
                file = fileToPreview,
                poleId = poleId,
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
                onClose = ::closePreview,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(PolePhotoPreviewBackground)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun PolePhotoPreview(
    file: File,
    poleId: String,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(PolePhotoPreviewBackground)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Navy900, PolePhotoPreviewBackground)))
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
            AsyncImage(
                model = file,
                contentDescription = "Ảnh cột đèn vừa chụp",
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(Radius.block)),
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
            ConfirmButton(isSaving = isSaving, onClick = onConfirm, modifier = Modifier.weight(1f))
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
        // Default M3 content padding is sized for text-only buttons - with a leading icon on a
        // half-width button, it pushed "Chụp lại" onto 2 lines on a real device.
        contentPadding = PolePhotoButtonContentPadding,
    ) {
        Icon(imageVector = Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text("Chụp lại")
    }
}

@Composable
private fun ConfirmButton(
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !isSaving,
        modifier = modifier.heightIn(min = PrimaryButtonHeight),
        shape = RoundedCornerShape(Radius.control),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Blue500,
                contentColor = Color.White,
                disabledContainerColor = PolePhotoConfirmSavingColor,
                disabledContentColor = Color.White,
            ),
        contentPadding = PolePhotoButtonContentPadding,
    ) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(if (isSaving) "Đang lưu…" else "Dùng ảnh này")
    }
}
