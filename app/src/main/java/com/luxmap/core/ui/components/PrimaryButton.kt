package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Dimens

// Vùng chạm tối thiểu 48dp — một tay, có thể đeo găng tay (mục 1 Design System v2.0).
// Màu lấy từ MaterialTheme.colorScheme để tự đổi đúng theo Light/Dark, không hard-code token màu.
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Text(text = text)
    }
}
