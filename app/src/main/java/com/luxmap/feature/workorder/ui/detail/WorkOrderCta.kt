package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.Requirement
import java.time.LocalDate

internal data class WorkOrderCtaState(
    val text: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val hint: String? = null,
    val hintColor: Color? = null,
    val progressText: String? = null,
    val progressSegments: List<Boolean>? = null,
    val requiresConfirmation: Boolean = false,
)

internal fun isReturnedForRework(state: WorkOrderDetailUiState.Success): Boolean =
    !state.justSubmitted &&
        (state.localCompletion == null || state.localCompletion.submitStatus == "synced") &&
        workOrderStatusFromWire(state.detail.woStatus) == WorkOrderStatus.IN_PROGRESS &&
        !state.detail.reviewNote.isNullOrBlank()

// Single bottom-pinned CTA slot for every state this screen can be in - matches the F09
// redesign mockup, which always pins exactly one button (start/complete/locked/done) instead of
// the old layout's separate inline buttons for each case.
@Composable
internal fun workOrderCtaState(
    state: WorkOrderDetailUiState.Success,
    onStart: () -> Unit,
    onSubmit: () -> Unit,
    today: LocalDate = LocalDate.now(),
): WorkOrderCtaState? {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val scheduleWindow = workOrderScheduleWindow(detail.scheduledDate, today)
    val submitLock = scheduleSubmitLock(scheduleWindow)
    val canSubmitNow = state.canSubmit() && !state.isSubmitting && submitLock == null
    return when {
        "start" in detail.allowedActions && scheduleWindow.canActOnSchedule &&
            detail.cancellationRequestStatus == "pending" ->
            WorkOrderCtaState(
                text = "Bắt đầu",
                icon = Icons.Filled.Lock,
                enabled = false,
                onClick = {},
                hint = "Đang chờ duyệt yêu cầu hủy",
                hintColor = SyncStatus.QUEUED_ONLINE.badgeColors().text,
            )
        "start" in detail.allowedActions && scheduleWindow.canActOnSchedule ->
            WorkOrderCtaState(
                text = "Bắt đầu",
                icon = Icons.Filled.PlayArrow,
                enabled = !state.isStarting,
                onClick = onStart,
            )
        // Two different reasons the window is closed - too early (unlocksOn still set) vs the
        // work order went overdue without ever being started (unlocksOn cleared, see
        // workOrderScheduleWindow). scheduleWindow.unlocksOn is only null when there is no
        // scheduled_date (canActOnSchedule = true then, never reaching this branch) or when
        // isOverdueToComplete is true, so the two conditions never both apply.
        "start" in detail.allowedActions && scheduleWindow.isOverdueToComplete ->
            WorkOrderCtaState(
                text = "Bắt đầu",
                icon = Icons.Filled.Lock,
                enabled = false,
                onClick = {},
                hint = "Đã khoá do quá hạn",
                hintColor = MaterialTheme.colorScheme.error,
            )
        "start" in detail.allowedActions ->
            WorkOrderCtaState(
                text = "Bắt đầu",
                icon = Icons.Filled.Lock,
                enabled = false,
                onClick = {},
                hint = "Mở vào " + DateFormatUtils.formatPlainDate(scheduleWindow.unlocksOn.toString()),
                hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        "complete" in detail.allowedActions && state.hasActiveLocalCompletion() ->
            WorkOrderCtaState(
                text = "Đã nộp" + (state.syncStatus()?.label()?.let { " — $it" } ?: ""),
                icon = Icons.Filled.HourglassTop,
                enabled = false,
                onClick = {},
            )
        "complete" in detail.allowedActions && kind == WorkOrderTaskKind.REPAIR ->
            WorkOrderCtaState(
                text = "Hoàn thành lệnh",
                icon = if (canSubmitNow) Icons.Filled.Check else Icons.Filled.Lock,
                enabled = canSubmitNow,
                onClick = onSubmit,
                hint = submitLock?.first ?: missingRequirementsHint(repairRequirements(state)),
                hintColor = submitLock?.second ?: LuxTheme.colors.dueToday,
                requiresConfirmation = true,
            )
        "complete" in detail.allowedActions && kind == WorkOrderTaskKind.INSPECTION -> {
            val recordedCount = detail.faults.count { state.faultOutcomes.containsKey(it.faultId) }
            val missingCount = detail.faults.size - recordedCount
            WorkOrderCtaState(
                text = "Nộp báo cáo kiểm tra",
                icon = if (canSubmitNow) Icons.Filled.Send else Icons.Filled.Lock,
                enabled = canSubmitNow,
                onClick = onSubmit,
                hint =
                    submitLock?.first ?: when {
                        !state.hasInspectionPhoto() -> "Cần có ít nhất 1 ảnh hiện trường"
                        missingCount > 0 -> "Còn $missingCount sự cố chưa có kết quả"
                        else -> null
                    },
                hintColor = submitLock?.second ?: LuxTheme.colors.dueToday,
                progressText = "$recordedCount/${detail.faults.size} sự cố đã có kết quả",
                progressSegments = detail.faults.map { state.faultOutcomes.containsKey(it.faultId) },
                requiresConfirmation = true,
            )
        }
        "complete" in detail.allowedActions && kind == WorkOrderTaskKind.POLE_GPS_UPDATE -> {
            val measured = state.poleMeasurements.map { it.poleId }.toSet().size
            val missing = state.poleReferences.size - measured
            WorkOrderCtaState(
                text = "Nộp báo cáo",
                icon = if (canSubmitNow) Icons.Filled.Send else Icons.Filled.Lock,
                enabled = canSubmitNow,
                onClick = onSubmit,
                hint = submitLock?.first ?: if (missing > 0) "Còn $missing cột chưa chụp" else null,
                hintColor = submitLock?.second ?: LuxTheme.colors.dueToday,
                requiresConfirmation = true,
            )
        }
        workOrderStatusFromWire(detail.woStatus) == WorkOrderStatus.DONE && "start" !in detail.allowedActions ->
            WorkOrderCtaState(
                text = "Đã nộp báo cáo",
                icon = Icons.Filled.HourglassTop,
                enabled = false,
                onClick = {},
                hint = "Chờ Kỹ sư bảo trì duyệt",
                hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        else -> null
    }
}

@Composable
private fun scheduleSubmitLock(window: WorkOrderScheduleWindow): Pair<String, Color>? =
    when {
        window.isOverdueToComplete -> "Đã khoá do quá hạn" to MaterialTheme.colorScheme.error
        window.submitOpensOn != null ->
            "Nộp được từ " + DateFormatUtils.formatPlainDate(window.submitOpensOn.toString()) to
                MaterialTheme.colorScheme.onSurfaceVariant
        else -> null
    }

// Same 2 requirements RequirementList shows in the "Nghiệm thu" card, reused here to build the
// "Cần ..." lock hint instead of duplicating the wording in 2 places.
internal fun repairRequirements(state: WorkOrderDetailUiState.Success): List<Requirement> =
    listOf(
        Requirement("Có ít nhất 1 ảnh sau sửa chữa", met = state.localEvidence.any { it.kind == "after" }),
        Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = state.reportNote.trim().length >= 10),
    )

internal fun missingRequirementsHint(requirements: List<Requirement>): String? {
    val missing = requirements.filterNot { it.met }
    if (missing.isEmpty()) return null
    return "Cần " + missing.joinToString(" và ") { it.label.lowercase() }
}
