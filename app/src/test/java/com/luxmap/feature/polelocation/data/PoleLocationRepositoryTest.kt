package com.luxmap.feature.polelocation.data

import com.luxmap.core.network.AssetsApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.polelocation.data.dto.PoleMissingLocationItemDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private fun missingItem(
    poleId: String,
    note: String? = null,
) = PoleMissingLocationItemDto(poleId = poleId, note = note)

class PoleLocationRepositoryTest {
    @Test
    fun `polesMissingLocation returns every server item when nothing is captured locally`() =
        runTest {
            val api = mockk<AssetsApi>()
            val dao = mockk<PoleLocationUpdateDao>()
            coEvery { api.listPoles(missingLocation = true) } returns
                PagedResultDto(1, 200, 2, listOf(missingItem("POLE-1"), missingItem("POLE-2")))
            coEvery { dao.byPoleId(any()) } returns null
            val repository = PoleLocationRepository(api, dao, mockk(relaxed = true))

            val result = repository.polesMissingLocation().getOrThrow()

            assertEquals(listOf("POLE-1", "POLE-2"), result.map { it.poleId })
            assertEquals(listOf(null, null), result.map { it.localStatus })
        }

    @Test
    fun `a pole already queued or done locally is dropped from the list`() =
        runTest {
            val api = mockk<AssetsApi>()
            val dao = mockk<PoleLocationUpdateDao>()
            coEvery { api.listPoles(missingLocation = true) } returns
                PagedResultDto(1, 200, 2, listOf(missingItem("POLE-1"), missingItem("POLE-2")))
            coEvery { dao.byPoleId("POLE-1") } returns
                LocalPoleLocationUpdateEntity(
                    "POLE-1", "/f/a.jpg", 1.0, 2.0, Instant.now(), PoleLocationUpdateStatus.QUEUED, "OP-1",
                )
            coEvery { dao.byPoleId("POLE-2") } returns null
            val repository = PoleLocationRepository(api, dao, mockk(relaxed = true))

            val result = repository.polesMissingLocation().getOrThrow()

            assertEquals(listOf("POLE-2"), result.map { it.poleId })
        }

    @Test
    fun `a pole whose update conflicted or failed still shows, so it can be retried`() =
        runTest {
            val api = mockk<AssetsApi>()
            val dao = mockk<PoleLocationUpdateDao>()
            coEvery { api.listPoles(missingLocation = true) } returns
                PagedResultDto(1, 200, 1, listOf(missingItem("POLE-1")))
            coEvery { dao.byPoleId("POLE-1") } returns
                LocalPoleLocationUpdateEntity(
                    "POLE-1", "/f/a.jpg", 1.0, 2.0, Instant.now(), PoleLocationUpdateStatus.CONFLICT, "OP-1",
                )
            val repository = PoleLocationRepository(api, dao, mockk(relaxed = true))

            val result = repository.polesMissingLocation().getOrThrow()

            assertEquals(listOf("POLE-1"), result.map { it.poleId })
            assertEquals(PoleLocationUpdateStatus.CONFLICT, result.single().localStatus)
        }

    @Test
    fun `submit writes Room first, then enqueues with a fresh client op id`() =
        runTest {
            val dao = mockk<PoleLocationUpdateDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val repository = PoleLocationRepository(mockk(), dao, syncQueueManager)
            val capturedAt = Instant.ofEpochSecond(1_000)

            repository.submit("POLE-1", "/f/a.jpg", 10.5, 106.5, capturedAt)

            val saved = slot<LocalPoleLocationUpdateEntity>()
            coVerify { dao.upsert(capture(saved)) }
            assertEquals("POLE-1", saved.captured.poleId)
            assertEquals(10.5, saved.captured.lat, 0.0)
            assertEquals(PoleLocationUpdateStatus.PENDING, saved.captured.status)
            coVerify {
                syncQueueManager.enqueue(
                    opType = "update_pole_location",
                    payloadJson = any(),
                    clientOpId = saved.captured.clientOpId,
                )
            }
            coVerify { dao.updateStatus("POLE-1", PoleLocationUpdateStatus.QUEUED) }
        }
}
