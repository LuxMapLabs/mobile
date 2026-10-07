package com.luxmap.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val processor: SyncQueueProcessor,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = if (processor.processQueuedOps()) Result.retry() else Result.success()
    }
