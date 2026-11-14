package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.lifecycle.SavedStateHandle
import com.luxmap.feature.workorder.polegps.data.AttachmentStatus
import com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val WORK_ORDER_ID = "WO-1"
private const val ATTACHMENT_ID = "A-1"

private fun attachment(
    status: AttachmentStatus,
    localPath: String? = null,
) = PoleGpsAttachment(ATTACHMENT_ID, "qr.pdf", 2048L, status, localPath)

private fun viewModel(repository: PoleGpsRepository) =
    PoleGpsPdfViewerViewModel(
        SavedStateHandle(mapOf("workOrderId" to WORK_ORDER_ID, "attachmentId" to ATTACHMENT_ID)),
        repository,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PoleGpsPdfViewerViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a ready attachment is Ready with its file name and local path`() {
        val repository = mockk<PoleGpsRepository>()
        every { repository.observeAttachments(WORK_ORDER_ID) } returns
            flowOf(listOf(attachment(AttachmentStatus.READY, "/files/qr.pdf")))
        val vm = viewModel(repository)

        val state = vm.state.value as PoleGpsPdfViewerUiState.Ready
        assertEquals("qr.pdf", state.fileName)
        assertEquals("/files/qr.pdf", state.filePath)
    }

    @Test
    fun `an attachment this id does not match is Empty`() {
        val repository = mockk<PoleGpsRepository>()
        every { repository.observeAttachments(WORK_ORDER_ID) } returns
            flowOf(listOf(attachment(AttachmentStatus.READY, "/files/other.pdf").copy(attachmentId = "A-OTHER")))
        val vm = viewModel(repository)

        assertEquals(PoleGpsPdfViewerUiState.Empty, vm.state.value)
    }

    @Test
    fun `a failed download is Error with retry allowed`() {
        val repository = mockk<PoleGpsRepository>()
        every { repository.observeAttachments(WORK_ORDER_ID) } returns
            flowOf(listOf(attachment(AttachmentStatus.FAILED)))
        val vm = viewModel(repository)

        val state = vm.state.value as PoleGpsPdfViewerUiState.Error
        assertTrue(state.canRetry)
    }

    @Test
    fun `a still-downloading attachment is Error without retry, it is already in progress`() {
        val repository = mockk<PoleGpsRepository>()
        every { repository.observeAttachments(WORK_ORDER_ID) } returns
            flowOf(listOf(attachment(AttachmentStatus.DOWNLOADING)))
        val vm = viewModel(repository)

        val state = vm.state.value as PoleGpsPdfViewerUiState.Error
        assertEquals(false, state.canRetry)
    }

    @Test
    fun `onRetry calls the repository with this work order and attachment id`() =
        runTest {
            val repository = mockk<PoleGpsRepository>(relaxed = true)
            every { repository.observeAttachments(WORK_ORDER_ID) } returns
                flowOf(listOf(attachment(AttachmentStatus.FAILED)))
            val vm = viewModel(repository)

            vm.onRetry()

            coVerify { repository.retryAttachment(WORK_ORDER_ID, ATTACHMENT_ID) }
        }
}
