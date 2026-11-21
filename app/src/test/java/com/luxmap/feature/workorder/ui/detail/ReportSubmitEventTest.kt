package com.luxmap.feature.workorder.ui.detail

import app.cash.turbine.test
import io.mockk.coEvery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReportSubmitEventTest {
    @get:Rule val main = DetailMainDispatcherRule()

    @Test
    fun `success event waits for local persistence and is delivered once`() =
        runTest {
            val fixture = DetailViewModelFixture()
            val saved = CompletableDeferred<Unit>()
            coEvery { fixture.completions.submitCompletion(any(), any(), any(), any(), any()) } coAnswers {
                saved.await()
            }
            val vm = fixture.create()
            advanceUntilIdle()
            vm.onReportNoteChanged("Đã kiểm tra hiện trường")
            vm.submitEvents.test {
                vm.submit()
                runCurrent()
                assertTrue(vm.success.isSubmitting)
                assertFalse(vm.success.justSubmitted)
                expectNoEvents()
                saved.complete(Unit)
                runCurrent()
                assertEquals(ReportSubmitEvent.SavedLocally, awaitItem())
                assertTrue(vm.success.justSubmitted)
                expectNoEvents()
            }
        }

    @Test
    fun `failed local save emits only an error and keeps all typed fields`() =
        runTest {
            val fixture = DetailViewModelFixture()
            coEvery { fixture.completions.submitCompletion(any(), any(), any(), any(), any()) } throws
                IOException("disk full")
            val vm = fixture.create()
            advanceUntilIdle()
            vm.onReportNoteChanged("Đã kiểm tra hiện trường")
            vm.onMaterialsUsedChanged("1 bóng LED")
            vm.onFaultOutcomeSelected("F1", "fault_present")
            vm.submitEvents.test {
                vm.submit()
                advanceUntilIdle()
                assertEquals(ReportSubmitEvent.Failed("disk full"), awaitItem())
                expectNoEvents()
            }
            assertFalse(vm.success.isSubmitting)
            assertFalse(vm.success.justSubmitted)
            assertEquals("Đã kiểm tra hiện trường", vm.success.reportNote)
            assertEquals("1 bóng LED", vm.success.materialsUsed)
            assertEquals(mapOf("F1" to "fault_present"), vm.success.faultOutcomes)
            assertEquals("disk full", vm.success.submitError)
        }
}
