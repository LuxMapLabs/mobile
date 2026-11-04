package com.luxmap.feature.map.data

import com.luxmap.core.network.MapApi
import com.luxmap.core.network.UserApi
import com.luxmap.core.network.errorMessageOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// map_bounds is null until a System Admin sets a frame for the caller's commune(s) on Web GIS
// (COMMUNE-BBOX, luxmap_backend AuthContracts.cs) — a real, distinct condition from "zero poles
// in a real area", surfaced as its own exception so the ViewModel can show a real explanation.
class MapBoundsUnavailableException : Exception(
    "Khu vực của bạn chưa được thiết lập khung bản đồ trên hệ thống — liên hệ Quản trị viên.",
)

// Calls the real GET /auth/me (for map_bounds) + GET /map/poles + GET /map/segments — one fixed
// bbox per map open, not a bbox per pan/zoom (FM-15 "Hướng B", see the design spec). Follows the
// same userApi.me() pattern RealHomeRepository/RealSurveyRepository/RealProfileRepository
// already use — no shared current-user cache exists in the app, so this is one more fresh call,
// matching the established convention rather than adding new caching infrastructure.
@Singleton
class RealMapRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val mapApi: MapApi,
    ) : MapRepository {
        override fun observeGisMapDataset(): Flow<GisMapDataset> =
            flow {
                try {
                    val mapBounds = userApi.me().mapBounds ?: throw MapBoundsUnavailableException()
                    val bbox = mapBounds.bbox
                    val bboxParam = "${bbox.minLng},${bbox.minLat},${bbox.maxLng},${bbox.maxLat}"

                    val poles = mapApi.poles(bbox = bboxParam).features.map { it.toPoleMarker() }
                    val segments = mapApi.segments(bbox = bboxParam).features.map { it.toRoadSegmentLine() }
                    val center = mapBounds.center.lat to mapBounds.center.lng
                    emit(GisMapDataset(poles = poles, segments = segments, center = center))
                } catch (e: MapBoundsUnavailableException) {
                    throw e
                } catch (e: HttpException) {
                    throw Exception(e.errorMessageOrNull() ?: "Không tải được dữ liệu bản đồ", e)
                } catch (e: IOException) {
                    throw Exception("Không có kết nối mạng. Vui lòng kiểm tra lại.", e)
                } catch (e: Exception) {
                    throw Exception("Không tải được dữ liệu bản đồ", e)
                }
            }
    }
