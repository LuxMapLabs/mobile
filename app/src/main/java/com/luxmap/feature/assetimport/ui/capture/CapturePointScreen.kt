package com.luxmap.feature.assetimport.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.TopSlideToast
import com.luxmap.core.ui.components.TopToastState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CapturePointRoute(
    tripId: String,
    onBack: () -> Unit,
    onOpenTripList: (String) -> Unit,
    viewModel: CapturePointViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val toastState = remember { TopToastState() }
    val scope = rememberCoroutineScope()

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                viewModel.onPhotoCaptured(file)
            }
        }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.CAMERA] == true) launchCamera()
        }

    fun requestCameraAndShoot() {
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            launchCamera()
        } else {
            permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    val ready = uiState as? CapturePointUiState.Ready
    LaunchedEffect(ready?.justSavedSeq) {
        val savedSeq = ready?.justSavedSeq ?: return@LaunchedEffect
        scope.launch { toastState.show("Đã lưu cột #$savedSeq") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is CapturePointUiState.Loading -> Unit
            is CapturePointUiState.Error -> Text(state.message, modifier = Modifier.padding(Spacing.lg))
            is CapturePointUiState.Ready ->
                CapturePointContent(
                    state = state,
                    onBack = onBack,
                    onOpenTripList = { onOpenTripList(tripId) },
                    onShoot = ::requestCameraAndShoot,
                    onRetake = { viewModel.onRetake() },
                    onNoteChanged = viewModel::onNoteChanged,
                    onSave = viewModel::onSave,
                )
        }
        TopSlideToast(
            state = toastState,
            modifier = Modifier.statusBarsPadding().padding(Spacing.md).align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun CapturePointContent(
    state: CapturePointUiState.Ready,
    onBack: () -> Unit,
    onOpenTripList: () -> Unit,
    onShoot: () -> Unit,
    onRetake: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val canSave = state.pendingShot != null && state.note.isNotBlank() && !state.isSaving
    val hint =
        when {
            state.pendingShot == null -> "Chụp ảnh cột trước"
            state.note.isBlank() -> "Nhập ghi chú để lưu"
            else -> null
        }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CaptureHeader(
            poleCount = state.poleCount,
            nextSeq = state.nextSeq,
            onBack = onBack,
            onOpenTripList = onOpenTripList,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { CameraArea(pendingShot = state.pendingShot, onShoot = onShoot, onRetake = onRetake) }
            item {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    MiniMapPlaceholder()
                    SubstationRow()
                    NoteField(note = state.note, onNoteChanged = onNoteChanged)
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (hint != null) {
                Text(
                    text = hint,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            PrimaryButton(
                text = "Lưu & tiếp tục chụp",
                onClick = onSave,
                enabled = canSave,
                isLoading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CaptureHeader(
    poleCount: Int,
    nextSeq: Int,
    onBack: () -> Unit,
    onOpenTripList: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient)
                .statusBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("KV-07 · Chuyến đang chạy", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
            Text("Cột #$nextSeq", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radius.control))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onOpenTripList)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text("$poleCount cột", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
