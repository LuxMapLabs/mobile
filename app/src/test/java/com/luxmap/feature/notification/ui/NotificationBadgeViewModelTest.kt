package com.luxmap.feature.notification.ui

import app.cash.turbine.test
import com.luxmap.feature.notification.data.NotificationRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBadgeViewModelTest {
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
    fun `starts at zero before the first refresh`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            val vm = NotificationBadgeViewModel(repository)

            assertEquals(0, vm.unreadCount.value)
        }

    @Test
    fun `refresh updates unreadCount from the repository`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.unreadCount() } returns Result.success(5)
            val vm = NotificationBadgeViewModel(repository)

            vm.unreadCount.test {
                assertEquals(0, awaitItem())
                vm.refresh()
                assertEquals(5, awaitItem())
            }
        }

    @Test
    fun `refresh keeps the previous count when the fetch fails`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.unreadCount() } returnsMany
                listOf(Result.success(3), Result.failure(IllegalStateException("network down")))
            val vm = NotificationBadgeViewModel(repository)

            vm.unreadCount.test {
                assertEquals(0, awaitItem())
                vm.refresh()
                assertEquals(3, awaitItem())
                vm.refresh()
                // No new emission - StateFlow conflates the unchanged value, so there is nothing
                // further to await here; if a failed refresh is the test's last action, confirm
                // via vm.unreadCount.value instead of awaiting a second time.
                expectNoEvents()
            }
            assertEquals(3, vm.unreadCount.value)
        }

    @Test
    fun `reset clears the count back to zero`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.unreadCount() } returns Result.success(9)
            val vm = NotificationBadgeViewModel(repository)

            vm.unreadCount.test {
                assertEquals(0, awaitItem())
                vm.refresh()
                assertEquals(9, awaitItem())
                vm.reset()
                assertEquals(0, awaitItem())
            }
        }
}
