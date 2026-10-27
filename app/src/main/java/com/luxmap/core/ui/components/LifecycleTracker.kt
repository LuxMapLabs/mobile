package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Blue500
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.Spacing

enum class LifecycleStepState { DONE, CURRENT, FUTURE }

data class LifecycleStep(val label: String, val state: LifecycleStepState)

// Thin segmented bar + one "Bước X/5 · <label>" line, matching the F09 v2 mockup's compact
// "Quy trình" row - replaces the earlier step-dots-with-labels-and-connecting-lines look, which
// needed the hero's full width. This component has only one caller (WorkOrderDetailScreen.kt),
// now inside a small summary card instead of the dark hero, so the smaller footprint matters.
@Composable
fun LifecycleTracker(
    steps: List<LifecycleStep>,
    modifier: Modifier = Modifier,
    onDarkHero: Boolean = false,
) {
    val doneColor = if (onDarkHero) Color.White else Navy700
    val currentColor = if (onDarkHero) Color.White.copy(alpha = 0.7f) else Blue500
    val futureColor = if (onDarkHero) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline
    val eyebrowColor = if (onDarkHero) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val activeTextColor = if (onDarkHero) Color.White else MaterialTheme.colorScheme.primary
    val currentIndex = steps.indexOfLast { it.state != LifecycleStepState.FUTURE }.coerceAtLeast(0)
    val currentStep = steps.getOrNull(currentIndex)
    val statusText =
        when {
            currentStep == null -> ""
            currentStep.state == LifecycleStepState.DONE -> "Chờ duyệt · ${currentStep.label}"
            else -> "Bước ${currentIndex + 1}/${steps.size} · ${currentStep.label}"
        }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Quy trình", style = LabelTextStyle, color = eyebrowColor)
            Text(text = statusText, style = BodyStrongTextStyle, color = activeTextColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            steps.forEach { step ->
                val color =
                    when (step.state) {
                        LifecycleStepState.DONE -> doneColor
                        LifecycleStepState.CURRENT -> currentColor
                        LifecycleStepState.FUTURE -> futureColor
                    }
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
}
