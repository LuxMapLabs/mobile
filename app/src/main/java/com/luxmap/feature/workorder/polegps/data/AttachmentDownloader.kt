package com.luxmap.feature.workorder.polegps.data

import com.luxmap.core.network.WorkOrdersApi
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadOutcome { DONE, RETRY }

@Singleton
class AttachmentDownloader
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PoleGpsDao,
        private val files: PoleGpsFiles,
    ) {
        suspend fun downloadPending(workOrderId: String): DownloadOutcome {
            var outcome = DownloadOutcome.DONE
            dao
                .attachments(workOrderId)
                .filter {
                    it.status == AttachmentStatusColumn.PENDING || it.status == AttachmentStatusColumn.DOWNLOADING
                }
                .forEach { item ->
                    if (!download(item)) outcome = DownloadOutcome.RETRY
                }
            return outcome
        }

        suspend fun retry(
            workOrderId: String,
            attachmentId: String,
        ) {
            dao.updateAttachmentStatus(workOrderId, attachmentId, AttachmentStatusColumn.PENDING, null)
        }

        private suspend fun download(item: LocalWorkOrderAttachmentEntity): Boolean {
            dao.updateAttachmentStatus(item.workOrderId, item.attachmentId, AttachmentStatusColumn.DOWNLOADING, null)
            val target = files.fileFor(item.workOrderId, item.attachmentId)
            return try {
                write(target) { api.downloadAttachment(item.downloadUrl).use { body -> body.byteStream().copyTo(it) } }
                dao.updateAttachmentStatus(
                    item.workOrderId,
                    item.attachmentId,
                    AttachmentStatusColumn.READY,
                    target.absolutePath,
                )
                true
            } catch (e: HttpException) {
                val permanent = e.code() in 400..499
                val status = if (permanent) AttachmentStatusColumn.FAILED else AttachmentStatusColumn.PENDING
                dao.updateAttachmentStatus(item.workOrderId, item.attachmentId, status, null)
                permanent
            } catch (e: IOException) {
                dao.updateAttachmentStatus(item.workOrderId, item.attachmentId, AttachmentStatusColumn.PENDING, null)
                false
            }
        }

        private inline fun write(
            target: File,
            block: (java.io.OutputStream) -> Unit,
        ) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.part")
            try {
                temp.outputStream().use(block)
                if (!temp.renameTo(target)) throw IOException("rename failed")
            } finally {
                temp.delete()
            }
        }
    }
