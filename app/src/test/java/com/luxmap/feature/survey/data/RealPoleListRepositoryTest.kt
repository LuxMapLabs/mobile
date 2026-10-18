package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.dto.PoleListItemDto
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

class RealPoleListRepositoryTest {
    @Test
    fun `maps the paged DTO items into domain items in the same order and caches them`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PoleListCacheDao>(relaxed = true)
            coEvery { api.poles("WO-1") } returns
                PagedResultDto(
                    page = 1,
                    pageSize = 200,
                    total = 2,
                    items =
                        listOf(
                            PoleListItemDto(poleId = "POLE-1", position = 1, fixtureStatus = "normal"),
                            PoleListItemDto(poleId = "POLE-2", position = 2, fixtureStatus = "unknown"),
                        ),
                )
            val repository = RealPoleListRepository(api, dao)

            val result = repository.poles("WO-1")

            assertTrue(result.isSuccess)
            assertEquals(
                PoleListResult(
                    listOf(
                        PoleListItem("POLE-1", 1, "normal"),
                        PoleListItem("POLE-2", 2, "unknown"),
                    ),
                    isStale = false,
                ),
                result.getOrNull(),
            )
            coVerify {
                dao.replaceForWorkOrder(
                    "WO-1",
                    listOf(
                        LocalPoleListItemEntity("WO-1", "POLE-1", 1, "normal"),
                        LocalPoleListItemEntity("WO-1", "POLE-2", 2, "unknown"),
                    ),
                )
            }
        }

    @Test
    fun `falls back to the cached list marked stale when the fetch fails but a cache exists`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PoleListCacheDao>()
            coEvery { api.poles("WO-1") } throws httpException(500)
            coEvery { dao.byWorkOrder("WO-1") } returns
                listOf(LocalPoleListItemEntity("WO-1", "POLE-1", 1, "normal"))
            val repository = RealPoleListRepository(api, dao)

            val result = repository.poles("WO-1")

            assertEquals(
                PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = true),
                result.getOrNull(),
            )
        }

    @Test
    fun `returns failure when the backend rejects the request and there is no cache`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PoleListCacheDao>()
            coEvery { api.poles("WO-1") } throws httpException(404)
            coEvery { dao.byWorkOrder("WO-1") } returns emptyList()
            val repository = RealPoleListRepository(api, dao)

            assertTrue(repository.poles("WO-1").isFailure)
        }
}
