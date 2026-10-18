package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealPoleListRepository
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PoleListCacheDao,
    ) : PoleListRepository {
        override suspend fun poles(workOrderId: String): Result<PoleListResult> =
            runCatching {
                val items = api.poles(workOrderId).items.map { PoleListItem(it.poleId, it.position, it.fixtureStatus) }
                dao.replaceForWorkOrder(workOrderId, items.map { it.toEntity(workOrderId) })
                PoleListResult(items, isStale = false)
            }.recoverCatching { e ->
                // Offline-first fallback (CLAUDE.md A4/A6): a failed fetch still shows the last
                // known list instead of a blank error screen, marked isStale so the UI can say so.
                val cached = dao.byWorkOrder(workOrderId).map { it.toDomain() }
                if (cached.isEmpty()) throw e
                PoleListResult(cached, isStale = true)
            }

        private fun PoleListItem.toEntity(workOrderId: String) =
            LocalPoleListItemEntity(workOrderId, poleId, position, fixtureStatus)

        private fun LocalPoleListItemEntity.toDomain() = PoleListItem(poleId, position, fixtureStatus)
    }
