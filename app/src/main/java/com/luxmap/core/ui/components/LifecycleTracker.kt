package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Spacing

enum class LifecycleStepState { DONE, CURRENT, FUTURE }

data class LifecycleStep(val label: String, val state: LifecycleStepState)

@Composable
fun LifecycleTracker(
    steps: List<LifecycleStep>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            steps.forEachIndexed { index, step ->
                StepDot(index = index, state = step.state)
                if (index != steps.lastIndex) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(connectorColor(step.state)),
                    )
                }
            }
        }
        Row(modifier = Modifier.padding(top = Spacing.xs)) {
            steps.forEach { step ->
                Text(
                    text = step.label,
                    style = LabelTextStyle,
                    textAlign = TextAlign.Center,
                    color =
                        if (step.state == LifecycleStepState.FUTURE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StepDot(
    index: Int,
    state: LifecycleStepState,
) {
    when (state) {
        LifecycleStepState.DONE ->
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(14.dp).testTag("lifecycle_check_$index"),
                )
            }

        LifecycleStepState.CURRENT ->
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )

        LifecycleStepState.FUTURE ->
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
    }
}

@Composable
private fun connectorColor(previousStepState: LifecycleStepState): Color =
    if (previousStepState == LifecycleStepState.DONE) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
