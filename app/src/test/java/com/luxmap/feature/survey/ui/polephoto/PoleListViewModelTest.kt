package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.PoleListItem
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coEvery
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
import java.time.Instant

private const val WORK_ORDER_ID = "WO-1"

private fun savedStateHandle() = SavedStateHandle(mapOf(PoleListViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

private fun photo(
    poleId: String,
    serverPhotoId: String? = null,
) = LocalPolePhotoEntity(
    photoId = "PH-$poleId",
    workOrderId = WORK_ORDER_ID,
    poleId = poleId,
    filePath = "/data/$poleId.jpg",
    capturedAt = Instant.parse("2026-10-15T10:00:00Z"),
    lat = 10.0,
    lng = 106.0,
    serverPhotoId = serverPhotoId,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PoleListViewModelTest {
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
    fun `emits Loading then Success with the sync status of each pole's latest photo`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(
                    listOf(
                        PoleListItem("POLE-1", 1, "normal"),
                        PoleListItem("POLE-2", 2, "dim"),
                    ),
                )
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(listOf(photo("POLE-1", serverPhotoId = "SERVER-PH-POLE-1")))
            every { polePhotoRepository.observeUploadStatuses(listOf("PH-POLE-1")) } returns
                flowOf(mapOf("PH-POLE-1" to "done"))
            val vm = PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository)

            vm.uiState.test {
                assertEquals(PoleListUiState.Loading, awaitItem())
                val success = awaitItem() as PoleListUiState.Success
                assertEquals(
                    listOf(
                        PoleListRowState(
                            "POLE-1",
                            1,
                            "normal",
                            serverPhotoId = "SERVER-PH-POLE-1",
                            syncStatus = SyncStatus.DONE,
                        ),
                        PoleListRowState("POLE-2", 2, "dim", serverPhotoId = null, syncStatus = null),
                    ),
                    success.poles,
                )
            }
        }

    @Test
    fun `emits Empty when the work order has no poles`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns Result.success(emptyList())
            val vm = PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository)

            vm.uiState.test {
                assertEquals(PoleListUiState.Loading, awaitItem())
                assertEquals(PoleListUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Error when the pole list request fails`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.failure(IllegalStateException("network down"))
            val vm = PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository)

            vm.uiState.test {
                assertEquals(PoleListUiState.Loading, awaitItem())
                val error = awaitItem() as PoleListUiState.Error
                assertEquals("network down", error.message)
            }
        }
}
