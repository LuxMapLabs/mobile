package com.luxmap.feature.polelocation.ui.list

import app.cash.turbine.test
import com.luxmap.feature.polelocation.data.PoleLocationRepository
import com.luxmap.feature.polelocation.data.PoleMissingLocationItem
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun item(poleId: String) = PoleMissingLocationItem(poleId, null, null, null, null)

@OptIn(ExperimentalCoroutinesApi::class)
class PoleLocationListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts Loading then shows the server's list as Success`() =
        runTest {
            val repository = mockk<PoleLocationRepository>()
            coEvery { repository.polesMissingLocation() } returns Result.success(listOf(item("POLE-1"), item("POLE-2")))
            val vm = PoleLocationListViewModel(repository)

            vm.uiState.test {
                assertEquals(PoleLocationListUiState.Loading, awaitItem())
                val success = awaitItem() as PoleLocationListUiState.Success
                assertEquals(listOf("POLE-1", "POLE-2"), success.items.map { it.poleId })
            }
        }

    @Test
    fun `an empty list is Empty, not a Success with no rows`() =
        runTest {
            val repository = mockk<PoleLocationRepository>()
            coEvery { repository.polesMissingLocation() } returns Result.success(emptyList())
            val vm = PoleLocationListViewModel(repository)

            vm.uiState.test {
                awaitItem()
                assertEquals(PoleLocationListUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `a repository failure is Error`() =
        runTest {
            val repository = mockk<PoleLocationRepository>()
            coEvery { repository.polesMissingLocation() } returns Result.failure(RuntimeException("offline"))
            val vm = PoleLocationListViewModel(repository)

            vm.uiState.test {
                awaitItem()
                val error = awaitItem() as PoleLocationListUiState.Error
                assertTrue(error.message.isNotBlank())
            }
        }

    @Test
    fun `onRetry goes back to Loading then re-fetches`() =
        runTest {
            val repository = mockk<PoleLocationRepository>()
            coEvery { repository.polesMissingLocation() } returns
                Result.success(emptyList()) andThen Result.success(listOf(item("POLE-1")))
            val vm = PoleLocationListViewModel(repository)

            vm.uiState.test {
                awaitItem()
                assertEquals(PoleLocationListUiState.Empty, awaitItem())
                vm.onRetry()
                assertEquals(PoleLocationListUiState.Loading, awaitItem())
                val success = awaitItem() as PoleLocationListUiState.Success
                assertEquals(listOf("POLE-1"), success.items.map { it.poleId })
            }
        }
}
