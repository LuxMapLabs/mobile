package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.feature.workorder.data.WorkOrderDetail
import java.time.LocalDate

enum class WorkOrderMessageTone { ERROR, WARNING, NEUTRAL, INFO }

data class WorkOrderMessage(
    val title: String,
    val body: String?,
    val tone: WorkOrderMessageTone,
)

data class WorkOrderNote(
    val label: String,
    val text: String,
)

data class WorkOrderMessages(
    val prominent: WorkOrderMessage?,
    val notes: List<WorkOrderNote>,
    val showOffline: Boolean,
)

fun workOrderMessages(
    detail: WorkOrderDetail,
    hasActiveLocalCompletion: Boolean,
    syncStatus: SyncStatus?,
    isOnline: Boolean,
    today: LocalDate,
): WorkOrderMessages {
    val status = workOrderStatusFromWire(detail.woStatus)
    val isPastSchedule = workOrderScheduleWindow(detail.scheduledDate, today).isOverdueToComplete
    val candidates = mutableListOf<WorkOrderMessage>()
    if (isPastSchedule && status == WorkOrderStatus.IN_PROGRESS) {
        candidates +=
            WorkOrderMessage(
                title = "Quá hạn nộp báo cáo",
                body = "Biểu mẫu đã khoá. Liên hệ Kỹ sư bảo trì.",
                tone = WorkOrderMessageTone.ERROR,
            )
    }
    if (isPastSchedule && status == WorkOrderStatus.ASSIGNED) {
        candidates +=
            WorkOrderMessage(
                title = "Quá hạn bắt đầu",
                body = "Liên hệ Kỹ sư bảo trì để được giao lại.",
                tone = WorkOrderMessageTone.ERROR,
            )
    }
    val reviewNote = detail.reviewNote?.takeIf { it.isNotBlank() }
    if (reviewNote != null && status == WorkOrderStatus.IN_PROGRESS) {
        candidates +=
            WorkOrderMessage(
                title = "Kỹ sư bảo trì yêu cầu bổ sung",
                body = reviewNote,
                tone = WorkOrderMessageTone.WARNING,
            )
    }
    when (detail.cancellationRequestStatus) {
        "rejected" ->
            candidates +=
                WorkOrderMessage(
                    title = "Yêu cầu hủy bị từ chối",
                    body = detail.cancellationRejectNote?.takeIf { it.isNotBlank() },
                    tone = WorkOrderMessageTone.WARNING,
                )
        "pending" ->
            candidates +=
                WorkOrderMessage(
                    title = "Đã gửi yêu cầu hủy",
                    body = "Đang chờ Kỹ sư bảo trì duyệt. Chưa thể bắt đầu lệnh.",
                    tone = WorkOrderMessageTone.NEUTRAL,
                )
    }
    if (hasActiveLocalCompletion || status == WorkOrderStatus.DONE) {
        candidates += submittedMessage(syncStatus)
    }

    val prominent = candidates.firstOrNull()
    val notes = mutableListOf<WorkOrderNote>()
    candidates.drop(1).forEach { message ->
        notes += WorkOrderNote(label = message.title, text = message.body.orEmpty())
    }
    if (reviewNote != null && status != WorkOrderStatus.IN_PROGRESS) {
        notes += WorkOrderNote(label = "Ghi chú duyệt", text = reviewNote)
    }
    detail.note?.takeIf { it.isNotBlank() }?.let {
        notes += WorkOrderNote(label = "Ghi chú giao việc", text = it)
    }
    return WorkOrderMessages(prominent = prominent, notes = notes, showOffline = !isOnline)
}

private fun submittedMessage(syncStatus: SyncStatus?): WorkOrderMessage =
    when (syncStatus) {
        null, SyncStatus.DONE ->
            WorkOrderMessage(
                title = "Đã nộp báo cáo",
                body = "Đang chờ Kỹ sư bảo trì duyệt.",
                tone = WorkOrderMessageTone.INFO,
            )
        SyncStatus.FAILED, SyncStatus.CONFLICT ->
            WorkOrderMessage(
                title = "Đã nộp báo cáo",
                body = "${syncStatus.label()}. Báo cáo vẫn được lưu trên thiết bị.",
                tone = WorkOrderMessageTone.WARNING,
            )
        SyncStatus.QUEUED_OFFLINE, SyncStatus.QUEUED_ONLINE, SyncStatus.SYNCING ->
            WorkOrderMessage(
                title = "Đã nộp báo cáo",
                body = "${syncStatus.label()}. Kỹ sư bảo trì sẽ duyệt sau khi đồng bộ.",
                tone = WorkOrderMessageTone.INFO,
            )
    }
