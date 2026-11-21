package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.workorder.data.WorkOrderDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 11, 16)

private fun detail(
    woStatus: String = "assigned",
    scheduledDate: String? = null,
    reviewNote: String? = null,
    note: String? = null,
    cancellation: String = "none",
    rejectNote: String? = null,
) = WorkOrderDetail(
    workOrderId = "WO-1",
    title = "Sửa đèn tuyến A",
    woStatus = woStatus,
    taskKind = "repair",
    dueDate = null,
    scheduledDate = scheduledDate,
    note = note,
    reviewNote = reviewNote,
    allowedActions = emptyList(),
    cancellationRequestStatus = cancellation,
    cancellationRejectNote = rejectNote,
    faults = emptyList(),
)

private fun messages(
    detail: WorkOrderDetail,
    submitted: Boolean = false,
    syncStatus: SyncStatus? = null,
    isOnline: Boolean = true,
) = workOrderMessages(detail, submitted, syncStatus, isOnline, TODAY)

class WorkOrderMessagesTest {
    @Test
    fun `a plain work order has no prominent message and no notes`() {
        val result = messages(detail())
        assertNull(result.prominent)
        assertTrue(result.notes.isEmpty())
        assertFalse(result.showOffline)
    }

    @Test
    fun `an in progress order past its scheduled date shows the locked form as an error`() {
        val result = messages(detail(woStatus = "in_progress", scheduledDate = "2026-11-15"))
        assertEquals("Quá hạn nộp báo cáo", result.prominent?.title)
        assertEquals(WorkOrderMessageTone.ERROR, result.prominent?.tone)
        assertEquals("Biểu mẫu đã khoá. Liên hệ Quản lý.", result.prominent?.body)
    }

    @Test
    fun `an assigned order past its scheduled date shows overdue to start`() {
        val result = messages(detail(woStatus = "assigned", scheduledDate = "2026-11-15"))
        assertEquals("Quá hạn bắt đầu", result.prominent?.title)
        assertEquals(WorkOrderMessageTone.ERROR, result.prominent?.tone)
        assertEquals("Liên hệ Quản lý để được giao lại.", result.prominent?.body)
    }

    @Test
    fun `a returned order shows the maintenance engineer request as a warning`() {
        val result = messages(detail(woStatus = "in_progress", reviewNote = "Chụp lại ảnh chóa đèn"))
        assertEquals("Quản lý yêu cầu bổ sung", result.prominent?.title)
        assertEquals("Chụp lại ảnh chóa đèn", result.prominent?.body)
        assertEquals(WorkOrderMessageTone.WARNING, result.prominent?.tone)
    }

    @Test
    fun `a rejected cancellation shows the reject note as a warning`() {
        val result = messages(detail(cancellation = "rejected", rejectNote = "Cần làm trong tuần này"))
        assertEquals("Yêu cầu hủy bị từ chối", result.prominent?.title)
        assertEquals("Cần làm trong tuần này", result.prominent?.body)
    }

    @Test
    fun `a pending cancellation is neutral and explains start is blocked`() {
        val result = messages(detail(cancellation = "pending"))
        assertEquals(WorkOrderMessageTone.NEUTRAL, result.prominent?.tone)
        assertTrue(result.prominent!!.body!!.contains("Chưa thể bắt đầu"))
    }

    @Test
    fun `a submitted order waiting to sync says review happens after sync`() {
        val result =
            messages(detail(woStatus = "in_progress"), submitted = true, syncStatus = SyncStatus.QUEUED_OFFLINE)
        assertEquals("Đã nộp báo cáo", result.prominent?.title)
        assertEquals("Chờ mạng. Quản lý sẽ duyệt sau khi đồng bộ.", result.prominent?.body)
        assertEquals(WorkOrderMessageTone.INFO, result.prominent?.tone)
    }

    @Test
    fun `a synced or server done order only says it is waiting for review`() {
        val synced = messages(detail(woStatus = "in_progress"), submitted = true, syncStatus = SyncStatus.DONE)
        val done = messages(detail(woStatus = "done"))
        assertEquals("Đang chờ Quản lý duyệt.", synced.prominent?.body)
        assertEquals("Đang chờ Quản lý duyệt.", done.prominent?.body)
    }

    @Test
    fun `a failed sync after submitting is a warning that keeps the report on the device`() {
        val result = messages(detail(woStatus = "in_progress"), submitted = true, syncStatus = SyncStatus.FAILED)
        assertEquals(WorkOrderMessageTone.WARNING, result.prominent?.tone)
        assertTrue(result.prominent!!.body!!.contains("vẫn được lưu trên thiết bị"))
    }

    @Test
    fun `only the highest priority message is prominent and the rest move to notes`() {
        val result =
            messages(
                detail(
                    woStatus = "in_progress",
                    scheduledDate = "2026-11-15",
                    reviewNote = "Bổ sung ảnh",
                    note = "Ưu tiên trước mưa",
                ),
            )
        assertEquals("Quá hạn nộp báo cáo", result.prominent?.title)
        assertEquals(listOf("Quản lý yêu cầu bổ sung", "Ghi chú giao việc"), result.notes.map { it.label })
    }

    @Test
    fun `a review note on an order that is no longer in progress is only a note`() {
        val result = messages(detail(woStatus = "verified", reviewNote = "Đạt yêu cầu"))
        assertNull(result.prominent)
        assertEquals(listOf("Ghi chú duyệt"), result.notes.map { it.label })
    }

    @Test
    fun `being offline is reported separately and never replaces the business message`() {
        val result = messages(detail(cancellation = "pending"), isOnline = false)
        assertTrue(result.showOffline)
        assertEquals("Đã gửi yêu cầu hủy", result.prominent?.title)
    }

    @Test
    fun `no message mentions Manager or an internal review gate`() {
        val all =
            listOf(
                messages(detail(woStatus = "in_progress", scheduledDate = "2026-11-15", reviewNote = "x")),
                messages(detail(woStatus = "assigned", scheduledDate = "2026-11-15", cancellation = "pending")),
                messages(detail(woStatus = "done", cancellation = "rejected", rejectNote = "y", note = "z")),
            )
        val text =
            all.joinToString(" ") { result ->
                listOfNotNull(result.prominent?.title, result.prominent?.body).joinToString(" ") +
                    result.notes.joinToString(" ") { it.label + " " + it.text }
            }
        assertFalse(text.contains("Manager"))
        assertFalse(text.contains("cổng"))
    }
}
