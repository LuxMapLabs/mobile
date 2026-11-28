package com.luxmap.feature.history.ui

import app.cash.turbine.test
import com.luxmap.feature.history.data.HistoryItem
import com.luxmap.feature.history.data.HistoryRepository
import com.luxmap.feature.history.data.HistoryResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun item(
    id: String,
    kind: String = "repair",
    status: String = "verified",
) = HistoryItem(
    workOrderId = id,
    title = "Lệnh $id",
    taskKind = kind,
    woStatus = status,
    communeId = "COM-001",
    updatedAt = "2026-11-20T08:00:00Z",
    scheduledDate = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
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
    fun `emits Success with the stale flag when the repository returns items`() =
        runTest {
            val repository = mockk<HistoryRepository>()
            every { repository.observeHistory() } returns
                flowOf(HistoryResult(listOf(item("WO-1"), item("WO-2", "survey")), isStale = true))
            val viewModel = HistoryViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HistoryUiState.Loading, awaitItem())
                val success = awaitItem() as HistoryUiState.Success
                assertEquals(listOf("WO-1", "WO-2"), success.items.map { it.workOrderId })
                assertEquals(true, success.isStale)
            }
        }

    @Test
    fun `emits Empty when there is no closed work order`() =
        runTest {
            val repository = mockk<HistoryRepository>()
            every { repository.observeHistory() } returns flowOf(HistoryResult(emptyList(), isStale = false))
            val viewModel = HistoryViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HistoryUiState.Loading, awaitItem())
                assertEquals(HistoryUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `an IOException is a network error, anything else is not, and retry recovers`() =
        runTest {
            val repository = mockk<HistoryRepository>()
            every { repository.observeHistory() } returnsMany
                listOf(
                    flow { throw IOException("timeout") },
                    flow { throw IllegalStateException("boom") },
                    flowOf(HistoryResult(listOf(item("WO-1")), isStale = false)),
                )
            val viewModel = HistoryViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HistoryUiState.Loading, awaitItem())
                assertEquals(true, (awaitItem() as HistoryUiState.Error).isNetworkError)
                viewModel.retry()
                assertEquals(HistoryUiState.Loading, awaitItem())
                assertEquals(false, (awaitItem() as HistoryUiState.Error).isNetworkError)
                viewModel.retry()
                assertEquals(HistoryUiState.Loading, awaitItem())
                awaitItem() as HistoryUiState.Success
            }
        }

    @Test
    fun `the first resume does not reload but later resumes do`() =
        runTest {
            val repository = mockk<HistoryRepository>()
            every { repository.observeHistory() } returns flowOf(HistoryResult(listOf(item("WO-1")), false))
            val viewModel = HistoryViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HistoryUiState.Loading, awaitItem())
                awaitItem() as HistoryUiState.Success
                viewModel.onResume()
                expectNoEvents()
                viewModel.onResume()
                assertEquals(HistoryUiState.Loading, awaitItem())
                awaitItem() as HistoryUiState.Success
            }
        }

    @Test
    fun `date caption follows the status and falls back to the update time for rows cached before the new fields`() {
        val verified = item("A").copy(closedAt = "2026-11-25T08:00:00Z")
        val done = item("B", status = "done").copy(completedAt = "2026-11-24T08:00:00Z")
        val legacy = item("C", status = "cancelled")

        assertEquals("Đã duyệt 25/11/2026", verified.dateCaption())
        assertEquals("Đã nộp 24/11/2026", done.dateCaption())
        assertEquals("Đã hủy 20/11/2026", legacy.dateCaption())
    }

    @Test
    fun `filters count and narrow by task kind, offering only kinds that have work`() {
        val items = listOf(item("R1"), item("R2"), item("S1", "survey"), item("X1", "audit"))

        assertEquals(listOf(HistoryFilter.ALL, HistoryFilter.REPAIR, HistoryFilter.SURVEY), items.availableFilters())
        assertEquals(4, items.countFor(HistoryFilter.ALL))
        assertEquals(2, items.countFor(HistoryFilter.REPAIR))
        assertEquals(listOf("S1"), items.filteredBy(HistoryFilter.SURVEY).map { it.workOrderId })
        assertEquals(items, items.filteredBy(HistoryFilter.ALL))
    }
}
