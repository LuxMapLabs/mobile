package com.luxmap.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Danger600
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.Success600

// Thresholds per the 2026-10-01 survey UI design spec §3 - not pulled from an existing document
// (Design System v2.0 §6.9 defers the exact numbers to "đặc tả kỹ thuật cấu hình"), confirmed
// with the project owner during that spec's review.
private const val GOOD_ACCURACY_METERS = 10f
private const val WEAK_ACCURACY_METERS = 30f

fun gpsAccuracyLabel(accuracyMeters: Float?): String =
    when {
        accuracyMeters == null -> "Không có vị trí hợp lệ"
        accuracyMeters <= GOOD_ACCURACY_METERS -> "GPS tốt · ±${accuracyMeters.toInt()} m"
        accuracyMeters <= WEAK_ACCURACY_METERS -> "GPS yếu · ±${accuracyMeters.toInt()} m"
        else -> "Không có vị trí hợp lệ"
    }

@Composable
fun GpsAccuracyIndicator(
    accuracyMeters: Float?,
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            accuracyMeters == null || accuracyMeters > WEAK_ACCURACY_METERS -> Danger600
            accuracyMeters <= GOOD_ACCURACY_METERS -> Success600
            else -> Gray500
        }
    Text(
        text = gpsAccuracyLabel(accuracyMeters),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier,
    )
}
