package com.luxmap.feature.home.data

import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    fun observeHomeData(): Flow<HomeData>
}
