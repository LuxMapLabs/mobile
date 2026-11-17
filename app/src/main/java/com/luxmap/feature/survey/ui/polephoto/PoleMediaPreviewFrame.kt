package com.luxmap.feature.survey.ui.polephoto

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.PrimaryButton

@Composable
fun PoleMediaPreviewFrame(
    poleId: String,
    isSaving: Boolean,
    retakeLabel: String,
    confirmLabel: String,
    onClose: () -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    warning: String? = null,
    keepRetakeLabelOnOneLine: Boolean = false,
    media: @Composable BoxScope.() -> Unit,
) {
    BackHandler { if (!isSaving) onClose() }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconButton(onClick = onClose, enabled = !isSaving) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Đóng",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Surface(color = LuxTheme.colors.overlayOnMedia, shape = MaterialTheme.shapes.small) {
                Text(
                    text = poleId,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg),
            contentAlignment = Alignment.Center,
            content = media,
        )
        if (warning != null) {
            Text(
                text = warning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.lg)) {
            val stackActions =
                keepRetakeLabelOnOneLine && !retakeLabelFitsHalfWidth(retakeLabel, maxWidth)
            val retake: @Composable (Modifier) -> Unit = {
                LuxSecondaryButton(text = retakeLabel, onClick = onRetake, enabled = !isSaving, modifier = it)
            }
            val confirm: @Composable (Modifier) -> Unit = {
                PrimaryButton(
                    text = if (isSaving) "Đang lưu…" else confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled && !isSaving,
                    isLoading = isSaving,
                    modifier = it,
                )
            }
            if (stackActions) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val actionModifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight)
                    retake(actionModifier)
                    confirm(actionModifier)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    retake(Modifier.weight(1f).fillMaxHeight())
                    confirm(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun retakeLabelFitsHalfWidth(
    label: String,
    availableWidth: Dp,
): Boolean {
    val textWidth = rememberTextMeasurer().measure(label, style = MaterialTheme.typography.labelLarge).size.width
    val padding = ButtonDefaults.ContentPadding
    val direction = LocalLayoutDirection.current
    val buttonPadding = padding.calculateLeftPadding(direction) + padding.calculateRightPadding(direction)
    val halfWidth = (availableWidth - Spacing.sm) / 2
    return with(LocalDensity.current) { textWidth <= (halfWidth - buttonPadding).toPx() }
}
