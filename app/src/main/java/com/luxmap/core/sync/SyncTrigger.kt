package com.luxmap.core.sync

interface SyncTrigger {
    suspend fun triggerNow()
}
