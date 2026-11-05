package com.luxmap.feature.assetimport.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton

@Composable
fun ReviewSubmitRoute(
    tripId: String,
    onBack: () -> Unit,
    onSubmitted: (String) -> Unit,
    viewModel: ReviewSubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is ReviewSubmitUiState.Loading ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            is ReviewSubmitUiState.Error -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(state.message)
                }
            }
            is ReviewSubmitUiState.Success -> {
                ReviewHeader(
                    count = state.poles.size,
                    tab = state.tab,
                    onBack = onBack,
                    onSelectTab = viewModel::onSelectTab,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item { WarningBanner(state.warningMessages) }
                    if (state.tab == ReviewTab.LIST) {
                        items(state.poles, key = { it.poleEntryId }) { pole ->
                            ReviewPoleCard(pole, onDelete = { viewModel.onDeletePole(pole.poleEntryId) })
                        }
                    } else {
                        item { ReviewMapPlaceholder() }
                    }
                }
                ReviewSubmitBar(
                    canSubmit = state.poles.isNotEmpty() && !state.isSubmitting,
                    isSubmitting = state.isSubmitting,
                    warningCount = state.warningMessages.size,
                    poleCount = state.poles.size,
                    onSubmit = { viewModel.onSubmit { onSubmitted(tripId) } },
                )
            }
        }
    }
}

@Composable
private fun ReviewHeader(
    count: Int,
    tab: ReviewTab,
    onBack: () -> Unit,
    onSelectTab: (ReviewTab) -> Unit,
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
                Text("KV-07 · $count cột", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                Text("Xem lại chuyến", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.control))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ReviewTabButton(
                "Danh sách",
                Icons.AutoMirrored.Filled.ViewList,
                tab == ReviewTab.LIST,
                Modifier.weight(1f),
            ) {
                onSelectTab(ReviewTab.LIST)
            }
            ReviewTabButton("Bản đồ", Icons.Filled.Map, tab == ReviewTab.MAP, Modifier.weight(1f)) {
                onSelectTab(ReviewTab.MAP)
            }
        }
    }
}

@Composable
private fun ReviewTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) Color.White else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else Color.White
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.badge))
                .background(bg)
                .heightIn(min = 36.dp)
                .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(" $label", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WarningBanner(messages: List<String>) {
    if (messages.isEmpty()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(Color(0xFFECFDF5))
                    .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF0F766E))
            Text("Không có cảnh báo", color = Color(0xFF0F766E), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(Color(0xFFFFF8EB))
                .border(1.dp, Color(0xFFFCD9A0), RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFB45309))
            Text(
                "${messages.size} cảnh báo cần xem",
                color = Color(0xFF8A5A00),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        messages.forEach { message -> Text(message, fontSize = 13.sp, color = Color(0xFF5B4210)) }
        Text("Chỉ là nhắc nhở. Bạn vẫn nộp được khi chưa xử lý.", fontSize = 12.sp, color = Color(0xFF8A5A00))
    }
}

@Composable
private fun ReviewPoleCard(
    pole: ReviewPoleItem,
    onDelete: () -> Unit,
) {
    val ringColor = if (pole.warningLabel != null) Color(0xFFFCD9A0) else Color.Transparent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, ringColor, RoundedCornerShape(Radius.card))
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
            }
            Text(pole.note, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val coordColor =
                if (pole.hasCoord) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            Text(pole.coordLabel, fontSize = 12.sp, color = coordColor)
            if (pole.warningLabel != null) {
                Text(
                    pole.warningLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8A5A00),
                    modifier =
                        Modifier.background(Color(0xFFFFF3DC), RoundedCornerShape(Radius.badge))
                            .padding(horizontal = Spacing.xs, vertical = 1.dp),
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Xoá cột",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewMapPlaceholder() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Bản đồ vệ tinh (sẽ thêm sau)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ReviewSubmitBar(
    canSubmit: Boolean,
    isSubmitting: Boolean,
    warningCount: Int,
    poleCount: Int,
    onSubmit: () -> Unit,
) {
    val hint =
        when {
            poleCount == 0 -> "Chưa có cột nào để nộp"
            warningCount > 0 -> "Còn $warningCount cảnh báo · vẫn nộp được"
            else -> "$poleCount cột sẽ được gửi để duyệt"
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        PrimaryButton(
            text = "Nộp chuyến đi",
            onClick = onSubmit,
            enabled = canSubmit,
            isLoading = isSubmitting,
            trailingIcon = Icons.Filled.CloudUpload,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
