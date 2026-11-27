package com.luxmap.feature.history.data

import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeHistory(): Flow<HistoryResult>
}
