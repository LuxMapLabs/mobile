package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private const val WORK_ORDER_ID = "WO-1"

private fun detail(allowedActions: List<String> = listOf("start")) =
    WorkOrderDetail(
        workOrderId = WORK_ORDER_ID,
        title = "Sửa đèn tuyến A",
        woStatus = "assigned",
        taskKind = "repair",
        dueDate = null,
        scheduledDate = null,
        note = null,
        allowedActions = allowedActions,
        faults = emptyList(),
    )

private fun savedStateHandle() = SavedStateHandle(mapOf(WorkOrderDetailViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

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

    @Test
    fun `emits Loading then Success when the repository has the work order`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(detail())
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val success = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals(WORK_ORDER_ID, success.detail.workOrderId)
            }
        }

    @Test
    fun `emits Empty when the repository returns null`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(null)
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                assertEquals(WorkOrderDetailUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns
                flow { throw IllegalStateException("network down") }
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val error = awaitItem() as WorkOrderDetailUiState.Error
                assertEquals("network down", error.message)
            }
        }

    @Test
    fun `start sets isStarting immediately, then swaps in the backend's returned detail directly`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            val assignedDetail = detail(allowedActions = listOf("start"))
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(assignedDetail)
            coEvery { repository.start(WORK_ORDER_ID) } coAnswers {
                delay(10)
                Result.success(detail(allowedActions = emptyList()))
            }
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val loaded = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(loaded.isStarting)

                viewModel.start()

                val starting = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(starting.isStarting)

                val started = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(started.detail.allowedActions.isEmpty())
                assertFalse(started.isStarting)
            }
            coVerify(exactly = 1) { repository.start(WORK_ORDER_ID) }
        }

    @Test
    fun `calling start twice before the first call returns only sends one request`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            val initialDetail = detail(allowedActions = listOf("start"))
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(initialDetail)
            coEvery { repository.start(WORK_ORDER_ID) } coAnswers {
                delay(10)
                Result.success(detail(allowedActions = emptyList()))
            }
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                awaitItem() as WorkOrderDetailUiState.Success

                viewModel.start()
                viewModel.start()

                awaitItem() as WorkOrderDetailUiState.Success
                awaitItem() as WorkOrderDetailUiState.Success
            }
            coVerify(exactly = 1) { repository.start(WORK_ORDER_ID) }
        }

    @Test
    fun `start failure keeps the loaded detail and sets a start error instead of discarding the page`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(detail())
            coEvery { repository.start(WORK_ORDER_ID) } returns Result.failure(IllegalStateException("conflict"))
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                awaitItem() as WorkOrderDetailUiState.Success

                viewModel.start()

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
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(detail())
            coEvery { repository.start(WORK_ORDER_ID) } returns Result.failure(IOException("Unable to resolve host"))
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                awaitItem() as WorkOrderDetailUiState.Success

                viewModel.start()

                awaitItem() as WorkOrderDetailUiState.Success
                val failed = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals("Mất kết nối. Vui lòng thử lại.", failed.startError)
            }
        }
}
