package com.luxmap.core.common

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Wraps StatFs so ViewModels never take a raw Context (matches LocationTracker's existing
// pattern of wrapping an Android system call instead of injecting Context directly).
@Singleton
class StorageMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun freeBytes(): Long = StatFs(context.filesDir.path).availableBytes
    }
