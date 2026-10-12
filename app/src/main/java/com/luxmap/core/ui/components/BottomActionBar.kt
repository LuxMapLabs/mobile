package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.luxmap.core.theme.ScreenMargin
import com.luxmap.core.theme.Spacing

// §3 BottomActionBar: a bottom-pinned hint + one primary CTA, used by screens where the main
// action can be locked and the reason must stay visible right above the button, not as a toast.
@Composable
fun BottomActionBar(
    ctaText: String,
    onCtaClick: () -> Unit,
    ctaEnabled: Boolean,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md, start = ScreenMargin, end = ScreenMargin, bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(bottom = Spacing.sm)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton(
                text = ctaText,
                onClick = onCtaClick,
                enabled = ctaEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
