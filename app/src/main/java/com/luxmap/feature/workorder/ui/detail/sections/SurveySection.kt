package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.LuxSectionHeader

@Composable
internal fun SurveySection(
    started: Boolean,
    canStart: Boolean,
    onOpenSurvey: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LuxSectionHeader(title = "Khảo sát tuyến")
        LuxCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "Chụp ảnh hoặc quay video từng cột đèn trên tuyến.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (started) {
                    LuxSecondaryButton(
                        text = "Mở danh sách cột",
                        onClick = onOpenSurvey,
                        leadingIcon = Icons.Rounded.PhotoCamera,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (canStart) {
                    Text(
                        text = "Bấm Bắt đầu ở cuối màn hình để mở danh sách cột cần chụp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
