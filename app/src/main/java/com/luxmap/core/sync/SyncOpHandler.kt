package com.luxmap.core.sync

interface SyncOpHandler {
    val opType: String

    // suspend, not a plain callback, so a caller can emit from a kotlinx.coroutines Flow
    // builder directly inside onProgress instead of needing a channel-backed workaround.
    suspend fun handle(
        payloadJson: String,
        onProgress: suspend (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): SyncOpResult
}

sealed interface SyncOpResult {
    data object Done : SyncOpResult

    data object RetryLater : SyncOpResult

    data class Failed(val message: String) : SyncOpResult

    data class Conflict(val message: String) : SyncOpResult
}
