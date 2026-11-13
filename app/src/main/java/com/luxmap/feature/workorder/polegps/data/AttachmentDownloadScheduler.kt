package com.luxmap.feature.workorder.polegps.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface AttachmentDownloadScheduler {
    fun schedule(workOrderId: String)
}

@Singleton
class WorkManagerAttachmentDownloadScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AttachmentDownloadScheduler {
        override fun schedule(workOrderId: String) {
            val request =
                OneTimeWorkRequestBuilder<AttachmentDownloadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(AttachmentDownloadWorker.KEY_WORK_ORDER_ID to workOrderId))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("pole-gps-pdf-$workOrderId", ExistingWorkPolicy.KEEP, request)
        }
    }
