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
                val polesText =
                    context.assets.open(MOCK_POLES_ASSET).bufferedReader().use { it.readText() }
                val poles = json.decodeFromString<PoleFeatureCollectionDto>(polesText).features

                val segmentsText =
                    context.assets.open(MOCK_ROAD_SEGMENTS_ASSET).bufferedReader().use { it.readText() }
                val segments = json.decodeFromString<RoadSegmentFeatureCollectionDto>(segmentsText).features

                // Fake-only: build each line by connecting poles that share segment_id, in the
                // order they appear in mock-poles.geojson, so the line passes through the same
                // markers shown on the map (matches how the route looks on Web). The real backend
                // must keep returning RoadSegment geometry directly (GET /api/v1/segments) — do
                // not copy this pole-based construction into RealMapRepository.
                val poleCoordinatesBySegment =
                    poles.groupBy({ it.properties.segmentId }) { pole ->
                        val (lng, lat) = pole.geometry.coordinates
                        lng to lat
                    }

                emit(
                    segments.map { segment ->
                        RoadSegmentLine(
                            segmentId = segment.properties.segmentId,
                            name = segment.properties.segmentName,
                            coordinates =
                                poleCoordinatesBySegment[segment.properties.segmentId]
                                    ?: segment.geometry.coordinates.map { (lng, lat) -> lng to lat },
                        )
                    },
                )
            }

        private companion object {
            const val MOCK_POLES_ASSET = "mock-poles.geojson"
            const val MOCK_ROAD_SEGMENTS_ASSET = "mock-segments.geojson"
        }
    }
