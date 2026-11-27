package com.luxmap.feature.workorder.ui.detail

import com.luxmap.feature.survey.data.PoleListItem
import com.luxmap.feature.survey.data.PoleListResult
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WorkOrderPolesTest {
    @get:Rule val main = DetailMainDispatcherRule()

    private val poles =
        listOf(
            PoleListItem("P2", 9, 10.0, 106.0, "normal", note = "Cạnh cầu"),
            PoleListItem("P1", 1, 11.0, 107.0, "out"),
        )

    @Test
    fun `inspection and repair load enriched poles before start in API order`() =
        runTest {
            for (kind in listOf("inspection", "repair")) {
                val fixture = DetailViewModelFixture()
                coEvery { fixture.poles.poles("W") } returns Result.success(PoleListResult(poles, false))
                val vm =
                    fixture.create(
                        fixture.detail(kind).copy(woStatus = "assigned", allowedActions = listOf("start")),
                    )
                advanceUntilIdle()
                assertEquals(OrderPolesUiState.Success(poles, false), vm.success.orderPoles)
                assertTrue(vm.success.detail.faults.isEmpty())
                coVerify(exactly = 1) { fixture.poles.poles("W") }
            }
        }

    @Test
    fun `unknown kinds do not fetch detail poles`() =
        runTest {
            for (kind in listOf("unknown")) {
                val fixture = DetailViewModelFixture()
                fixture.create(fixture.detail(kind))
                advanceUntilIdle()
                coVerify(exactly = 0) { fixture.poles.poles(any()) }
            }
        }

    @Test
    fun `slow poles never block the detail and a failed fetch can retry inline`() =
        runTest {
            val fixture = DetailViewModelFixture()
            val pending = CompletableDeferred<Unit>()
            coEvery { fixture.poles.poles("W") } coAnswers {
                pending.await()
                Result.failure(IOException("offline"))
            }
            val vm = fixture.create()
            runCurrent()
            assertEquals(OrderPolesUiState.Loading, vm.success.orderPoles)
            vm.onReportNoteChanged("Nội dung đang nhập")
            pending.complete(Unit)
            runCurrent()
            assertEquals(OrderPolesUiState.Error("offline"), vm.success.orderPoles)
            coEvery { fixture.poles.poles("W") } returns Result.success(PoleListResult(poles, true))
            vm.retryPoles()
            advanceUntilIdle()
            assertEquals(OrderPolesUiState.Success(poles, true), vm.success.orderPoles)
            assertEquals("Nội dung đang nhập", vm.success.reportNote)
        }

    @Test
    fun `empty list is an inline empty state`() =
        runTest {
            val vm = DetailViewModelFixture().create()
            advanceUntilIdle()
            assertEquals(OrderPolesUiState.Empty, vm.success.orderPoles)
        }
}
