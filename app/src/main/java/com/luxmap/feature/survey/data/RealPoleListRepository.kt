package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.dto.PoleListItemDto
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
                val firstPage = api.poles(workOrderId)
                val dtos = firstPage.items.toMutableList()
                var page = firstPage.page
                while (dtos.size < firstPage.total) {
                    val next = api.poles(workOrderId, page = ++page)
                    check(next.items.isNotEmpty()) { "Danh sách cột chưa tải đủ" }
                    dtos.addAll(next.items)
                }
                val items = dtos.map { it.toDomain() }
                dao.replaceForWorkOrder(workOrderId, items.map { it.toEntity(workOrderId) })
                PoleListResult(items, isStale = false)
            }.recoverCatching { e ->
                // Offline-first fallback (CLAUDE.md A4/A6): a failed fetch still shows the last
                // known list instead of a blank error screen, marked isStale so the UI can say so.
                val cached = dao.byWorkOrder(workOrderId).map { it.toDomain() }
                if (cached.isEmpty()) throw e
                PoleListResult(cached, isStale = true)
            }

        override suspend fun cachedPoles(workOrderId: String): List<PoleListItem> =
            dao.byWorkOrder(workOrderId).map { it.toDomain() }
    }

internal fun PoleListItemDto.toDomain() =
    PoleListItem(
        poleId, position, location?.lat, location?.lng, fixtureStatus,
        segmentId = segmentId,
        note = note,
        fixtureType = fixtureType,
        lampWatt = lampWatt,
        lampCount = lampCount,
        fixtureCount = fixtureCount,
        nearSensitivePoi = nearSensitivePoi,
    )

internal fun PoleListItem.toEntity(workOrderId: String) =
    LocalPoleListItemEntity(
        workOrderId, poleId, position, lat, lng, fixtureStatus,
        segmentId = segmentId,
        note = note,
        fixtureType = fixtureType,
        lampWatt = lampWatt,
        lampCount = lampCount,
        fixtureCount = fixtureCount,
        nearSensitivePoi = nearSensitivePoi,
    )

internal fun LocalPoleListItemEntity.toDomain() =
    PoleListItem(
        poleId, position, lat, lng, fixtureStatus,
        segmentId = segmentId,
        note = note,
        fixtureType = fixtureType,
        lampWatt = lampWatt,
        lampCount = lampCount,
        fixtureCount = fixtureCount,
        nearSensitivePoi = nearSensitivePoi,
    )
