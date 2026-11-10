package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.PoleListItem
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PoleListResult
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
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
import java.time.Instant

private const val WORK_ORDER_ID = "WO-1"

private fun savedStateHandle() = SavedStateHandle(mapOf(PoleListViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

private fun photo(
    poleId: String,
    serverPhotoId: String? = null,
    aiStatus: String? = null,
    cvState: String? = null,
) = LocalPolePhotoEntity(
    photoId = "PH-$poleId",
    workOrderId = WORK_ORDER_ID,
    poleId = poleId,
    filePath = "/data/$poleId.jpg",
    capturedAt = Instant.parse("2026-10-15T10:00:00Z"),
    lat = 10.0,
    lng = 106.0,
    serverPhotoId = serverPhotoId,
    aiStatus = aiStatus,
    cvState = cvState,
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
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(
                    PoleListResult(
                        listOf(
                            PoleListItem("POLE-1", 1, "normal"),
                            PoleListItem("POLE-2", 2, "dim"),
                        ),
                        isStale = false,
                    ),
                )
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(listOf(photo("POLE-1", serverPhotoId = "SERVER-PH-POLE-1")))
            every { polePhotoRepository.observeUploadStatuses(listOf("PH-POLE-1")) } returns
                flowOf(mapOf("PH-POLE-1" to "done"))
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)

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
                assertEquals(false, success.isStale)
                assertEquals(false, success.isCompleted)
            }
        }

    @Test
    fun `onComplete submits a completion reporting how many poles were captured`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(
                    PoleListResult(
                        listOf(PoleListItem("POLE-1", 1, "normal"), PoleListItem("POLE-2", 2, "dim")),
                        isStale = false,
                    ),
                )
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(listOf(photo("POLE-1", serverPhotoId = "SERVER-PH-POLE-1")))
            every { polePhotoRepository.observeUploadStatuses(listOf("PH-POLE-1")) } returns
                flowOf(mapOf("PH-POLE-1" to "done"))
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            coEvery { polePhotoRepository.enqueuePendingReviews(WORK_ORDER_ID) } returns 0
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)

            vm.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success
            }
            vm.onComplete()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                completionRepository.submitCompletion(
                    workOrderId = WORK_ORDER_ID,
                    taskKind = "survey",
                    reportNote = "Đã khảo sát 1/2 cột đèn bằng ảnh chụp.",
                    materialsUsed = null,
                    faultOutcomes = null,
                )
            }
        }

    // Found on a real device 2026-10-29: tapping "Hoan tat" gave no feedback at all. This event
    // lets the Route show a toast right on this screen instead of navigating away silently.
    @Test
    fun `onComplete emits surveyCompleted after a successful submit`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = false))
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(listOf(photo("POLE-1", serverPhotoId = "SERVER-PH-POLE-1")))
            every { polePhotoRepository.observeUploadStatuses(listOf("PH-POLE-1")) } returns
                flowOf(mapOf("PH-POLE-1" to "done"))
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            coEvery { polePhotoRepository.enqueuePendingReviews(WORK_ORDER_ID) } returns 0
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)
            dispatcher.scheduler.advanceUntilIdle()

            vm.surveyCompleted.test {
                vm.onComplete()
                awaitItem()
            }
        }

    // Found on a real device 2026-10-29: the Hub card looked completely unchanged after
    // completing a survey, since nothing locally tracked a pending completion for it - this is
    // the same local_work_order_completion row WorkOrderDetailScreen already relies on for
    // repair/inspection, just read here too.
    @Test
    fun `a local completion row marks the state isCompleted and locks further reporting`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = false))
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { polePhotoRepository.observeUploadStatuses(emptyList()) } returns flowOf(emptyMap())
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns
                flowOf(
                    LocalWorkOrderCompletionEntity(
                        workOrderId = WORK_ORDER_ID,
                        reportNote = "Đã khảo sát 1/1 cột đèn bằng ảnh chụp.",
                        materialsUsed = null,
                        faultOutcomesJson = null,
                        clientOpId = "OP-1",
                        submitStatus = "pending",
                    ),
                )
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)

            vm.uiState.test {
                awaitItem() // Loading
                val success = awaitItem() as PoleListUiState.Success
                assertEquals(true, success.isCompleted)
            }
        }

    @Test
    fun `onComplete does nothing when no pole has been captured yet`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = false))
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { polePhotoRepository.observeUploadStatuses(emptyList()) } returns flowOf(emptyMap())
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)

            vm.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success
            }
            vm.onComplete()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { completionRepository.submitCompletion(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `emits Success with isStale when the repository falls back to its cache`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = true))
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { polePhotoRepository.observeUploadStatuses(emptyList()) } returns flowOf(emptyMap())
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)

            vm.uiState.test {
                assertEquals(PoleListUiState.Loading, awaitItem())
                val success = awaitItem() as PoleListUiState.Success
                assertEquals(true, success.isStale)
            }
        }

    @Test
    fun `emits Empty when the work order has no poles`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(emptyList(), isStale = false))
            val vm =
                PoleListViewModel(
                    savedStateHandle(),
                    poleListRepository,
                    polePhotoRepository,
                    mockk<WorkOrderCompletionRepository>(relaxed = true),
                )

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
            val vm =
                PoleListViewModel(
                    savedStateHandle(),
                    poleListRepository,
                    polePhotoRepository,
                    mockk<WorkOrderCompletionRepository>(relaxed = true),
                )

            vm.uiState.test {
                assertEquals(PoleListUiState.Loading, awaitItem())
                val error = awaitItem() as PoleListUiState.Error
                assertEquals("network down", error.message)
            }
        }

    @Test
    fun `onViewAiResult shows Loading then the fetched result when nothing is cached yet`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(emptyList(), isStale = false))
            coEvery { polePhotoRepository.cachedResult("SERVER-PH-1") } returns null
            coEvery { polePhotoRepository.fetchResult(WORK_ORDER_ID, "SERVER-PH-1") } returns
                Result.success(photo("POLE-1", serverPhotoId = "SERVER-PH-1", aiStatus = "available", cvState = "on"))
            val vm =
                PoleListViewModel(
                    savedStateHandle(),
                    poleListRepository,
                    polePhotoRepository,
                    mockk<WorkOrderCompletionRepository>(relaxed = true),
                )

            vm.aiResultState.test {
                assertEquals(null, awaitItem())
                vm.onViewAiResult("SERVER-PH-1")
                assertEquals(PolePhotoResultUiState.Loading, awaitItem())
                val result = awaitItem() as PolePhotoResultUiState.Success
                assertEquals("on", result.cvState)
                assertEquals(false, result.isStale)
            }
        }

    @Test
    fun `onViewAiResult replaces the stale cache with the fresh result on success`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(emptyList(), isStale = false))
            coEvery { polePhotoRepository.cachedResult("SERVER-PH-1") } returns
                photo("POLE-1", serverPhotoId = "SERVER-PH-1", aiStatus = "available", cvState = "off")
            coEvery { polePhotoRepository.fetchResult(WORK_ORDER_ID, "SERVER-PH-1") } returns
                Result.success(photo("POLE-1", serverPhotoId = "SERVER-PH-1", aiStatus = "available", cvState = "on"))
            val vm =
                PoleListViewModel(
                    savedStateHandle(),
                    poleListRepository,
                    polePhotoRepository,
                    mockk<WorkOrderCompletionRepository>(relaxed = true),
                )

            vm.aiResultState.test {
                assertEquals(null, awaitItem())
                vm.onViewAiResult("SERVER-PH-1")
                val stale = awaitItem() as PolePhotoResultUiState.Success
                assertEquals("off", stale.cvState)
                assertEquals(true, stale.isStale)
                val fresh = awaitItem() as PolePhotoResultUiState.Success
                assertEquals("on", fresh.cvState)
                assertEquals(false, fresh.isStale)
            }
        }

    @Test
    fun `onViewAiResult keeps the stale cached result instead of Error when the refresh fails`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(emptyList(), isStale = false))
            coEvery { polePhotoRepository.cachedResult("SERVER-PH-1") } returns
                photo("POLE-1", serverPhotoId = "SERVER-PH-1", aiStatus = "available", cvState = "off")
            coEvery { polePhotoRepository.fetchResult(WORK_ORDER_ID, "SERVER-PH-1") } returns
                Result.failure(IllegalStateException("network down"))
            val vm =
                PoleListViewModel(
                    savedStateHandle(),
                    poleListRepository,
                    polePhotoRepository,
                    mockk<WorkOrderCompletionRepository>(relaxed = true),
                )

            vm.aiResultState.test {
                assertEquals(null, awaitItem())
                vm.onViewAiResult("SERVER-PH-1")
                // Only one emission: the failed refresh re-assigns the same cached value, which
                // StateFlow conflates away (no distinct second emission to await).
                val stale = awaitItem() as PolePhotoResultUiState.Success
                assertEquals("off", stale.cvState)
                assertEquals(true, stale.isStale)
            }
        }

    @Test
    fun `onViewAiResult maps ai_status unavailable to Success with a null cvState, not Empty`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(emptyList(), isStale = false))
            coEvery { polePhotoRepository.cachedResult("SERVER-PH-1") } returns null
            coEvery { polePhotoRepository.fetchResult(WORK_ORDER_ID, "SERVER-PH-1") } returns
                Result.success(photo("POLE-1", serverPhotoId = "SERVER-PH-1", aiStatus = "unavailable", cvState = null))
            val vm =
                PoleListViewModel(
                    savedStateHandle(),
                    poleListRepository,
                    polePhotoRepository,
                    mockk<WorkOrderCompletionRepository>(relaxed = true),
                )

            vm.aiResultState.test {
                assertEquals(null, awaitItem())
                vm.onViewAiResult("SERVER-PH-1")
                assertEquals(PolePhotoResultUiState.Loading, awaitItem())
                // The Field Engineer must still be able to review a photo even when the AI model
                // failed outright (business rule D-2) - this must not be the Empty dead end.
                val result = awaitItem() as PolePhotoResultUiState.Success
                assertEquals(null, result.cvState)
            }
        }

    @Test
    fun `onSubmitReview saves the draft locally and reflects it in aiResultState without calling the server`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(emptyList(), isStale = false))
            coEvery { polePhotoRepository.cachedResult("SERVER-PH-1") } returns null
            coEvery { polePhotoRepository.fetchResult(WORK_ORDER_ID, "SERVER-PH-1") } returns
                Result.success(photo("POLE-1", serverPhotoId = "SERVER-PH-1", aiStatus = "available", cvState = "on"))
            val vm =
                PoleListViewModel(
                    savedStateHandle(),
                    poleListRepository,
                    polePhotoRepository,
                    mockk<WorkOrderCompletionRepository>(relaxed = true),
                )

            vm.aiResultState.test {
                assertEquals(null, awaitItem())
                vm.onViewAiResult("SERVER-PH-1")
                assertEquals(PolePhotoResultUiState.Loading, awaitItem())
                awaitItem() // the fetched result, not under test here

                vm.onSubmitReview("SERVER-PH-1", "off", "Thấy đèn tắt")
                val saved = awaitItem() as PolePhotoResultUiState.Success
                assertEquals("off", saved.draftFinalCvState)
                assertEquals("Thấy đèn tắt", saved.draftReviewNote)
            }
            coVerify { polePhotoRepository.saveDraftReview("SERVER-PH-1", "off", "Thấy đèn tắt") }
            coVerify(exactly = 0) { polePhotoRepository.enqueuePendingReviews(any()) }
        }

    @Test
    fun `onComplete enqueues pending reviews before submitting completion`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = false))
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(listOf(photo("POLE-1", serverPhotoId = "SERVER-PH-POLE-1")))
            every { polePhotoRepository.observeUploadStatuses(listOf("PH-POLE-1")) } returns
                flowOf(mapOf("PH-POLE-1" to "done"))
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            coEvery { polePhotoRepository.enqueuePendingReviews(WORK_ORDER_ID) } returns 2
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)
            vm.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success
            }

            vm.surveyCompleted.test {
                vm.onComplete()
                assertEquals(2, awaitItem())
            }
            coVerify { polePhotoRepository.enqueuePendingReviews(WORK_ORDER_ID) }
            coVerify { completionRepository.submitCompletion(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `a pole's row carries hasPendingReview when its latest photo has an unsent draft`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = false))
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(
                    listOf(
                        photo("POLE-1", serverPhotoId = "SERVER-PH-POLE-1").copy(draftFinalCvState = "off"),
                    ),
                )
            every { polePhotoRepository.observeUploadStatuses(listOf("PH-POLE-1")) } returns
                flowOf(mapOf("PH-POLE-1" to "done"))
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)

            vm.uiState.test {
                awaitItem() // Loading
                val success = awaitItem() as PoleListUiState.Success
                assertEquals(true, success.poles.single().hasPendingReview)
            }
        }
}
