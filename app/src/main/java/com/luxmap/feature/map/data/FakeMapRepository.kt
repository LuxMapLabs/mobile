package com.luxmap.feature.map.data

import android.content.Context
import com.luxmap.feature.map.data.dto.PoleFeatureCollectionDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Đọc asset mock-poles.geojson (copy nguyên bản từ docs/mock-poles.geojson người dùng chuẩn
// bị theo Contract v1.1 §2.1) — dùng flow{} dù đọc cục bộ để MapUiState.Loading có ý nghĩa
// thật khi FM-15 thay bằng RealMapRepository gọi API.
@Singleton
class FakeMapRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MapRepository {
        private val json = Json { ignoreUnknownKeys = true }

        override fun observePoles(): Flow<List<PoleMarker>> =
            flow {
                val text =
                    context.assets.open(MOCK_POLES_ASSET).bufferedReader().use { it.readText() }
                val collection = json.decodeFromString<PoleFeatureCollectionDto>(text)
                emit(collection.features.map { it.toPoleMarker() })
            }

        private companion object {
            const val MOCK_POLES_ASSET = "mock-poles.geojson"
        }
    }
