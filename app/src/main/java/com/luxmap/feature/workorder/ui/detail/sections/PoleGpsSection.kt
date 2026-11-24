package com.luxmap.feature.workorder.ui.detail.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxListItem
import com.luxmap.core.ui.components.PoleIdentificationDetails
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.polegps.data.AttachmentStatus
import com.luxmap.feature.workorder.polegps.data.CorridorCheck
import com.luxmap.feature.workorder.polegps.data.MeasurementSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.ui.corridorListLabel
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
    polesMissingPhoto: Set<String> = emptySet(),
    reportNote: String,
    locked: Boolean,
    onOpenPdf: (String) -> Unit,
    onMeasurePole: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onAttachmentFeedback: (String) -> Unit,
    onOpenMap: (poleId: String, lat: Double?, lng: Double?, isProposed: Boolean) -> Unit = { _, _, _, _ -> },
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
                    "Cột đã báo trước sẽ được gửi lại với vị trí cũ. " +
                        "Chỉ cần đo lại cột Quản lý yêu cầu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ListCard {
                references.forEachIndexed { index, reference ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val measurement = measurements.firstOrNull { it.poleId == reference.poleId }
                    PoleGpsItem(
                        reference = reference,
                        measurement = measurement,
                        missingPhoto = reference.poleId in polesMissingPhoto,
                        canCapture = canCapture,
                        onOpenMap = {
                            when {
                                measurement != null -> {
                                    onOpenMap(reference.poleId, measurement.lat, measurement.lng, true)
                                }
                                else -> onOpenMap(reference.poleId, reference.lat, reference.lng, false)
                            }
                        },
                        onMeasure = { onMeasurePole(reference.poleId) },
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
    missingPhoto: Boolean,
    canCapture: Boolean,
    onOpenMap: () -> Unit,
    onMeasure: () -> Unit,
) {
    val oldPosition =
        if (reference.lat != null && reference.lng != null) {
            "%.6f, %.6f".format(Locale.ROOT, reference.lat, reference.lng)
        } else {
            "Chưa có"
        }
    Column(
        Modifier.padding(bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        LuxListItem(
            headline = reference.poleId,
            leadingIcon = Icons.Filled.LocationOn,
            onClick = onOpenMap,
            showChevron = false,
            trailing = {
                if (canCapture) {
                    if (measurement == null || missingPhoto) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            StatusBadge(
                                text = if (measurement == null) "Chưa chụp" else "Thiếu ảnh",
                                colors = WorkOrderStatus.OPEN.badgeColors(),
                            )
                            FilledTonalButton(
                                onClick = onMeasure,
                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                                modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Đo GPS", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            val badgeText =
                                if (measurement.source == MeasurementSource.DEVICE) "Đã đo" else "Đã báo trước"
                            val badgeColors =
                                if (measurement.source == MeasurementSource.DEVICE) {
                                    WorkOrderStatus.VERIFIED.badgeColors()
                                } else {
                                    WorkOrderStatus.OPEN.badgeColors()
                                }
                            StatusBadge(text = badgeText, colors = badgeColors)
                            IconButton(
                                onClick = onMeasure,
                                modifier = Modifier.size(Dimens.minTouchTarget),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay,
                                    contentDescription = "Đo lại GPS cột ${reference.poleId}",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                } else {
                    val badgeText =
                        when {
                            measurement != null && missingPhoto -> "Thiếu ảnh"
                            measurement?.source == MeasurementSource.DEVICE -> "Đã đo"
                            measurement?.source == MeasurementSource.SERVER -> "Đã báo trước"
                            else -> "Chưa bắt đầu"
                        }
                    val badgeColors =
                        if (measurement != null && !missingPhoto) {
                            WorkOrderStatus.VERIFIED.badgeColors()
                        } else {
                            WorkOrderStatus.OPEN.badgeColors()
                        }
                    StatusBadge(text = badgeText, colors = badgeColors)
                }
            },
        )
        reference.identification?.let { item ->
            PoleIdentificationDetails(
                poleId = reference.poleId,
                position = reference.position,
                segmentId = item.segmentId,
                note = item.note,
                fixtureType = item.fixtureType,
                lampWatt = item.lampWatt,
                lampCount = item.lampCount,
                fixtureCount = item.fixtureCount,
                nearSensitivePoi = item.nearSensitivePoi,
                lat = measurement?.lat ?: reference.lat,
                lng = measurement?.lng ?: reference.lng,
                onOpenMap = onOpenMap,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        Text(
            "GPS cũ: $oldPosition",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        measurement?.let {
            Text(
                "GPS đã ghi nhận: %.6f, %.6f".format(Locale.ROOT, it.lat, it.lng),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            corridorListLabel(it.corridor)?.let { label ->
                val outside = it.corridor is CorridorCheck.Outside
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (outside) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (outside) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}
