package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.survey.data.dto.PoleListItemDto
import io.mockk.coEvery
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
    fun `maps the paged DTO items into domain items in the same order`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
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
            val repository = RealPoleListRepository(api)

            val result = repository.poles("WO-1")

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    PoleListItem("POLE-1", 1, "normal"),
                    PoleListItem("POLE-2", 2, "unknown"),
                ),
                result.getOrNull(),
            )
        }

    @Test
    fun `returns failure when the backend rejects the request`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            coEvery { api.poles("WO-1") } throws httpException(404)
            val repository = RealPoleListRepository(api)

            assertTrue(repository.poles("WO-1").isFailure)
        }
}
