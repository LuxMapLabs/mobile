package com.luxmap.feature.assetimport.ui.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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

@Composable
fun PendingApprovalRoute(
    tripId: String,
    onBackToSurveyHub: () -> Unit,
    viewModel: PendingApprovalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is PendingApprovalUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is PendingApprovalUiState.Error -> Text(state.message, modifier = Modifier.align(Alignment.Center))
            is PendingApprovalUiState.Success ->
                PendingApprovalContent(state = state, onBackToSurveyHub = onBackToSurveyHub)
        }
    }
}

@Composable
private fun PendingApprovalContent(
    state: PendingApprovalUiState.Success,
    onBackToSurveyHub: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SuccessHero(
                areaLabel = state.areaLabel,
                count = state.poles.size,
                submittedAtLabel = state.submittedAtLabel,
            )
        }
        item {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                InfoCard()
                PoleStatusCard(poles = state.poles)
                SegmentCard(firstSeq = state.firstSeq, lastSeq = state.lastSeq)
                OutlinedButton(
                    onClick = onBackToSurveyHub,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight),
                ) {
                    Text("Về Khảo sát")
                }
            }
        }
    }
}

@Composable
private fun SuccessHero(
    areaLabel: String,
    count: Int,
    submittedAtLabel: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(BrandHeroGradient).padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF059669)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Text("Đã nộp chuyến đi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "$areaLabel · $count cột · nộp lúc $submittedAtLabel",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
        )
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radius.badge))
                    .background(Color.White)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                Icons.Filled.HourglassTop,
                contentDescription = null,
                tint = Color(0xFFB45309),
                modifier = Modifier.size(14.dp),
            )
            Text("Chờ duyệt", color = Color(0xFFB45309), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            "Manager duyệt riêng từng cột trên Web GIS, dựa vào ảnh bạn chụp. " +
                "Cột được duyệt mới chính thức lên bản đồ.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PoleStatusCard(poles: List<PendingPoleItem>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Trạng thái từng cột", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("${poles.size} cột chờ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            poles.forEach { pole -> PoleStatusRow(pole) }
        }
    }
}

@Composable
private fun PoleStatusRow(pole: PendingPoleItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AsyncImage(
            model = pole.photoFilePath,
            contentDescription = "Ảnh cột #${pole.seq}",
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.control)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "#${pole.seq}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(pole.note, fontSize = 13.sp, maxLines = 1)
        }
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radius.badge))
                    .background(Color(0xFFFFF3DC))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                Icons.Filled.HourglassTop,
                contentDescription = null,
                tint = Color(0xFF8A5A00),
                modifier = Modifier.size(14.dp),
            )
            Text("Chờ duyệt", color = Color(0xFF8A5A00), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SegmentCard(
    firstSeq: Int,
    lastSeq: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Filled.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Đoạn đường (segment)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SegmentChip("Đầu #$firstSeq")
            Box(modifier = Modifier.weight(1f))
            SegmentChip("Cuối #$lastSeq")
        }
        Text(
            "Xác định theo cột đầu và cột cuối của chuyến.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SegmentChip(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radius.badge))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
