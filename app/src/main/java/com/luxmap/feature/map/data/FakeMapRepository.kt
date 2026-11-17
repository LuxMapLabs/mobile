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

                loadedPoles = poles
                emit(
                    GisMapDataset(
                        poles = poles,
                        segments = segments,
                        cabinets = sampleCabinets(poles),
                        communes = sampleCommunes(poles),
                    ),
                )
            }

        private var loadedPoles: List<PoleMarker> = emptyList()

        override suspend fun cabinetTopology(cabinetId: String): CabinetTopology {
            val cabinet =
                sampleCabinets(loadedPoles).firstOrNull { it.cabinetId == cabinetId }
                    ?: throw CabinetTopologyException.NotInScope()
            var from = cabinet.lng to cabinet.lat
            val edges =
                loadedPoles.take(SAMPLE_TOPOLOGY_POLES).map { pole ->
                    val to = pole.lng to pole.lat
                    TopologyEdge(
                        feederId = cabinet.feeders.last().feederId,
                        toPoleId = pole.poleId,
                        coordinates = listOf(from, to),
                    )
                        .also { from = to }
                }
            return CabinetTopology(edges = edges, unlocatedPoleCount = 1, unroutedPoleCount = 0)
        }

        private fun sampleCabinets(poles: List<PoleMarker>): List<CabinetMarker> {
            val anchor = poles.firstOrNull() ?: return emptyList()
            return listOf(
                CabinetMarker(
                    cabinetId = "CAB-0001",
                    name = "Tủ điện chợ",
                    communeId = "COM-001",
                    lat = anchor.lat + CABINET_OFFSET,
                    lng = anchor.lng,
                    feeders =
                        listOf(
                            CabinetFeeder("FDR-0001", 1, FeederMode.AUTO, FeederPower.ON),
                            CabinetFeeder("FDR-0002", 2, FeederMode.OFF, FeederPower.OFF),
                        ),
                    iotNodeCount = 1,
                ),
                CabinetMarker(
                    cabinetId = "CAB-0002",
                    name = "Tủ điện trường học",
                    communeId = "COM-001",
                    lat = anchor.lat - CABINET_OFFSET,
                    lng = anchor.lng + CABINET_OFFSET,
                    feeders = listOf(CabinetFeeder("FDR-0003", 1, FeederMode.AUTO, FeederPower.ON)),
                    iotNodeCount = 0,
                ),
            )
        }

        private fun sampleCommunes(poles: List<PoleMarker>): List<CommuneOutline> {
            if (poles.isEmpty()) return emptyList()
            val minLng = poles.minOf { it.lng } - COMMUNE_MARGIN
            val maxLng = poles.maxOf { it.lng } + COMMUNE_MARGIN
            val minLat = poles.minOf { it.lat } - COMMUNE_MARGIN
            val maxLat = poles.maxOf { it.lat } + COMMUNE_MARGIN
            val ring = listOf(minLng to minLat, maxLng to minLat, maxLng to maxLat, minLng to maxLat, minLng to minLat)
            return listOf(CommuneOutline(communeId = "COM-001", name = "Xã mẫu", polygons = listOf(listOf(ring))))
        }

        private companion object {
            const val MOCK_POLES_ASSET = "mock-poles.geojson"
            const val MOCK_ROAD_SEGMENTS_ASSET = "mock-segments.geojson"
            const val SAMPLE_TOPOLOGY_POLES = 3
            const val CABINET_OFFSET = 0.0008
            const val COMMUNE_MARGIN = 0.002
        }
    }
