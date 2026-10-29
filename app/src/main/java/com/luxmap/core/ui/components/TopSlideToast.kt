package com.luxmap.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import kotlinx.coroutines.delay

// Matches the Khao Sat v4 mock's toastDown pattern (dark rounded card, green checkmark circle,
// slides in from above the content) - used where a plain Material Snackbar at the bottom would
// be too easy to miss right after a bottom-sheet action (CLAUDE.md 2026-10-29).
private const val AUTO_DISMISS_MS = 3200L

@Stable
class TopToastState {
    var currentMessage by mutableStateOf<String?>(null)
        private set

    // Mirrors SnackbarHostState.showSnackbar's usage pattern (scope.launch { toastState.show(...) })
    // rather than a fire-and-forget call, so a caller can tell when the toast has gone away.
    suspend fun show(message: String) {
        currentMessage = message
        delay(AUTO_DISMISS_MS)
        if (currentMessage == message) currentMessage = null
    }
}

@Composable
fun TopSlideToast(
    state: TopToastState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.currentMessage != null,
        enter = slideInVertically(animationSpec = tween(250)) { -it } + fadeIn(),
        exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(Radius.card))
                    .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(Color(0xFF059669), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Color.White)
            }
            Text(text = state.currentMessage.orEmpty(), style = BodyStrongTextStyle, color = Color.White)
        }
    }
}
