package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.Success600
import java.io.File

private val TileSize = 88.dp

@Composable
fun EvidencePicker(
    hasPhoto: Boolean,
    filePath: String?,
    onTileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (hasPhoto && filePath != null) {
            FilledTile(filePath = filePath, onClick = onTileClick)
        } else {
            AddTile(onClick = onTileClick)
        }
    }
}

// Shows the captured photo itself instead of a plain "Đã chụp" placeholder, so the field engineer
// can tell which pole/angle it was without opening it. Tapping now opens a preview (the caller
// decides what that does, e.g. show a dialog with its own retake button) instead of retaking
// directly - a single tap used to silently discard the photo and reopen the camera.
@Composable
private fun FilledTile(
    filePath: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(TileSize)
                .clickable(onClick = onClick)
                .clip(RoundedCornerShape(Radius.control)),
    ) {
        AsyncImage(
            model = File(filePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
        ) {
            Text(
                text = "Chụp ảnh",
                style = LabelTextStyle,
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Success600,
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(18.dp),
        )
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
                .semantics { contentDescription = "Chụp ảnh" }
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
            imageVector = Icons.Filled.AddAPhoto,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "Chụp ảnh", style = LabelTextStyle)
    }
}
