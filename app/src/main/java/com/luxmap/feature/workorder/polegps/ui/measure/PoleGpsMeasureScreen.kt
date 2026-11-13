package com.luxmap.feature.workorder.polegps.ui.measure

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.survey.domain.GpsAccuracyGate
import com.luxmap.feature.workorder.polegps.domain.FixQuality

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
        onSave = viewModel::onSave,
        onMeasureAgain = viewModel::onMeasureAgain,
        onRetryPermission = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    )
}

@Composable
fun PoleGpsMeasureContent(
    state: PoleGpsMeasureUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMeasureAgain: () -> Unit,
    onRetryPermission: () -> Unit,
) {
    when (state) {
        PoleGpsMeasureUiState.Loading -> CenteredMessage("Đang chuẩn bị đo GPS...", showProgress = true)
        PoleGpsMeasureUiState.Empty ->
            CenteredMessage("Cột này không thuộc lệnh này.", actionLabel = "Quay lại", onAction = onBack)
        is PoleGpsMeasureUiState.Error ->
            CenteredMessage(state.message, actionLabel = "Thử lại", onAction = onRetryPermission)
        is PoleGpsMeasureUiState.Success -> MeasureBody(state, onSave, onMeasureAgain)
    }
}

private fun formatCoordinate(value: Double?): String = value?.let { "%.6f".format(it) } ?: "Chưa có"

private fun qualityLabel(quality: FixQuality): String =
    when (quality) {
        FixQuality.WAITING -> "Đang chờ tín hiệu GPS"
        FixQuality.WEAK ->
            "Sai số cao hơn ${"%.0f".format(GpsAccuracyGate.PROPOSED_THRESHOLD_METERS)} m. Di chuyển ra nơi thoáng hơn"
        FixQuality.HOLDING -> "Giữ yên thiết bị..."
        FixQuality.READY -> "Đủ độ chính xác"
    }

@Composable
private fun MeasureBody(
    state: PoleGpsMeasureUiState.Success,
    onSave: () -> Unit,
    onMeasureAgain: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(text = state.poleLabel, style = MaterialTheme.typography.headlineSmall)
        InfoCard(title = "Vị trí GIS hiện tại") {
            Text(text = "Lat: ${formatCoordinate(state.previous?.lat)}")
            Text(text = "Lng: ${formatCoordinate(state.previous?.lng)}")
        }
        InfoCard(title = "Vị trí đo được") {
            Text(text = "Lat: ${formatCoordinate(state.liveFix?.lat)}")
            Text(text = "Lng: ${formatCoordinate(state.liveFix?.lng)}")
            val accuracyText = state.liveFix?.let { "±${"%.1f".format(it.accuracyM)} m" } ?: "Chưa có"
            Text(text = "Độ chính xác: $accuracyText")
            Text(text = qualityLabel(state.quality), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.saved) {
            Text(text = "Đã lưu vị trí cột", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onMeasureAgain,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
                shape = RoundedCornerShape(Radius.control),
            ) {
                Text(text = "Đo lại")
            }
        } else {
            PrimaryButton(
                text = "Lưu vị trí",
                onClick = onSave,
                enabled = state.canSave && !state.saved,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        content()
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
