package com.luxmap.feature.assetimport.ui.triplist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton

@Composable
fun TripListRoute(
    tripId: String,
    onBack: () -> Unit,
    onCaptureMore: (String) -> Unit,
    onEndTrip: (String) -> Unit,
    viewModel: TripListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is TripListUiState.Loading ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            is TripListUiState.Error -> {
                TripListHeader(areaLabel = "", startedAtLabel = "", count = 0, onBack = onBack)
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(state.message)
                }
            }
            is TripListUiState.Empty -> {
                TripListHeader(
                    areaLabel = state.areaLabel,
                    startedAtLabel = state.startedAtLabel,
                    count = 0,
                    onBack = onBack,
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    TripListEmptyCard()
                }
                TripListBottomBar(canEndTrip = false, onCaptureMore = { onCaptureMore(tripId) }, onEndTrip = {})
            }
            is TripListUiState.Success -> {
                TripListHeader(
                    areaLabel = state.areaLabel,
                    startedAtLabel = state.startedAtLabel,
                    count = state.poles.size,
                    onBack = onBack,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.poles, key = { it.poleEntryId }) { pole -> TripPoleCard(pole) }
                }
                TripListBottomBar(
                    canEndTrip = true,
                    onCaptureMore = { onCaptureMore(tripId) },
                    onEndTrip = { onEndTrip(tripId) },
                )
            }
        }
    }
}

@Composable
private fun TripListHeader(
    areaLabel: String,
    startedAtLabel: String,
    count: Int,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient)
                .statusBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
            }
            Column {
                Text(areaLabel, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                Text("Chuyến đang chạy", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TripStatChip(label = "Đã ghi nhận", value = "$count cột", modifier = Modifier.weight(1f))
            TripStatChip(label = "Bắt đầu lúc", value = startedAtLabel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TripStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.control))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TripPoleCard(pole: TripPoleItem) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AsyncImage(
            model = pole.photoFilePath,
            contentDescription = "Ảnh cột #${pole.seq}",
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(Radius.control)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    "#${pole.seq}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(pole.timeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (pole.manuallyAdjusted) {
                    Text(
                        "Chỉnh tay",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8A5A00),
                        modifier =
                            Modifier.background(Color(0xFFFFF3DC), RoundedCornerShape(Radius.badge))
                                .padding(horizontal = Spacing.xs, vertical = 1.dp),
                    )
                }
            }
            Text(pole.note, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (!pole.hasCoord) {
                    Icon(
                        Icons.Filled.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                }
                val coordColor =
                    if (pole.hasCoord) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                Text(pole.coordLabel, fontSize = 12.sp, color = coordColor)
            }
        }
    }
}

@Composable
private fun TripListEmptyCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sheet))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier =
                Modifier.size(
                    64.dp,
                ).clip(RoundedCornerShape(Radius.block)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text("Chưa ghi nhận cột nào", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Bấm \"Chụp tiếp\" để ghi nhận cột đầu tiên trên tuyến.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TripListBottomBar(
    canEndTrip: Boolean,
    onCaptureMore: () -> Unit,
    onEndTrip: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedButton(
            onClick = onCaptureMore,
            modifier = Modifier.weight(1f).heightIn(min = PrimaryButtonHeight),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(" Chụp tiếp")
        }
        PrimaryButton(
            text = "Kết thúc chuyến",
            onClick = onEndTrip,
            enabled = canEndTrip,
            trailingIcon = Icons.Filled.Flag,
            modifier = Modifier.weight(1.3f),
        )
    }
}
