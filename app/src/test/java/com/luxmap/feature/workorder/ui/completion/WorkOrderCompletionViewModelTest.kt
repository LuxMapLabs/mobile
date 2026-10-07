package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

private const val WORK_ORDER_ID = "WO-1"

private fun savedStateHandle() =
    SavedStateHandle(mapOf(WorkOrderCompletionViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

private fun repairDetail(
    faults: List<WorkOrderFaultDetail> = emptyList(),
    reviewNote: String? = null,
) = WorkOrderDetail(
    workOrderId = WORK_ORDER_ID,
    title = "Sửa đèn tuyến A",
    woStatus = "in_progress",
    taskKind = "repair",
    dueDate = null,
    scheduledDate = null,
    note = null,
    reviewNote = reviewNote,
    reportNote = null,
    materialsUsed = null,
    allowedActions = listOf("complete"),
    faults = faults,
)

private fun inspectionDetail(faults: List<WorkOrderFaultDetail>) = repairDetail(faults).copy(taskKind = "inspection")

private fun fault(id: String) =
    WorkOrderFaultDetail(
        faultId = id,
        poleId = null,
        lat = 10.0,
        lng = 106.0,
        faultType = "lamp_out",
        faultStatus = "confirmed",
        severity = "high",
        inspectionOutcome = null,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class WorkOrderCompletionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        detail: WorkOrderDetail?,
        completionRepository: WorkOrderCompletionRepository,
        isOnline: Boolean = true,
    ): WorkOrderCompletionViewModel {
        val detailRepository = mockk<WorkOrderDetailRepository>()
        every { detailRepository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(detail)
        val connectivityObserver = mockk<ConnectivityObserver>()
        every { connectivityObserver.isOnline } returns flowOf(isOnline)
        return WorkOrderCompletionViewModel(
            savedStateHandle(),
            detailRepository,
            completionRepository,
            connectivityObserver,
        )
    }

    @Test
    fun `emits Loading then Empty when the work order does not exist`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = null, completionRepository = completionRepository)

            vm.uiState.test {
                assertEquals(WorkOrderCompletionUiState.Loading, awaitItem())
                assertEquals(WorkOrderCompletionUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `a repair cannot submit until an after photo exists and the note is at least 10 characters`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = repairDetail(), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertFalse(loaded.canSubmit())

                vm.onReportNoteChanged("short")
                assertFalse((awaitItem() as WorkOrderCompletionUiState.Success).canSubmit())
            }
        }

    @Test
    fun `a repair can submit once an after photo exists and the note is long enough`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = WORK_ORDER_ID,
                    kind = "after",
                    filePath = "/data/OP-1.jpg",
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.0,
                    lng = 106.0,
                    uploadStatus = "pending",
                )
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(evidence)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = repairDetail(), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertFalse(loaded.canSubmit())

                vm.onReportNoteChanged("Đã thay bóng đèn mới")
                val withNote = awaitItem() as WorkOrderCompletionUiState.Success
                assertTrue(withNote.canSubmit())
            }
        }

    @Test
    fun `an inspection with no linked faults only needs the note, and sends fault_outcomes as null`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = inspectionDetail(emptyList()), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onReportNoteChanged("Không phát hiện sự cố nào")
                val withNote = awaitItem() as WorkOrderCompletionUiState.Success
                assertTrue(withNote.canSubmit())

                vm.submit()
                dispatcher.scheduler.advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                completionRepository.submitCompletion(
                    workOrderId = WORK_ORDER_ID,
                    taskKind = "inspection",
                    reportNote = "Không phát hiện sự cố nào",
                    materialsUsed = null,
                    faultOutcomes = null,
                )
            }
        }

    @Test
    fun `an inspection with linked faults cannot submit until every fault has an outcome`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val faults = listOf(fault("FAULT-1"), fault("FAULT-2"))
            val vm = viewModel(detail = inspectionDetail(faults), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onReportNoteChanged("Đã kiểm tra tại hiện trường")
                val withNote = awaitItem() as WorkOrderCompletionUiState.Success
                assertFalse(withNote.canSubmit())

                vm.onFaultOutcomeSelected("FAULT-1", "fault_present")
                val oneDone = awaitItem() as WorkOrderCompletionUiState.Success
                assertFalse(oneDone.canSubmit())

                vm.onFaultOutcomeSelected("FAULT-2", "fault_absent")
                val bothDone = awaitItem() as WorkOrderCompletionUiState.Success
                assertTrue(bothDone.canSubmit())

                vm.submit()
                dispatcher.scheduler.advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                completionRepository.submitCompletion(
                    workOrderId = WORK_ORDER_ID,
                    taskKind = "inspection",
                    reportNote = "Đã kiểm tra tại hiện trường",
                    materialsUsed = null,
                    faultOutcomes =
                        listOf(
                            FaultOutcome("FAULT-1", "fault_present"),
                            FaultOutcome("FAULT-2", "fault_absent"),
                        ),
                )
            }
        }

    @Test
    fun `submit writes through the repository even while offline, never blocking on connectivity`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm =
                viewModel(
                    detail = inspectionDetail(emptyList()),
                    completionRepository = completionRepository,
                    isOnline = false,
                )

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onReportNoteChanged("Không phát hiện sự cố nào")
                awaitItem()

                vm.submit()
                dispatcher.scheduler.advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { completionRepository.submitCompletion(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `a non-blank reviewNote prefills reportNote and materialsUsed from the previous submission`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val detail =
                repairDetail(reviewNote = "Chưa đủ ảnh sau, bổ sung thêm")
                    .copy(reportNote = "Đã thay bóng đèn", materialsUsed = "1 bóng LED")
            val vm = viewModel(detail = detail, completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertEquals("Đã thay bóng đèn", loaded.reportNote)
                assertEquals("1 bóng LED", loaded.materialsUsed)
                assertEquals("Chưa đủ ảnh sau, bổ sung thêm", loaded.detail.reviewNote)
            }
        }

    @Test
    fun `syncStatus is null until a local completion row exists`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = repairDetail(), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertNull(loaded.syncStatus())
            }
        }

    @Test
    fun `syncStatus reflects a pending local completion as queued-online or queued-offline`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            val pending =
                LocalWorkOrderCompletionEntity(
                    workOrderId = WORK_ORDER_ID,
                    reportNote = "note",
                    materialsUsed = null,
                    faultOutcomesJson = null,
                    clientOpId = "COMP-1",
                    submitStatus = "pending",
                )
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(pending)
            val vm = viewModel(detail = repairDetail(), completionRepository = completionRepository, isOnline = false)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertEquals(SyncStatus.QUEUED_OFFLINE, loaded.syncStatus())
            }
        }
}
