package com.luxmap.feature.assetimport.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

@Composable
fun CameraArea(
    pendingShot: PendingShot?,
    onShoot: () -> Unit,
    onRetake: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        if (pendingShot == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0B1220)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text("Hướng camera vào toàn thân cột", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    Box(
                        modifier =
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onShoot),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Chụp ảnh", tint = Color(0xFF1F3864))
                    }
                }
            }
        } else {
            AsyncImage(
                model = pendingShot.file,
                contentDescription = "Ảnh cột đèn vừa chụp",
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF6EE7B7),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (pendingShot.lat != null) "GPS ±6 m" else "Chưa có GPS",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier =
                        Modifier.background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(Radius.badge),
                        ).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md)
                        .clip(RoundedCornerShape(Radius.control))
                        .background(Color.White.copy(alpha = 0.92f))
                        .clickable(onClick = onRetake)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    Icons.Filled.Replay,
                    contentDescription = null,
                    tint = Color(0xFF1F3864),
                    modifier = Modifier.size(16.dp),
                )
                Text("Chụp lại", color = Color(0xFF1F3864), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
