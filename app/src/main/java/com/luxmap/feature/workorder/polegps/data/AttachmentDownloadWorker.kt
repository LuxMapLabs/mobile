package com.luxmap.feature.workorder.polegps.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AttachmentDownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val downloader: AttachmentDownloader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val workOrderId = inputData.getString(KEY_WORK_ORDER_ID) ?: return Result.failure()
            return when (downloader.downloadPending(workOrderId)) {
                DownloadOutcome.DONE -> Result.success()
                DownloadOutcome.RETRY -> Result.retry()
            }
        }

        companion object {
            const val KEY_WORK_ORDER_ID = "work_order_id"
        }
    }
