package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoItemDto
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PolePhotoWithdrawalTest {
    private val api = mockk<WorkOrdersApi>()
    private val dao = mockk<PolePhotoDao>()
    private val withdrawal = PolePhotoWithdrawal(api, dao)

    @Test
    fun `replacement withdraws older active media across pages and keeps the latest`() =
        runTest {
            coEvery { api.listPolePhotos("WO-1", "P-1", 1, 100) } returns
                PagedResultDto(1, 2, 3, listOf(photo("A"), photo("B")))
            coEvery { api.listPolePhotos("WO-1", "P-1", 2, 100) } returns PagedResultDto(2, 2, 3, listOf(photo("C")))
            coEvery { dao.byPole("WO-1", "P-1") } returns emptyList()
            for (old in listOf("A", "B")) {
                coJustRun { api.withdrawPolePhoto("WO-1", old) }
                coJustRun { dao.deleteByServerPhotoId("WO-1", old) }
            }

            withdrawal.withdrawReplacedMedia("WO-1", "P-1")

            coVerifyOrder {
                api.withdrawPolePhoto("WO-1", "A")
                dao.deleteByServerPhotoId("WO-1", "A")
                api.withdrawPolePhoto("WO-1", "B")
                dao.deleteByServerPhotoId("WO-1", "B")
            }
            coVerify(exactly = 0) { api.withdrawPolePhoto("WO-1", "C") }
        }

    @Test
    fun `cancel clears local records only after the server withdraws the selected result`() =
        runTest {
            coEvery { api.listPolePhotos("WO-1", "P-1", 1, 100) } returns page(photo("A"), photo("B"))
            coJustRun { api.withdrawPolePhoto("WO-1", "A") }
            coJustRun { api.withdrawPolePhoto("WO-1", "B") }
            coEvery { dao.byPole("WO-1", "P-1") } returns listOf(localPhoto("B"), localPhoto("A"))
            coJustRun { dao.deleteByIds(listOf("local-B", "local-A")) }

            withdrawal.withdrawPole("WO-1", "P-1", "B")

            coVerifyOrder {
                api.withdrawPolePhoto("WO-1", "A")
                api.withdrawPolePhoto("WO-1", "B")
                dao.deleteByIds(listOf("local-B", "local-A"))
            }
        }

    @Test
    fun `cancel preserves the local result if the server is unreachable`() =
        runTest {
            coEvery { api.listPolePhotos("WO-1", "P-1", 1, 100) } returns page(photo("A"))
            coEvery { dao.byPole("WO-1", "P-1") } returns listOf(localPhoto("A"))
            coEvery { api.withdrawPolePhoto("WO-1", "A") } throws IOException()

            val failure = runCatching { withdrawal.withdrawPole("WO-1", "P-1", "A") }.exceptionOrNull()

            assertTrue(failure is IOException)
            coVerify(exactly = 0) { dao.deleteByIds(any()) }
        }

    @Test
    fun `cancel from an older screen leaves the newer media available`() =
        runTest {
            coEvery { dao.byPole("WO-1", "P-1") } returns listOf(localPhoto("B"), localPhoto("A"))

            val failure = runCatching { withdrawal.withdrawPole("WO-1", "P-1", "A") }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            coVerify(exactly = 0) { api.withdrawPolePhoto(any(), any()) }
            coVerify(exactly = 0) { dao.deleteByIds(any()) }
        }

    private fun localPhoto(id: String) =
        mockk<LocalPolePhotoEntity> {
            every { photoId } returns "local-$id"
            every { serverPhotoId } returns id
        }

    private fun page(vararg records: PolePhotoItemDto) = PagedResultDto(1, 100, records.size, records.toList())

    private fun photo(id: String) =
        mockk<PolePhotoItemDto> {
            every { photoId } returns id
            every { decision } returns null
        }
}
