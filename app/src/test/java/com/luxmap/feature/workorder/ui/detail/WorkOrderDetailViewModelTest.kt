package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PoleListResult
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import com.luxmap.feature.workorder.polegps.data.MeasurementSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.data.PoleReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import java.io.IOException
import java.time.Instant

private const val WORK_ORDER_ID = "WO-1"

private fun savedStateHandle() = SavedStateHandle(mapOf(WorkOrderDetailViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

private fun repairDetail(
    allowedActions: List<String> = listOf("start"),
    faults: List<WorkOrderFaultDetail> = emptyList(),
    reviewNote: String? = null,
) = WorkOrderDetail(
    workOrderId = WORK_ORDER_ID,
    title = "Sửa đèn tuyến A",
    woStatus = "assigned",
    taskKind = "repair",
    dueDate = null,
    scheduledDate = null,
    note = null,
    reviewNote = reviewNote,
    reportNote = null,
    materialsUsed = null,
    allowedActions = allowedActions,
    faults = faults,
)

private fun inspectionDetail(faults: List<WorkOrderFaultDetail>) =
    repairDetail(allowedActions = listOf("complete"), faults = faults).copy(taskKind = "inspection")

private fun poleGpsDetail(poleIds: List<String> = listOf("P-1", "P-2")) =
    repairDetail(allowedActions = listOf("complete")).copy(taskKind = "pole_gps_update", poleIds = poleIds)

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
class WorkOrderDetailViewModelTest {
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
        detailFlow: Flow<WorkOrderDetail?>,
        detailRepository: WorkOrderDetailRepository = mockk(),
        completionRepository: WorkOrderCompletionRepository = mockk(),
        poleGpsRepository: PoleGpsRepository? = null,
        poleListRepository: PoleListRepository? = null,
        isOnline: Boolean = true,
        evidence: LocalWorkOrderEvidenceEntity? = null,
        completion: LocalWorkOrderCompletionEntity? = null,
    ): WorkOrderDetailViewModel {
        every { detailRepository.observeWorkOrderDetail(WORK_ORDER_ID) } returns detailFlow
        every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(evidence)
        every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(completion)
        val resolvedPoleGpsRepository =
            poleGpsRepository ?: mockk<PoleGpsRepository>().also {
                every { it.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
                every { it.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
                coEvery { it.poleReferences(WORK_ORDER_ID) } returns emptyList()
            }
        val resolvedPoleListRepository =
            poleListRepository ?: mockk<PoleListRepository>().also {
                coEvery { it.poles(WORK_ORDER_ID) } returns Result.success(PoleListResult(emptyList(), isStale = false))
            }
        val connectivityObserver = mockk<ConnectivityObserver>()
        every { connectivityObserver.isOnline } returns flowOf(isOnline)
        return WorkOrderDetailViewModel(
            savedStateHandle(),
            detailRepository,
            completionRepository,
            resolvedPoleGpsRepository,
            resolvedPoleListRepository,
            connectivityObserver,
        )
    }

    private fun viewModel(
        detail: WorkOrderDetail?,
        detailRepository: WorkOrderDetailRepository = mockk(),
        completionRepository: WorkOrderCompletionRepository = mockk(),
        poleGpsRepository: PoleGpsRepository? = null,
        poleListRepository: PoleListRepository? = null,
        isOnline: Boolean = true,
        evidence: LocalWorkOrderEvidenceEntity? = null,
        completion: LocalWorkOrderCompletionEntity? = null,
    ) = viewModel(
        flowOf(detail),
        detailRepository,
        completionRepository,
        poleGpsRepository,
        poleListRepository,
        isOnline,
        evidence,
        completion,
    )

    @Test
    fun `emits Loading then Success when the repository has the work order`() =
        runTest {
            val vm = viewModel(detail = repairDetail())

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val success = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals(WORK_ORDER_ID, success.detail.workOrderId)
            }
        }

    @Test
    fun `emits Empty when the repository returns null`() =
        runTest {
            val vm = viewModel(detail = null)

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                assertEquals(WorkOrderDetailUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val vm = viewModel(detailFlow = flow { throw IllegalStateException("network down") })

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val error = awaitItem() as WorkOrderDetailUiState.Error
                assertEquals("network down", error.message)
            }
        }

    @Test
    fun `start sets isStarting immediately, then swaps in the backend's returned detail directly`() =
        runTest {
            val detailRepository = mockk<WorkOrderDetailRepository>()
            coEvery { detailRepository.start(WORK_ORDER_ID) } coAnswers {
                delay(10)
                Result.success(repairDetail(allowedActions = emptyList()))
            }
            val vm =
                viewModel(detail = repairDetail(allowedActions = listOf("start")), detailRepository = detailRepository)

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val loaded = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(loaded.isStarting)

                vm.start()

                val starting = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(starting.isStarting)

                val started = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(started.detail.allowedActions.isEmpty())
                assertFalse(started.isStarting)
            }
            coVerify(exactly = 1) { detailRepository.start(WORK_ORDER_ID) }
        }

    @Test
    fun `calling start twice before the first call returns only sends one request`() =
        runTest {
            val detailRepository = mockk<WorkOrderDetailRepository>()
            coEvery { detailRepository.start(WORK_ORDER_ID) } coAnswers {
                delay(10)
                Result.success(repairDetail(allowedActions = emptyList()))
            }
            val vm =
                viewModel(detail = repairDetail(allowedActions = listOf("start")), detailRepository = detailRepository)

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                awaitItem() as WorkOrderDetailUiState.Success

                vm.start()
                vm.start()

                awaitItem() as WorkOrderDetailUiState.Success
                awaitItem() as WorkOrderDetailUiState.Success
            }
            coVerify(exactly = 1) { detailRepository.start(WORK_ORDER_ID) }
        }

    @Test
    fun `start failure keeps the loaded detail and sets a start error instead of discarding the page`() =
        runTest {
            val detailRepository = mockk<WorkOrderDetailRepository>()
            coEvery { detailRepository.start(WORK_ORDER_ID) } returns Result.failure(IllegalStateException("conflict"))
            val vm = viewModel(detail = repairDetail(), detailRepository = detailRepository)

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                awaitItem() as WorkOrderDetailUiState.Success

                vm.start()

                val starting = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(starting.isStarting)
                val failed = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(failed.isStarting)
                assertEquals("Không bắt đầu được lệnh này", failed.startError)
            }
        }

    @Test
    fun `a network failure on start shows a connectivity message, not the raw exception`() =
        runTest {
            val detailRepository = mockk<WorkOrderDetailRepository>()
            coEvery { detailRepository.start(WORK_ORDER_ID) } returns
                Result.failure(IOException("Unable to resolve host"))
            val vm = viewModel(detail = repairDetail(), detailRepository = detailRepository)

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                awaitItem() as WorkOrderDetailUiState.Success

                vm.start()

                awaitItem() as WorkOrderDetailUiState.Success
                val failed = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals("Mất kết nối. Vui lòng thử lại.", failed.startError)
            }
        }

    @Test
    fun `emits Loading then Empty when the work order does not exist`() =
        runTest {
            val vm = viewModel(detail = null)

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                assertEquals(WorkOrderDetailUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `a repair cannot submit until an after photo exists and the note is at least 10 characters`() =
        runTest {
            val vm = viewModel(detail = repairDetail(allowedActions = listOf("complete")))

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(loaded.canSubmit())

                vm.onReportNoteChanged("short")
                assertFalse((awaitItem() as WorkOrderDetailUiState.Success).canSubmit())
            }
        }

    @Test
    fun `a repair can submit once an after photo exists and the note is long enough`() =
        runTest {
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
            val vm = viewModel(detail = repairDetail(allowedActions = listOf("complete")), evidence = evidence)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(loaded.canSubmit())

                vm.onReportNoteChanged("Đã thay bóng đèn mới")
                val withNote = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(withNote.canSubmit())
            }
        }

    @Test
    fun `an inspection with no linked faults only needs the note, and sends fault_outcomes as null`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            val vm =
                viewModel(detail = inspectionDetail(emptyList()), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onReportNoteChanged("Không phát hiện sự cố nào")
                val withNote = awaitItem() as WorkOrderDetailUiState.Success
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
            val faults = listOf(fault("FAULT-1"), fault("FAULT-2"))
            val vm =
                viewModel(detail = inspectionDetail(faults), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onReportNoteChanged("Đã kiểm tra tại hiện trường")
                val withNote = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(withNote.canSubmit())

                vm.onFaultOutcomeSelected("FAULT-1", "fault_present")
                val oneDone = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(oneDone.canSubmit())

                vm.onFaultOutcomeSelected("FAULT-2", "fault_absent")
                val bothDone = awaitItem() as WorkOrderDetailUiState.Success
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
            val detail =
                repairDetail(reviewNote = "Chưa đủ ảnh sau, bổ sung thêm")
                    .copy(reportNote = "Đã thay bóng đèn", materialsUsed = "1 bóng LED")
            val vm = viewModel(detail = detail)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals("Đã thay bóng đèn", loaded.reportNote)
                assertEquals("1 bóng LED", loaded.materialsUsed)
                assertEquals("Chưa đủ ảnh sau, bổ sung thêm", loaded.detail.reviewNote)
            }
        }

    @Test
    fun `syncStatus is null until a local completion row exists`() =
        runTest {
            val vm = viewModel(detail = repairDetail())

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderDetailUiState.Success
                assertNull(loaded.syncStatus())
            }
        }

    @Test
    fun `syncStatus reflects a pending local completion as queued-online or queued-offline`() =
        runTest {
            val pending =
                LocalWorkOrderCompletionEntity(
                    workOrderId = WORK_ORDER_ID,
                    reportNote = "note",
                    materialsUsed = null,
                    faultOutcomesJson = null,
                    clientOpId = "COMP-1",
                    submitStatus = "pending",
                )
            val vm = viewModel(detail = repairDetail(), isOnline = false, completion = pending)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals(SyncStatus.QUEUED_OFFLINE, loaded.syncStatus())
            }
        }

    @Test
    fun `a pole_gps_update order loads its pole references once and its measurements reactively`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>()
            val measurement =
                PoleMeasurement("P-1", 10.0, 106.0, 3.0, Instant.ofEpochSecond(1), MeasurementSource.DEVICE)
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(listOf(measurement))
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns
                listOf(PoleReference("P-1", 4, 10.0, 106.0))
            val vm = viewModel(detail = poleGpsDetail(poleIds = listOf("P-1")), poleGpsRepository = poleGpsRepository)

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val success = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals(listOf("P-1"), success.poleReferences.map { it.poleId })
                assertEquals(1, success.poleMeasurements.size)
            }
        }

    @Test
    fun `a pole_gps_update order refreshes poles from the server before reading pole references`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>()
            val poleListRepository = mockk<PoleListRepository>()
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(emptyList(), isStale = false))
            val vm =
                viewModel(
                    detail = poleGpsDetail(),
                    poleGpsRepository = poleGpsRepository,
                    poleListRepository = poleListRepository,
                )

            vm.uiState.test {
                awaitItem()
                awaitItem()
            }

            coVerify { poleListRepository.poles(WORK_ORDER_ID) }
        }

    @Test
    fun `a repair order never refreshes the pole list`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>(relaxed = true)
            val vm = viewModel(detail = repairDetail(), poleListRepository = poleListRepository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
            }

            coVerify(exactly = 0) { poleListRepository.poles(any()) }
        }

    @Test
    fun `canSubmit for pole_gps_update needs a valid note and at least 1 measured pole`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>()
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
            val vm = viewModel(detail = poleGpsDetail(), poleGpsRepository = poleGpsRepository)

            vm.uiState.test {
                awaitItem()
                val empty = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(empty.canSubmit())
            }
        }

    @Test
    fun `canSubmit for pole_gps_update is true with partial reporting, a single measured pole is enough`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>()
            val measurement =
                PoleMeasurement("P-1", 10.0, 106.0, 3.0, Instant.ofEpochSecond(1), MeasurementSource.DEVICE)
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(listOf(measurement))
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
            val vm =
                viewModel(detail = poleGpsDetail(poleIds = listOf("P-1", "P-2")), poleGpsRepository = poleGpsRepository)

            vm.uiState.test {
                awaitItem()
                val success = awaitItem() as WorkOrderDetailUiState.Success
                val valid = success.copy(reportNote = "Đã đo xong 1/2 cột đèn")
                assertTrue(valid.canSubmit())
            }
        }

    @Test
    fun `retryAttachment delegates to the pole GPS repository with this work order and attachment id`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>(relaxed = true)
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
            val vm = viewModel(detail = poleGpsDetail(), poleGpsRepository = poleGpsRepository)

            vm.retryAttachment("A-1")
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { poleGpsRepository.retryAttachment(WORK_ORDER_ID, "A-1") }
        }
}
