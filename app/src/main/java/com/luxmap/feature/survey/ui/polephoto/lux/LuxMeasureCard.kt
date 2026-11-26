package com.luxmap.feature.survey.ui.polephoto.lux

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.LuxTextButton
import com.luxmap.core.ui.components.PrimaryButton
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VI = Locale.forLanguageTag("vi")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", VI)
private const val DAYLIGHT_HINT_LUX = 200.0

fun formatLux(value: Double): String = String.format(VI, "%.2f", value)

@Composable
fun LuxMeasureCard(
    state: LuxCardUiState,
    isEnabled: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onPair: () -> Unit,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onRetryConnect: () -> Unit,
    onRecord: () -> Unit,
    onRemeasure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = LuxTheme.colors.cardContainer,
    ) {
        if (state is LuxCardUiState.Collapsed) {
            CollapsedHeader(isEnabled = isEnabled, onExpand = onExpand)
            return@Surface
        }
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Đo lux (tuỳ chọn)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                LuxTextButton(text = "Không đo", onClick = onCollapse, enabled = isEnabled)
            }
            when (state) {
                LuxCardUiState.Collapsed -> Unit
                LuxCardUiState.NotPaired ->
                    MessageWithAction("Chưa ghép nối máy đo lux.", "Ghép nối", onPair, isEnabled)
                LuxCardUiState.PermissionMissing ->
                    MessageWithAction(
                        "Cần quyền Bluetooth để kết nối máy đo.",
                        "Cấp quyền",
                        onRequestPermission,
                        isEnabled,
                    )
                LuxCardUiState.BluetoothOff ->
                    MessageWithAction("Bluetooth đang tắt.", "Bật Bluetooth", onEnableBluetooth, isEnabled)
                LuxCardUiState.Connecting -> Connecting()
                is LuxCardUiState.Live -> Live(state = state, isEnabled = isEnabled, onRecord = onRecord)
                is LuxCardUiState.Recorded ->
                    Recorded(state = state, isEnabled = isEnabled, onRemeasure = onRemeasure)
                LuxCardUiState.Disconnected ->
                    MessageWithAction("Mất kết nối máy đo.", "Thử lại", onRetryConnect, isEnabled)
                LuxCardUiState.Unreachable ->
                    MessageWithAction(
                        "Không kết nối được máy đo. Kiểm tra máy đo đã bật và ở gần điện thoại.",
                        "Thử lại",
                        onRetryConnect,
                        isEnabled,
                    )
            }
        }
    }
}

@Composable
private fun CollapsedHeader(
    isEnabled: Boolean,
    onExpand: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(enabled = isEnabled, role = Role.Button, onClick = onExpand)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(imageVector = Icons.Rounded.Sensors, contentDescription = null)
        Text(
            text = "Đo lux (tuỳ chọn)",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(imageVector = Icons.Rounded.ExpandMore, contentDescription = null)
    }
}

@Composable
private fun MessageWithAction(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    isEnabled: Boolean,
) {
    Text(text = message, style = MaterialTheme.typography.bodyMedium)
    LuxSecondaryButton(text = actionLabel, onClick = onAction, enabled = isEnabled, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Connecting() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text = "Đang kết nối máy đo…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Live(
    state: LuxCardUiState.Live,
    isEnabled: Boolean,
    onRecord: () -> Unit,
) {
    LuxValue(value = state.latestLux)
    Text(
        text =
            if (state.stableLux != null) {
                "Số đã ổn định."
            } else {
                "Giữ yên máy đo… (${state.sampleCount}/${state.requiredSamples})"
            },
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.stableLux != null) LuxTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = "Đứng ngay dưới cột, giữ máy đo yên, cảm biến hướng thẳng lên đèn.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    DaylightHint(state.latestLux)
    PrimaryButton(
        text = "Ghi nhận",
        onClick = onRecord,
        enabled = isEnabled && state.stableLux != null,
        branded = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Recorded(
    state: LuxCardUiState.Recorded,
    isEnabled: Boolean,
    onRemeasure: () -> Unit,
) {
    LuxValue(value = state.lux)
    Text(
        text = "Đã ghi nhận lúc ${TIME_FORMAT.format(state.measuredAt.atZone(ZoneId.systemDefault()))}",
        style = MaterialTheme.typography.bodyMedium,
        color = LuxTheme.colors.success,
    )
    DaylightHint(state.lux)
    LuxSecondaryButton(text = "Đo lại", onClick = onRemeasure, enabled = isEnabled, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LuxValue(value: Double?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = value?.let(::formatLux) ?: "—", style = MaterialTheme.typography.displaySmall)
        Text(
            text = "lux · số đo tương đối",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
    }
}

@Composable
private fun DaylightHint(value: Double?) {
    if (value != null && value > DAYLIGHT_HINT_LUX) {
        Text(
            text = "Số đo cao, có thể đang đo ban ngày.",
            style = MaterialTheme.typography.bodySmall,
            color = LuxTheme.colors.warning,
        )
    }
}
