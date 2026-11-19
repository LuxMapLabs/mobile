package com.luxmap.feature.workorder.ui.detail.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxListItem
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.polegps.data.AttachmentStatus
import com.luxmap.feature.workorder.polegps.data.MeasurementSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.data.PoleReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
internal fun PoleGpsSection(
    attachments: List<PoleGpsAttachment>,
    references: List<PoleReference>,
    measurements: List<PoleMeasurement>,
    canCapture: Boolean,
    showReport: Boolean,
    reportNote: String,
    locked: Boolean,
    onOpenPdf: (String) -> Unit,
    onMeasurePole: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onAttachmentFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDownload by remember { mutableStateOf<PoleGpsAttachment?>(null) }
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            val localPath = pendingDownload?.localPath
            pendingDownload = null
            if (uri != null && localPath != null) {
                scope.launch {
                    val saved =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                checkNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                                    File(localPath).inputStream().use { it.copyTo(output) }
                                }
                            }.isSuccess
                        }
                    val message = if (saved) "Đã lưu file vào thiết bị" else "Không lưu được file"
                    onAttachmentFeedback(message)
                }
            }
        }
    val referenceIds = references.map { it.poleId }.toSet()
    val measuredIds =
        measurements.filter { it.source == MeasurementSource.DEVICE }
            .map { it.poleId }.toSet().intersect(referenceIds)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CountedSectionHeader(title = "Tệp đính kèm", count = "${attachments.size} file")
            ListCard {
                attachments.forEachIndexed { index, attachment ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PdfAttachmentItem(
                        attachment = attachment,
                        onOpen = { onOpenPdf(attachment.attachmentId) },
                        onRetry = { onRetryAttachment(attachment.attachmentId) },
                        onDownload = {
                            pendingDownload = attachment
                            saveLauncher.launch(attachment.fileName)
                        },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CountedSectionHeader(title = "Cột đèn", count = "${measuredIds.size}/${references.size} đã đo trên máy")
            if (measurements.any { it.source == MeasurementSource.SERVER }) {
                Text(
                    "Báo cáo mới chỉ gửi các cột đã chụp ảnh trên thiết bị này. " +
                        "Cột đã báo trước cần chụp lại để gửi lại.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ListCard {
                references.forEachIndexed { index, reference ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PoleGpsItem(
                        reference = reference,
                        measurement = measurements.firstOrNull { it.poleId == reference.poleId },
                        enabled = canCapture,
                        onClick = { onMeasurePole(reference.poleId) },
                    )
                }
            }
        }
        if (showReport) {
            ReportNoteField(
                title = "Báo cáo",
                value = reportNote,
                onValueChange = onReportNoteChanged,
                placeholder = "VD: Đã đo xong các cột, 1 cột chưa bắt được tín hiệu GPS",
                locked = locked,
            )
        }
    }
}

@Composable
private fun ListCard(content: @Composable () -> Unit) {
    LuxCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
        content()
    }
}

@Composable
private fun PdfAttachmentItem(
    attachment: PoleGpsAttachment,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
) {
    val (supporting, onTap) =
        when (attachment.status) {
            AttachmentStatus.READY -> "Chạm để xem" to onOpen
            AttachmentStatus.FAILED -> "Tải lỗi. Chạm để thử lại." to onRetry
            AttachmentStatus.DOWNLOADING -> "Đang tải…" to null
            AttachmentStatus.PENDING -> "Chờ tải" to null
        }
    LuxListItem(
        headline = attachment.fileName,
        supporting = supporting,
        leadingIcon = Icons.Filled.Description,
        onClick = onTap,
        showChevron = false,
        trailing =
            if (attachment.status == AttachmentStatus.READY) {
                {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Tải file ${attachment.fileName} về máy",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                null
            },
    )
}

@Composable
private fun PoleGpsItem(
    reference: PoleReference,
    measurement: PoleMeasurement?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val oldPosition =
        if (reference.lat != null && reference.lng != null) {
            "%.6f, %.6f".format(Locale.ROOT, reference.lat, reference.lng)
        } else {
            "Chưa có"
        }
    LuxListItem(
        headline = reference.poleId,
        supporting =
            measurement?.let {
                "GPS đã ghi nhận: %.6f, %.6f\nSai số GPS: ±%.1f m\nThời gian mẫu: %s"
                    .format(Locale.ROOT, it.lat, it.lng, it.accuracyM, it.capturedAt)
            } ?: "GPS cũ: $oldPosition",
        leadingIcon = Icons.Filled.LocationOn,
        onClick = if (enabled) onClick else null,
        trailing = {
            when {
                measurement?.source == MeasurementSource.DEVICE ->
                    StatusBadge(text = "Đã đo", colors = WorkOrderStatus.VERIFIED.badgeColors())
                measurement?.source == MeasurementSource.SERVER ->
                    StatusBadge(text = "Đã báo trước", colors = WorkOrderStatus.OPEN.badgeColors())
                !enabled -> StatusBadge(text = "Chưa bắt đầu", colors = WorkOrderStatus.OPEN.badgeColors())
                else -> StatusBadge(text = "Chưa chụp", colors = WorkOrderStatus.OPEN.badgeColors())
            }
        },
    )
}
