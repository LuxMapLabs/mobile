package com.luxmap.feature.workorder.polegps.data

import com.luxmap.core.network.WorkOrdersApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AttachmentDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val api = mockk<WorkOrdersApi>()
    private val dao = mockk<PoleGpsDao>(relaxed = true)

    private fun attachment(
        id: String,
        status: String = AttachmentStatusColumn.PENDING,
    ) = LocalWorkOrderAttachmentEntity(
        "WO-1",
        id,
        "$id.pdf",
        3L,
        "/api/v1/work-orders/WO-1/attachments/$id",
        status,
        null,
    )

    private fun downloader() = AttachmentDownloader(api, dao, PoleGpsFiles(tmp.root))

    private fun pdf(text: String): ResponseBody = text.toResponseBody("application/pdf".toMediaType())

    private fun notFound() =
        HttpException(
            Response.error<ResponseBody>(404, "{}".toResponseBody("application/json".toMediaType())),
        )

    @Test
    fun `successful download writes the file and marks it ready`() =
        runTest {
            coEvery { dao.attachments("WO-1") } returns listOf(attachment("A-1"))
            coEvery { api.downloadAttachment(any()) } returns pdf("abc")
            val outcome = downloader().downloadPending("WO-1")
            assertEquals(DownloadOutcome.DONE, outcome)
            val path = PoleGpsFiles(tmp.root).fileFor("WO-1", "A-1")
            assertEquals("abc", path.readText())
            coVerify { dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.READY, path.absolutePath) }
        }

    @Test
    fun `a removed file is marked failed and the others still download`() =
        runTest {
            coEvery { dao.attachments("WO-1") } returns listOf(attachment("A-1"), attachment("A-2"))
            coEvery { api.downloadAttachment("/api/v1/work-orders/WO-1/attachments/A-1") } throws notFound()
            coEvery { api.downloadAttachment("/api/v1/work-orders/WO-1/attachments/A-2") } returns pdf("ok")
            val outcome = downloader().downloadPending("WO-1")
            assertEquals(DownloadOutcome.DONE, outcome)
            coVerify { dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.FAILED, null) }
            assertTrue(PoleGpsFiles(tmp.root).fileFor("WO-1", "A-2").exists())
        }

    @Test
    fun `a network error keeps the file pending and asks for a retry`() =
        runTest {
            coEvery { dao.attachments("WO-1") } returns listOf(attachment("A-1"))
            coEvery { api.downloadAttachment(any()) } throws IOException("offline")
            val outcome = downloader().downloadPending("WO-1")
            assertEquals(DownloadOutcome.RETRY, outcome)
            coVerify { dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.PENDING, null) }
        }

    @Test
    fun `ready files are not downloaded again`() =
        runTest {
            coEvery { dao.attachments("WO-1") } returns listOf(attachment("A-1", AttachmentStatusColumn.READY))
            downloader().downloadPending("WO-1")
            coVerify(exactly = 0) { api.downloadAttachment(any()) }
        }

    @Test
    fun `retry puts a failed file back to pending`() =
        runTest {
            downloader().retry("WO-1", "A-1")
            coVerify { dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.PENDING, null) }
        }
}
