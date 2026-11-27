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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LightStatusBarIcons
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.LuxTextButton
import com.luxmap.core.ui.components.PoleIdentificationDetails
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.survey.data.PoleListItem

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
// no separate "Dùng ảnh này" step and no AI result to wait for. Sáng/Tắt are the two big,
// filled answers (gloves, one hand, at night); "Không rõ" is the deliberate third option below.
@Composable
fun ObservationActions(
    isSaving: Boolean,
    retakeLabel: String,
    onObserve: (Observation) -> Unit,
    onRetake: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    fun observe(observation: Observation) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onObserve(observation)
    }
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
            ObservationButton(
                observation = Observation.ON,
                container = LuxTheme.brand.lumen,
                content = LuxTheme.brand.onLumen,
                enabled = !isSaving,
                onClick = ::observe,
                modifier = Modifier.weight(1f),
            )
            ObservationButton(
                observation = Observation.OFF,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurface,
                enabled = !isSaving,
                onClick = ::observe,
                modifier = Modifier.weight(1f),
            )
        }
        LuxSecondaryButton(
            text = Observation.UNSURE.label,
            leadingIcon = Observation.UNSURE.icon,
            onClick = { observe(Observation.UNSURE) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().testTag("observe_${Observation.UNSURE.wireValue}"),
        )
        LuxTextButton(
            text = retakeLabel,
            onClick = onRetake,
            enabled = !isSaving,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ObservationButton(
    observation: Observation,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: (Observation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onClick(observation) },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        modifier = modifier.heightIn(min = 72.dp).testTag("observe_${observation.wireValue}"),
    ) {
        Icon(imageVector = observation.icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Text(
            text = observation.label,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

fun RouteHandoff.message(): String =
    when (outcome) {
        RouteHandoff.SKIPPED -> "Đã bỏ qua $poleId"
        else -> "Đã lưu $poleId · ${Observation.fromWire(outcome)?.label.orEmpty()}"
    }

// Route mode shows this before every camera launch: which pole is next, enough to recognise it
// on the street (segment, note, fixture, map), and that the previous pole was saved. One extra
// tap per pole, traded for not photographing the wrong lamp - the system camera cannot show
// the pole id itself.
@Composable
fun RoutePoleCard(
    poleId: String,
    progress: RouteProgress?,
    pole: PoleListItem?,
    previous: RouteHandoff?,
    cameraWasClosed: Boolean,
    onCapture: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onOpenMap: () -> Unit,
) {
    LightStatusBarIcons()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (progress != null) {
            Text(
                text = "Cột ${progress.index}/${progress.total}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (progress.index - 1).toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (previous != null && !cameraWasClosed) {
            LuxBanner(
                message = previous.message(),
                tone = if (previous.outcome == RouteHandoff.SKIPPED) LuxBannerTone.NEUTRAL else LuxBannerTone.SUCCESS,
                modifier = Modifier.testTag("route_previous"),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        ) {
            Text(
                text = if (cameraWasClosed) "Chưa có ảnh cho cột này" else "Cột tiếp theo",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = poleId,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (pole != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    PoleIdentificationDetails(
                        poleId = pole.poleId,
                        position = pole.position,
                        segmentId = pole.segmentId,
                        note = pole.note,
                        fixtureType = pole.fixtureType,
                        lampWatt = pole.lampWatt,
                        lampCount = pole.lampCount,
                        fixtureCount = pole.fixtureCount,
                        nearSensitivePoi = pole.nearSensitivePoi,
                        lat = pole.lat,
                        lng = pole.lng,
                        onOpenMap = onOpenMap,
                    )
                }
                if (pole.lat != null && pole.lng != null) {
                    LuxSecondaryButton(
                        text = "Xem trên bản đồ",
                        leadingIcon = Icons.Rounded.Map,
                        onClick = onOpenMap,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        PrimaryButton(
            text = "Chụp cột này",
            onClick = onCapture,
            trailingIcon = Icons.Rounded.PhotoCamera,
            branded = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("route_capture"),
        )
        LuxSecondaryButton(
            text = "Bỏ qua cột này",
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().testTag("route_skip"),
        )
        LuxTextButton(
            text = "Dừng, về danh sách cột",
            onClick = onStop,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
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
