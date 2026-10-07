package com.luxmap.core.sync

interface SyncOpHandler {
    val opType: String

    suspend fun handle(payloadJson: String): SyncOpResult
}

sealed interface SyncOpResult {
    data object Done : SyncOpResult

    data object RetryLater : SyncOpResult

    data class Failed(val message: String) : SyncOpResult

    data class Conflict(val message: String) : SyncOpResult
}
