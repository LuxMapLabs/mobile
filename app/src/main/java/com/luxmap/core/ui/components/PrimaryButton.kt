package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Bg
import com.luxmap.core.theme.Navy

// Nút bấm tối thiểu 44dp chiều cao (design tokens CLAUDE.md).
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
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Bg),
    ) {
        Text(text = text)
    }
}
