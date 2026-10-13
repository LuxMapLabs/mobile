package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

private val TileSize = 88.dp

@Composable
fun EvidencePicker(
    hasPhoto: Boolean,
    onTileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (hasPhoto) {
            FilledTile(onClick = onTileClick)
        } else {
            AddTile(onClick = onTileClick)
        }
    }
}

@Composable
private fun FilledTile(onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .size(TileSize)
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.control)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = "Đã chụp", style = LabelTextStyle)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Column(
        modifier =
            Modifier
                .size(TileSize)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Chụp ảnh sau" }
                .drawBehind {
                    drawRoundRect(
                        color = outlineColor,
                        cornerRadius = CornerRadius(Radius.control.toPx()),
                        style =
                            Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                            ),
                    )
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "Chụp ảnh sau", style = LabelTextStyle)
    }
}
