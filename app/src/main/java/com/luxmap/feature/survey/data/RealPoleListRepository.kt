package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealPoleListRepository
    @Inject
    constructor(
        private val api: WorkOrdersApi,
    ) : PoleListRepository {
        override suspend fun poles(workOrderId: String): Result<List<PoleListItem>> =
            runCatching {
                api.poles(workOrderId).items.map { PoleListItem(it.poleId, it.position, it.fixtureStatus) }
            }
    }
