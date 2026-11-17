package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luxmap.core.theme.Spacing
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import java.io.File

@Composable
internal fun CompletionEvidenceSection(
    title: String,
    required: Boolean,
    photos: List<LocalWorkOrderEvidenceEntity>,
    editable: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CompletionFieldHeader(title = title, required = required)
        if (photos.isNotEmpty()) Text("${photos.size} ảnh", style = MaterialTheme.typography.bodyMedium)
        photos.forEachIndexed { index, photo ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AsyncImage(
                    model = File(photo.filePath),
                    contentDescription = "Xem ảnh ${index + 1}",
                    modifier = Modifier.size(64.dp).clickable(role = Role.Button) { previewId = photo.clientOpId },
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text =
                        when (photo.uploadStatus) {
                            "synced" -> "Đã đồng bộ"
                            "pending" -> "Chờ đồng bộ"
                            "syncing" -> "Đang tải lên"
                            "retryable_error" -> "Tải lỗi. Bạn có thể thử lại."
                            "replaceable_error" -> "Cần thay ảnh"
                            "failed" -> "Không tải được ảnh"
                            "conflict" -> "Xung đột đồng bộ"
                            else -> "Ảnh nháp"
                        },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (editable && photo.uploadStatus == "draft") {
                    TextButton(onClick = { onRemove(photo.clientOpId) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Bỏ ảnh")
                    }
                }
            }
        }
        if (editable) {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Thêm ảnh")
            }
        }
    }
    photos.firstOrNull { it.clientOpId == previewId }?.let { photo ->
        AlertDialog(
            onDismissRequest = { previewId = null },
            title = { Text(title) },
            text = {
                AsyncImage(
                    model = File(photo.filePath),
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentScale = ContentScale.Fit,
                )
            },
            confirmButton = { TextButton(onClick = { previewId = null }) { Text("Đóng") } },
        )
    }
}
