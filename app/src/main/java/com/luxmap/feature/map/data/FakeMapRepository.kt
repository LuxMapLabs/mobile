package com.luxmap.feature.map.data

import android.content.Context
import com.luxmap.feature.map.data.dto.PoleFeatureCollectionDto
import com.luxmap.feature.map.data.dto.RoadSegmentFeatureCollectionDto
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

        // Route geometry always comes from mock-segments.geojson's own LineString — do NOT
        // connect pole points by segment_id. Poles are not always recorded in route order, so
        // joining them can draw a zig-zag line instead of the real road shape. The backend
        // (GET /api/v1/segments) returns this same geometry directly.
        override fun observeGisMapDataset(): Flow<GisMapDataset> =
            flow {
                val polesText =
                    context.assets.open(MOCK_POLES_ASSET).bufferedReader().use { it.readText() }
                val poles =
                    json.decodeFromString<PoleFeatureCollectionDto>(polesText).features
                        .map { it.toPoleMarker() }

                val segmentsText =
                    context.assets.open(MOCK_ROAD_SEGMENTS_ASSET).bufferedReader().use { it.readText() }
                val segments =
                    json.decodeFromString<RoadSegmentFeatureCollectionDto>(segmentsText).features
                        .map { it.toRoadSegmentLine() }

                emit(GisMapDataset(poles = poles, segments = segments))
            }

        private companion object {
            const val MOCK_POLES_ASSET = "mock-poles.geojson"
            const val MOCK_ROAD_SEGMENTS_ASSET = "mock-segments.geojson"
        }
    }
