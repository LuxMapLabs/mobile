package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.LuxTextButton
import com.luxmap.core.ui.components.PrimaryButton

// The Field Engineer's own label for a pole photo, picked right after the shutter.
enum class Observation(val wireValue: String, val label: String, val icon: ImageVector) {
    ON("on", "Sáng", Icons.Rounded.Lightbulb),
    OFF("off", "Tắt", Icons.Outlined.Lightbulb),
    UNSURE("unsure", "Không rõ", Icons.Rounded.Help),
    ;

    companion object {
        fun fromWire(value: String?): Observation? = entries.firstOrNull { it.wireValue == value }
    }
}

val SKIP_REASONS =
    listOf(
        "Không tìm thấy cột",
        "Bị cây hoặc vật cản che",
        "Không tiếp cận được",
        "Cột đã bị dỡ bỏ",
    )

fun routeTitle(
    poleId: String,
    progress: RouteProgress?,
): String = progress?.let { "Cột ${it.index}/${it.total} · $poleId" } ?: poleId

// Bottom of the photo preview: saving the photo IS picking what the engineer saw, so there is
// no separate "Dùng ảnh này" step and no AI result to wait for.
@Composable
fun ObservationActions(
    isSaving: Boolean,
    retakeLabel: String,
    onObserve: (Observation) -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = if (isSaving) "Đang lưu…" else "Đèn đang thế nào?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Observation.entries.forEach { observation ->
                LuxSecondaryButton(
                    text = observation.label,
                    leadingIcon = observation.icon,
                    onClick = { onObserve(observation) },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).testTag("observe_${observation.wireValue}"),
                )
            }
        }
        LuxTextButton(
            text = retakeLabel,
            onClick = onRetake,
            enabled = !isSaving,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

// Shown in route mode when the camera was closed without a photo: the engineer either retries,
// marks the pole as skipped with a reason, or stops and goes back to the pole list.
@Composable
fun RoutePolePrompt(
    title: String,
    onCapture: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Chưa có ảnh cho cột này.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Chụp cột này", onClick = onCapture, modifier = Modifier.fillMaxWidth())
        LuxSecondaryButton(
            text = "Bỏ qua cột này",
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().testTag("route_skip"),
        )
        LuxTextButton(text = "Dừng, về danh sách cột", onClick = onStop)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipReasonSheet(
    poleId: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
            Text(
                text = "Vì sao bỏ qua $poleId?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm),
            )
            SKIP_REASONS.forEach { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { onPick(reason) }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }
        }
    }
}
