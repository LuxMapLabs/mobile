package com.luxmap.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing

private val ButtonIconSize = 20.dp

@Composable
fun LuxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        shape = MaterialTheme.shapes.medium,
        border =
            BorderStroke(
                1.dp,
                if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
            ),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = LuxTheme.colors.disabledContent,
            ),
    ) {
        ButtonContent(text = text, leadingIcon = leadingIcon)
    }
}

@Composable
fun LuxTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        shape = MaterialTheme.shapes.medium,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = LuxTheme.colors.disabledContent,
            ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LuxDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = Dimens.minTouchTarget),
            shape = MaterialTheme.shapes.medium,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = LuxTheme.colors.disabledContainer,
                    disabledContentColor = LuxTheme.colors.disabledContent,
                ),
        ) {
            ButtonContent(text = text, leadingIcon = leadingIcon)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = Dimens.minTouchTarget),
            shape = MaterialTheme.shapes.medium,
            border =
                BorderStroke(
                    1.dp,
                    if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                ),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = LuxTheme.colors.disabledContent,
                ),
        ) {
            ButtonContent(text = text, leadingIcon = leadingIcon)
        }
    }
}

@Composable
private fun RowScope.ButtonContent(
    text: String,
    leadingIcon: ImageVector?,
) {
    if (leadingIcon != null) {
        Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
        Spacer(Modifier.width(Spacing.sm))
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

@LuxPreviews
@Composable
private fun LuxButtonsPreview() {
    LuxMapTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LuxSecondaryButton(text = "Chỉ đường", onClick = {}, leadingIcon = Icons.Rounded.Navigation)
            LuxSecondaryButton(text = "Chỉ đường", onClick = {}, enabled = false)
            LuxTextButton(text = "Xem thêm", onClick = {})
            LuxDestructiveButton(text = "Đăng xuất", onClick = {}, leadingIcon = Icons.AutoMirrored.Rounded.Logout)
            LuxDestructiveButton(text = "Yêu cầu hủy", onClick = {}, filled = false)
        }
    }
}
