package com.luxmap.feature.workorder.data

import com.luxmap.core.security.TokenStore
import com.luxmap.feature.workorder.data.dao.WorkOrderDraftDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderDraftEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealWorkOrderDraftRepository
    @Inject
    constructor(
        private val dao: WorkOrderDraftDao,
        private val tokenStore: TokenStore,
    ) : WorkOrderDraftRepository {
        override suspend fun ownerUserId(): String? = tokenStore.cachedUserId()

        override suspend fun load(
            ownerUserId: String,
            workOrderId: String,
        ): WorkOrderDraft? {
            checkOwner(ownerUserId)
            val row = dao.get(ownerUserId, workOrderId) ?: return null
            checkOwner(ownerUserId)
            return WorkOrderDraft(row.reportNote, row.materialsUsed, Json.decodeFromString(row.faultOutcomesJson))
        }

        override suspend fun save(
            ownerUserId: String,
            workOrderId: String,
            draft: WorkOrderDraft,
        ) {
            checkOwner(ownerUserId)
            dao.save(
                LocalWorkOrderDraftEntity(
                    ownerUserId,
                    workOrderId,
                    draft.reportNote,
                    draft.materialsUsed,
                    Json.encodeToString(draft.faultOutcomes),
                ),
            )
        }

        override suspend fun delete(
            ownerUserId: String,
            workOrderId: String,
        ) {
            checkOwner(ownerUserId)
            dao.delete(ownerUserId, workOrderId)
        }

        private suspend fun checkOwner(ownerUserId: String) {
            check(tokenStore.cachedUserId() == ownerUserId) { "Phiên đăng nhập đã thay đổi" }
        }
    }
