package com.luxmap.feature.notification.ui

import app.cash.turbine.test
import com.luxmap.feature.notification.data.NotificationItem
import com.luxmap.feature.notification.data.NotificationPage
import com.luxmap.feature.notification.data.NotificationRepository
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import io.mockk.coEvery
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
import org.junit.Before
import org.junit.Test

private fun fakeWorkOrderDetailRepository(taskKind: String? = null): WorkOrderDetailRepository {
    val repository = mockk<WorkOrderDetailRepository>()
    val detail =
        taskKind?.let {
            WorkOrderDetail(
                workOrderId = "WO-1",
                title = "Title",
                woStatus = "assigned",
                taskKind = it,
                dueDate = null,
                scheduledDate = null,
                note = null,
                allowedActions = emptyList(),
                faults = emptyList(),
            )
        }
    every { repository.observeWorkOrderDetail(any()) } returns flowOf(detail)
    return repository
}

private fun item(
    id: String,
    entityType: String = "work_order",
    readAt: String? = null,
) = NotificationItem(
    id,
    "work_order_assigned",
    "Title $id",
    "Body $id",
    entityType,
    "WO-1",
    "2026-10-18T08:00:00Z",
    readAt,
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationListViewModelTest {
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
    fun `emits Loading then Success with mapped rows`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1")), total = 1, unreadCount = 1))
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository())

            vm.uiState.test {
                assertEquals(NotificationListUiState.Loading, awaitItem())
                val success = awaitItem() as NotificationListUiState.Success
                assertEquals(1, success.items.size)
                assertEquals("NTF-1", success.items[0].notificationId)
                assertEquals(false, success.items[0].isRead)
            }
        }

    @Test
    fun `emits Success carrying the real total from the page, even when fewer items load than total`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1")), total = 60, unreadCount = 1))
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository())

            vm.uiState.test {
                awaitItem()
                val success = awaitItem() as NotificationListUiState.Success
                assertEquals(60, success.total)
            }
        }

    @Test
    fun `emits Empty when there are no notifications`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.list() } returns
                Result.success(NotificationPage(emptyList(), total = 0, unreadCount = 0))
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository())

            vm.uiState.test {
                assertEquals(NotificationListUiState.Loading, awaitItem())
                assertEquals(NotificationListUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Error when the fetch fails`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.list() } returns Result.failure(IllegalStateException("network down"))
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository())

            vm.uiState.test {
                assertEquals(NotificationListUiState.Loading, awaitItem())
                val error = awaitItem() as NotificationListUiState.Error
                assertEquals("network down", error.message)
            }
        }

    @Test
    fun `onMarkRead optimistically flips the row and calls the repository`() =
        runTest {
            val repository = mockk<NotificationRepository>(relaxed = true)
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1")), total = 1, unreadCount = 1))
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository())

            vm.uiState.test {
                awaitItem()
                val success = awaitItem() as NotificationListUiState.Success
                assertEquals(false, success.items[0].isRead)
                vm.onMarkRead("NTF-1")
                val updated = awaitItem() as NotificationListUiState.Success
                assertEquals(true, updated.items[0].isRead)
            }
            dispatcher.scheduler.advanceUntilIdle()
            coVerify { repository.markRead("NTF-1") }
        }

    @Test
    fun `onMarkAllRead flips every row and calls the repository`() =
        runTest {
            val repository = mockk<NotificationRepository>(relaxed = true)
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1"), item("NTF-2")), total = 2, unreadCount = 2))
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository())

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onMarkAllRead()
                val updated = awaitItem() as NotificationListUiState.Success
                assertEquals(true, updated.items.all { it.isRead })
            }
            dispatcher.scheduler.advanceUntilIdle()
            coVerify { repository.markAllRead() }
        }

    @Test
    fun `onMarkRead on a non-work-order row still marks it read`() =
        runTest {
            val repository = mockk<NotificationRepository>(relaxed = true)
            coEvery { repository.list() } returns
                Result.success(
                    NotificationPage(listOf(item("NTF-1", entityType = "survey_sweep")), total = 1, unreadCount = 1),
                )
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository())

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onMarkRead("NTF-1")
                val updated = awaitItem() as NotificationListUiState.Success
                assertEquals(true, updated.items[0].isRead)
            }
        }

    @Test
    fun `onItemClick on a survey work order emits OpenSurveyHub`() =
        runTest {
            val repository = mockk<NotificationRepository>(relaxed = true)
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1")), total = 1, unreadCount = 1))
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository(taskKind = "survey"))

            vm.navigate.test {
                vm.onItemClick(
                    NotificationListRowState(
                        "NTF-1",
                        "work_order_assigned",
                        "Title",
                        "Body",
                        "work_order",
                        "WO-1",
                        "2026-10-18T08:00:00Z",
                        isRead = false,
                    ),
                )
                assertEquals(NotificationNavigationEvent.OpenSurveyHub("WO-1"), awaitItem())
            }
        }

    @Test
    fun `onItemClick on a repair work order emits OpenWorkOrderDetail`() =
        runTest {
            val repository = mockk<NotificationRepository>(relaxed = true)
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1")), total = 1, unreadCount = 1))
            val vm = NotificationListViewModel(repository, fakeWorkOrderDetailRepository(taskKind = "repair"))

            vm.navigate.test {
                vm.onItemClick(
                    NotificationListRowState(
                        "NTF-1",
                        "work_order_assigned",
                        "Title",
                        "Body",
                        "work_order",
                        "WO-1",
                        "2026-10-18T08:00:00Z",
                        isRead = false,
                    ),
                )
                assertEquals(NotificationNavigationEvent.OpenWorkOrderDetail("WO-1"), awaitItem())
            }
        }
}
