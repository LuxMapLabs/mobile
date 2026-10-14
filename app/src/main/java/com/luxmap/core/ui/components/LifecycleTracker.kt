package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val doneColor = MaterialTheme.colorScheme.onSurface
    val futureColor = MaterialTheme.colorScheme.outline
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .drawBehind {
                        val cellWidth = size.width / steps.size
                        val y = size.height / 2
                        for (index in 0 until steps.lastIndex) {
                            val startX = cellWidth * (index + 0.5f)
                            val endX = cellWidth * (index + 1.5f)
                            val color = if (steps[index].state == LifecycleStepState.DONE) doneColor else futureColor
                            drawLine(
                                color = color,
                                start = Offset(startX, y),
                                end = Offset(endX, y),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    },
        ) {
            steps.forEachIndexed { index, step ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    StepDot(index = index, state = step.state)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            steps.forEach { step ->
                Text(
                    text = step.label,
                    style = LabelTextStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (step.state == LifecycleStepState.FUTURE) futureColor else doneColor,
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
