package com.luxmap.feature.workorder.polegps.ui.measure

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.PrimaryButton

@Composable
fun PoleGpsMeasureRoute(
    onBack: () -> Unit,
    viewModel: PoleGpsMeasureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
        }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onPermissionGranted() else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    PoleGpsMeasureContent(
        state = state,
        onBack = onBack,
        onRetryPermission = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    )
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
            is PoleGpsMeasureUiState.Success -> CenteredMessage("Đang mở camera...", showProgress = true)
        }
    }
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
