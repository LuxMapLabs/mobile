package com.luxmap.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Spacing

private val LoadingIndicatorSize = 18.dp
private val LoadingIndicatorStrokeWidth = 2.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    trailingIcon: ImageVector? = null,
    branded: Boolean = false,
) {
    val brand = LuxTheme.brand
    val container = if (branded) brand.lumen else MaterialTheme.colorScheme.primary
    val content = if (branded) brand.onLumen else MaterialTheme.colorScheme.onPrimary
    Button(
        onClick = { if (!isLoading) onClick() },
        enabled = enabled,
        modifier =
            modifier
                .heightIn(min = PrimaryButtonHeight)
                .semantics { if (isLoading) stateDescription = "Đang xử lý" },
        shape = MaterialTheme.shapes.medium,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = LuxTheme.colors.disabledContainer,
                disabledContentColor = LuxTheme.colors.disabledContent,
            ),
        border = if (branded && enabled) BorderStroke(1.dp, brand.onLumen) else null,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
        if (isLoading) {
            Spacer(Modifier.width(Spacing.sm))
            CircularProgressIndicator(
                modifier = Modifier.size(LoadingIndicatorSize),
                color = content,
                strokeWidth = LoadingIndicatorStrokeWidth,
            )
        } else if (trailingIcon != null) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(imageVector = trailingIcon, contentDescription = null)
        }
    }
}

@LuxPreviews
@Composable
private fun PrimaryButtonPreview() {
    LuxMapTheme {
        Column {
            PrimaryButton(text = "Bắt đầu", onClick = {})
            PrimaryButton(text = "Đang nộp…", onClick = {}, isLoading = true)
            PrimaryButton(text = "Hoàn thành lệnh", onClick = {}, enabled = false)
        }
    }
}
