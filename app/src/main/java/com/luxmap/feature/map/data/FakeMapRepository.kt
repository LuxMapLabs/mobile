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

        override fun observePoles(): Flow<List<PoleMarker>> =
            flow {
                val text =
                    context.assets.open(MOCK_POLES_ASSET).bufferedReader().use { it.readText() }
                val collection = json.decodeFromString<PoleFeatureCollectionDto>(text)
                emit(collection.features.map { it.toPoleMarker() })
            }

        override fun observeRoadSegments(): Flow<List<RoadSegmentLine>> =
            flow {
                val segmentsText =
                    context.assets.open(MOCK_ROAD_SEGMENTS_ASSET).bufferedReader().use { it.readText() }
                val segments = json.decodeFromString<RoadSegmentFeatureCollectionDto>(segmentsText).features

                // Always use the LineString from mock-segments.geojson as the route geometry —
                // do NOT connect pole points by segment_id. Poles are not always recorded in
                // route order, so joining them can draw a zig-zag line instead of the real road
                // shape. The backend (GET /api/v1/segments) returns this same geometry directly.
                emit(
                    segments.map { segment ->
                        RoadSegmentLine(
                            segmentId = segment.properties.segmentId,
                            name = segment.properties.segmentName,
                            hasActiveSegmentFault = segment.properties.hasActiveSegmentFault,
                            coordinates = segment.geometry.coordinates.map { (lng, lat) -> lng to lat },
                        )
                    },
                )
            }

        private companion object {
            const val MOCK_POLES_ASSET = "mock-poles.geojson"
            const val MOCK_ROAD_SEGMENTS_ASSET = "mock-segments.geojson"
        }
    }
