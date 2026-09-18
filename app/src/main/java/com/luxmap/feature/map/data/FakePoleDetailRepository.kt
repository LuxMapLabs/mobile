package com.luxmap.feature.map.data

import android.content.Context
import com.luxmap.feature.map.data.dto.PoleDetailDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePoleDetailRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PoleDetailRepository {
        private val json = Json { ignoreUnknownKeys = true }

        // Only these 3 poles (1 per fixture_status we care about) have a mock detail file —
        // every other pole_id on the map correctly falls through to null/Empty instead of
        // silently showing POLE-0047's data like the previous version did.
        override fun observePoleDetail(poleId: String): Flow<PoleDetail?> =
            flow {
                val assetName = MOCK_ASSET_BY_POLE_ID[poleId]
                if (assetName == null) {
                    emit(null)
                    return@flow
                }
                val text = context.assets.open(assetName).bufferedReader().use { it.readText() }
                val dto = json.decodeFromString<PoleDetailDto>(text)
                emit(dto.toPoleDetail())
            }

        private companion object {
            val MOCK_ASSET_BY_POLE_ID =
                mapOf(
                    "POLE-0047" to "mock-pole-detail-POLE-0047.json",
                    "POLE-0001" to "mock-pole-detail-POLE-0001.json",
                    "POLE-0014" to "mock-pole-detail-POLE-0014.json",
                )
        }
    }
