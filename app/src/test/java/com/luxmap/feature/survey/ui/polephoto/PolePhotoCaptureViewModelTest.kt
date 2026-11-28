package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.LuxReadingRepository
import com.luxmap.feature.survey.data.PoleListItem
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PoleListResult
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPoleSkipEntity
import com.luxmap.feature.survey.ui.polephoto.lux.LuxCardUiState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.time.Instant

private const val WORK_ORDER_ID = "WO-1"
private const val POLE_ID = "POLE-1"

private fun pole(id: String) =
    PoleListItem(
        poleId = id,
        position = null,
        lat = null,
        lng = null,
        fixtureStatus = "normal",
    )

private fun poleListRepository(
    poleIds: List<String> = emptyList(),
    cachedIds: List<String> = emptyList(),
): PoleListRepository =
    mockk {
        coEvery { cachedPoles(WORK_ORDER_ID) } returns cachedIds.map(::pole)
        coEvery { poles(WORK_ORDER_ID) } returns
            Result.success(PoleListResult(poleIds.map(::pole), isStale = false))
    }

private fun savedStateHandle(routeMode: Boolean = false) =
    SavedStateHandle(
        mapOf(
            PolePhotoCaptureViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID,
            PolePhotoCaptureViewModel.POLE_ID_ARG to POLE_ID,
            PolePhotoCaptureViewModel.ROUTE_ARG to routeMode,
        ),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PolePhotoCaptureViewModelTest {
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
    fun `onPhotoCaptured sends the real GPS fix to the repository and calls onDone`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(),
                    repository,
                    locationTracker,
                    mockk(relaxed = true),
                    poleListRepository(),
                )
            val file = File("x.jpg")
            var done = false

            vm.onPhotoCaptured(file, "on") { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.capture(
                    workOrderId = WORK_ORDER_ID,
                    poleId = POLE_ID,
                    filePath = file.absolutePath,
                    lat = 10.0,
                    lng = 106.0,
                    observation = "on",
                )
            }
            assertTrue(done)
        }

    @Test
    fun `onPhotoCaptured does not save fake coordinates when the GPS fix is unavailable`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns null
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(),
                    repository,
                    locationTracker,
                    mockk(relaxed = true),
                    poleListRepository(),
                )
            var done = false

            vm.onPhotoCaptured(File("x.jpg"), "on") { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) {
                repository.capture(
                    workOrderId = any(),
                    poleId = any(),
                    filePath = any(),
                    lat = any(),
                    lng = any(),
                    observation = any(),
                )
            }
            assertTrue(vm.locationMissing.value)
            assertFalse(done)
        }

    @Test
    fun `retrying after a missing GPS fix saves the real location and clears the flag`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returnsMany listOf(null, LatLng(10.0, 106.0))
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(),
                    repository,
                    locationTracker,
                    mockk(relaxed = true),
                    poleListRepository(),
                )
            val file = File("x.jpg")
            var done = false

            vm.onPhotoCaptured(file, "on") {}
            dispatcher.scheduler.advanceUntilIdle()
            vm.onPhotoCaptured(file, "on") { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.capture(
                    workOrderId = WORK_ORDER_ID,
                    poleId = POLE_ID,
                    filePath = file.absolutePath,
                    lat = 10.0,
                    lng = 106.0,
                    observation = "on",
                )
            }
            assertFalse(vm.locationMissing.value)
            assertTrue(done)
        }

    @Test
    fun `a photo that could not be written keeps the preview open and flags the failure`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.capture(
                    workOrderId = any(),
                    poleId = any(),
                    filePath = any(),
                    lat = any(),
                    lng = any(),
                    observation = any(),
                )
            } throws RuntimeException("disk full")
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(),
                    repository,
                    locationTracker,
                    mockk(relaxed = true),
                    poleListRepository(),
                )
            var done = false

            vm.onPhotoCaptured(File("x.jpg"), "on") { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(done)
            assertTrue(vm.saveFailed.value)
        }

    private val luxReading = LuxCardUiState.Recorded(12.3, Instant.parse("2026-11-24T12:00:00Z"))

    private fun savedMedia() =
        LocalPolePhotoEntity(
            photoId = "PHOTO-9",
            workOrderId = WORK_ORDER_ID,
            poleId = POLE_ID,
            filePath = "x.jpg",
            capturedAt = Instant.EPOCH,
            lat = 10.0,
            lng = 106.0,
        )

    @Test
    fun `onPhotoCaptured records the lux reading against the saved media`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.capture(
                    workOrderId = any(),
                    poleId = any(),
                    filePath = any(),
                    lat = any(),
                    lng = any(),
                    observation = any(),
                )
            } returns savedMedia()
            val luxRepository = mockk<LuxReadingRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(),
                    repository,
                    locationTracker,
                    luxRepository,
                    poleListRepository(),
                )

            vm.onPhotoCaptured(File("x.jpg"), "on", luxReading) {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { luxRepository.record(WORK_ORDER_ID, POLE_ID, "PHOTO-9", 12.3, luxReading.measuredAt) }
        }

    @Test
    fun `onPhotoCaptured records no lux when none was measured`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.capture(
                    workOrderId = any(),
                    poleId = any(),
                    filePath = any(),
                    lat = any(),
                    lng = any(),
                    observation = any(),
                )
            } returns savedMedia()
            val luxRepository = mockk<LuxReadingRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(),
                    repository,
                    locationTracker,
                    luxRepository,
                    poleListRepository(),
                )

            vm.onPhotoCaptured(File("x.jpg"), "on") {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { luxRepository.record(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `onPhotoCaptured records no lux when saving the media fails`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.capture(
                    workOrderId = any(),
                    poleId = any(),
                    filePath = any(),
                    lat = any(),
                    lng = any(),
                    observation = any(),
                )
            } throws RuntimeException("disk full")
            val luxRepository = mockk<LuxReadingRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(),
                    repository,
                    locationTracker,
                    luxRepository,
                    poleListRepository(),
                )
            var done = false

            vm.onPhotoCaptured(File("x.jpg"), "on", luxReading) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { luxRepository.record(any(), any(), any(), any(), any()) }
            assertFalse(done)
        }

    @Test
    fun `route mode hands back the next pole with neither a photo nor a skip`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            every { repository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(
                    listOf(
                        savedMedia().copy(poleId = POLE_ID),
                        savedMedia().copy(poleId = "POLE-2", engineerObservation = "on"),
                    ),
                )
            every { repository.observeSkips(WORK_ORDER_ID) } returns
                flowOf(listOf(LocalSurveyPoleSkipEntity(WORK_ORDER_ID, "POLE-3", "Bị che", Instant.EPOCH)))
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val poles = listOf("POLE-0", POLE_ID, "POLE-2", "POLE-3")
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(routeMode = true),
                    repository,
                    locationTracker,
                    mockk(relaxed = true),
                    poleListRepository(poles),
                )
            var next: String? = "unset"

            vm.onPhotoCaptured(File("x.jpg"), "off") { next = it }
            dispatcher.scheduler.advanceUntilIdle()

            // POLE-2 has a photo and POLE-3 a skip, so it wraps around to POLE-0.
            assertEquals("POLE-0", next)
            assertEquals(RouteProgress(index = 2, total = 4), vm.progress.value)
        }

    @Test
    fun `skipping a pole records the reason and returns to the list outside route mode`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(),
                    repository,
                    mockk(),
                    mockk(relaxed = true),
                    poleListRepository(listOf(POLE_ID, "POLE-2")),
                )
            var next: String? = "unset"

            vm.onSkip("Không tìm thấy cột") { next = it }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { repository.skipPole(WORK_ORDER_ID, POLE_ID, "Không tìm thấy cột") }
            assertNull(next)
        }

    @Test
    fun `the route card reads the pole saved on the device without waiting for the network`() =
        runTest {
            val poles = mockk<PoleListRepository>()
            coEvery { poles.cachedPoles(WORK_ORDER_ID) } returns listOf(pole("POLE-0"), pole(POLE_ID))
            val vm =
                PolePhotoCaptureViewModel(
                    savedStateHandle(routeMode = true),
                    mockk(relaxed = true),
                    mockk(),
                    mockk(relaxed = true),
                    poles,
                )
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(POLE_ID, vm.pole.value?.poleId)
            assertEquals(RouteProgress(index = 2, total = 2), vm.progress.value)
            coVerify(exactly = 0) { poles.poles(any()) }
        }
}
