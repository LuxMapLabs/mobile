package com.luxmap.feature.workorder.ui.detail

import com.luxmap.feature.workorder.data.WorkOrderDraft
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WorkOrderDraftTest {
    @get:Rule val main = DetailMainDispatcherRule()

    private val draft = WorkOrderDraft("Đã kiểm tra hiện trường", "1 bóng LED", mapOf("F1" to "fault_present"))

    @Test
    fun `slow restore never overwrites newer typing`() =
        runTest {
            val fixture = DetailViewModelFixture()
            val load = CompletableDeferred<WorkOrderDraft?>()
            coEvery { fixture.drafts.load("U", "W") } coAnswers { load.await() }
            val vm = fixture.create()
            runCurrent()
            vm.onReportNoteChanged("Ghi chú vừa nhập")
            load.complete(draft)
            runCurrent()
            assertEquals("Ghi chú vừa nhập", vm.success.reportNote)
            advanceUntilIdle()
            coVerify { fixture.drafts.save("U", "W", WorkOrderDraft("Ghi chú vừa nhập", "", emptyMap())) }
        }

    @Test
    fun `an in flight navigation flush finishes before submit deletes the draft`() =
        runTest {
            val fixture = DetailViewModelFixture()
            val finishWrite = CompletableDeferred<Unit>()
            val operations = mutableListOf<String>()
            coEvery { fixture.drafts.save(any(), any(), any()) } coAnswers {
                finishWrite.await()
                operations += "saved"
            }
            coEvery { fixture.drafts.delete("U", "W") } coAnswers { operations += "deleted" }
            val vm = fixture.create()
            runCurrent()
            vm.onReportNoteChanged(draft.reportNote)
            backgroundScope.launch { vm.flushDraft() }
            runCurrent()
            vm.submit()
            runCurrent()
            assertTrue(operations.isEmpty())
            finishWrite.complete(Unit)
            runCurrent()
            advanceUntilIdle()
            assertEquals(listOf("saved", "deleted"), operations)
            assertFalse(vm.success.hasDraft)
            assertTrue(vm.success.justSubmitted)
        }

    @Test
    fun `typing debounces for 500ms and saves the latest complete form once`() =
        runTest {
            val fixture = DetailViewModelFixture()
            val vm = fixture.create()
            runCurrent()
            vm.onReportNoteChanged("Đang nhập")
            runCurrent()
            advanceTimeBy(300)
            vm.onReportNoteChanged(draft.reportNote)
            vm.onMaterialsUsedChanged(draft.materialsUsed)
            vm.onFaultOutcomeSelected("F1", "fault_present")
            runCurrent()
            advanceTimeBy(499)
            runCurrent()
            coVerify(exactly = 0) { fixture.drafts.save(any(), any(), any()) }
            assertFalse(vm.success.hasDraft)
            advanceTimeBy(1)
            runCurrent()
            coVerify(exactly = 1) { fixture.drafts.save("U", "W", draft) }
            assertTrue(vm.success.hasDraft)
        }

    @Test
    fun `a recreated viewmodel restores persisted text materials and outcomes`() =
        runTest {
            val fixture = DetailViewModelFixture()
            var stored: WorkOrderDraft? = null
            coEvery { fixture.drafts.save("U", "W", any()) } coAnswers { stored = thirdArg() }
            coEvery { fixture.drafts.load("U", "W") } coAnswers { stored }
            val first = fixture.create()
            runCurrent()
            first.onReportNoteChanged(draft.reportNote)
            first.onMaterialsUsedChanged(draft.materialsUsed)
            first.onFaultOutcomeSelected("F1", "fault_present")
            advanceUntilIdle()
            val reopened = fixture.create()
            runCurrent()
            assertEquals(draft.reportNote, reopened.success.reportNote)
            assertEquals(draft.materialsUsed, reopened.success.materialsUsed)
            assertEquals(draft.faultOutcomes, reopened.success.faultOutcomes)
            assertTrue(reopened.success.hasDraft)
        }

    @Test
    fun `an order past its scheduled date still restores its draft`() =
        runTest {
            val fixture = DetailViewModelFixture()
            coEvery { fixture.drafts.load("U", "W") } returns draft
            val vm = fixture.create(fixture.detail().copy(scheduledDate = LocalDate.now().minusDays(1).toString()))
            runCurrent()
            assertTrue(vm.success.hasDraft)
        }

    @Test
    fun `finished and already submitted orders do not restore or save drafts`() =
        runTest {
            val fixture = DetailViewModelFixture()
            coEvery { fixture.drafts.load("U", "W") } returns draft
            val details = listOf(fixture.detail().copy(woStatus = "done", allowedActions = emptyList()))
            for (detail in details) {
                val vm = fixture.create(detail)
                runCurrent()
                assertFalse(vm.success.hasDraft)
                vm.onReportNoteChanged("Không lưu lệnh đã khoá")
                advanceUntilIdle()
            }
            fixture.completion.value = LocalWorkOrderCompletionEntity("W", "saved", null, null, "OP", "pending")
            val submitted = fixture.create()
            runCurrent()
            assertFalse(submitted.success.hasDraft)
            coVerify(exactly = 0) { fixture.drafts.load(any(), any()) }
            coVerify(exactly = 0) { fixture.drafts.save(any(), any(), any()) }
        }

    @Test
    fun `submit cancels an imminent autosave then deletes the draft with no later write`() =
        runTest {
            val fixture = DetailViewModelFixture()
            coEvery { fixture.drafts.load("U", "W") } returns draft
            val vm = fixture.create()
            runCurrent()
            vm.onReportNoteChanged("Đã hoàn tất kiểm tra mới")
            runCurrent()
            advanceTimeBy(499)
            vm.submit()
            advanceUntilIdle()
            coVerify(exactly = 1) { fixture.drafts.delete("U", "W") }
            coVerify(exactly = 0) { fixture.drafts.save(any(), any(), any()) }
            assertFalse(vm.success.hasDraft)
            assertTrue(vm.success.justSubmitted)
        }

    @Test
    fun `failed submit preserves the draft and its autosave`() =
        runTest {
            val fixture = DetailViewModelFixture()
            coEvery { fixture.completions.submitCompletion(any(), any(), any(), any(), any()) } throws
                IOException("disk full")
            val vm = fixture.create()
            runCurrent()
            vm.onReportNoteChanged(draft.reportNote)
            vm.submit()
            advanceUntilIdle()
            coVerify(exactly = 0) { fixture.drafts.delete(any(), any()) }
            coVerify(exactly = 1) { fixture.drafts.save("U", "W", WorkOrderDraft(draft.reportNote, "", emptyMap())) }
            assertTrue(vm.success.hasDraft)
        }

    @Test
    fun `leaving flushes before debounce expires`() =
        runTest {
            val fixture = DetailViewModelFixture()
            val vm = fixture.create()
            runCurrent()
            vm.onReportNoteChanged("Ghi chú trước khi mở camera")
            vm.flushDraft()
            coVerify(exactly = 1) { fixture.drafts.save("U", "W", any()) }
            advanceUntilIdle()
            coVerify(exactly = 1) { fixture.drafts.save("U", "W", any()) }
        }

    @Test
    fun `draft save failure keeps form and never claims draft was saved`() =
        runTest {
            val fixture = DetailViewModelFixture()
            coEvery { fixture.drafts.save(any(), any(), any()) } throws
                IOException("disk full")
            val vm = fixture.create()
            runCurrent()
            vm.onReportNoteChanged(draft.reportNote)
            advanceUntilIdle()
            assertFalse(vm.success.hasDraft)
            assertEquals(draft.reportNote, vm.success.reportNote)
            assertEquals("Chưa lưu được bản nháp trên máy", vm.success.draftError)
        }
}
