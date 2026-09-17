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

        // mock-pole-detail.json only has one pole (POLE-0047) — the passed poleId is not used
        // yet, real backend call in RealPoleDetailRepository will use it as a path param.
        override fun observePoleDetail(poleId: String): Flow<PoleDetail> =
            flow {
                val text =
                    context.assets.open(MOCK_POLE_DETAIL_ASSET).bufferedReader().use { it.readText() }
                val dto = json.decodeFromString<PoleDetailDto>(text)
                emit(dto.toPoleDetail())
            }

        private companion object {
            const val MOCK_POLE_DETAIL_ASSET = "mock-pole-detail.json"
        }
    }
