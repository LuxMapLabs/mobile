package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.ScreenMargin
import com.luxmap.core.theme.Spacing

// §3 BottomActionBar: a bottom-pinned hint + one primary CTA, used by screens where the main
// action can be locked and the reason must stay visible right above the button, not as a toast.
// pinned = false drops the nav-bar inset, top divider, and screen margin for a screen that embeds
// this inline instead of as a real Scaffold(bottomBar = …) - its only consumer today (F03) keeps
// the surrounding list's own horizontal padding instead of adding a second one on top of it.
// progressText is a separate, neutral-colored line (e.g. "3/5 đã có kết quả") for a screen that
// wants to show ongoing progress, not a blocking reason - unlike hint, it is never styled as an
// error. ctaIcon is optional (defaults to null, no icon) so existing callers are unaffected.
@Composable
fun BottomActionBar(
    ctaText: String,
    onCtaClick: () -> Unit,
    ctaEnabled: Boolean,
    hint: String? = null,
    hintColor: Color? = null,
    showHintIcon: Boolean = false,
    progressText: String? = null,
    progressSegments: List<Boolean>? = null,
    ctaIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
    pinned: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().let { if (pinned) it.navigationBarsPadding() else it }) {
        if (pinned) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = Spacing.md,
                        start = if (pinned) ScreenMargin else 0.dp,
                        end = if (pinned) ScreenMargin else 0.dp,
                        bottom = if (pinned) Spacing.lg else 0.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!progressSegments.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(6.dp).padding(bottom = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    progressSegments.forEach { done ->
                        val color = if (done) Navy700 else MaterialTheme.colorScheme.outline
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .background(color, RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
            if (progressText != null) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            if (hint != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier =
                        Modifier
                            .padding(bottom = Spacing.sm)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    if (showHintIcon) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = hintColor ?: MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = hintColor ?: MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            PrimaryButton(
                text = ctaText,
                onClick = onCtaClick,
                enabled = ctaEnabled,
                trailingIcon = ctaIcon,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
