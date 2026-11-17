package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoItemDto
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolePhotoWithdrawal
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PolePhotoDao,
    ) {
        internal val mediaMutex = Mutex()

        suspend fun withdrawReplacedMedia(
            workOrderId: String,
            poleId: String,
        ) {
            val active = listMedia(workOrderId, poleId)
            val activeIds = active.mapTo(mutableSetOf()) { it.photoId }
            val absent =
                dao.byPole(workOrderId, poleId)
                    .filter { it.serverPhotoId != null && it.serverPhotoId !in activeIds }
            if (absent.isNotEmpty()) dao.deleteByIds(absent.map { it.photoId })
            active.dropLast(1).filter { it.decision == null }.forEach { old ->
                api.withdrawPolePhoto(workOrderId, old.photoId)
                dao.deleteByServerPhotoId(workOrderId, old.photoId)
            }
        }

        suspend fun withdrawPole(
            workOrderId: String,
            poleId: String,
            selectedPhotoId: String,
        ) {
            val local = dao.byPole(workOrderId, poleId)
            val selected =
                local.firstOrNull()?.takeIf { it.serverPhotoId == selectedPhotoId }
                    ?: error("Kết quả đang xem không còn là bản mới nhất.")
            val active = listMedia(workOrderId, poleId)
            val current = active.firstOrNull { it.photoId == selectedPhotoId }
            val isStale =
                if (current != null) {
                    current != active.last()
                } else {
                    active.any { !Instant.parse(it.capturedAt).isBefore(selected.capturedAt) }
                }
            check(!isStale) { "Kết quả đang xem không còn là bản mới nhất." }
            check(current?.decision == null) { "Kết quả này đã được quản lý duyệt." }
            active.filter { it.photoId != selectedPhotoId && it.decision == null }.forEach { old ->
                api.withdrawPolePhoto(workOrderId, old.photoId)
            }
            api.withdrawPolePhoto(workOrderId, selectedPhotoId)
            dao.deleteByIds(local.map { it.photoId })
        }

        private suspend fun listMedia(
            workOrderId: String,
            poleId: String,
        ): List<PolePhotoItemDto> {
            val items = mutableListOf<PolePhotoItemDto>()
            var page = 1
            do {
                val response = api.listPolePhotos(workOrderId, poleId, page, PAGE_SIZE)
                items.addAll(response.items)
                page++
            } while (response.items.isNotEmpty() && items.size < response.total)
            return items
        }

        private companion object {
            const val PAGE_SIZE = 100
        }
    }
