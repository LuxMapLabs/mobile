package com.luxmap.feature.map.ui

import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.PoleMarker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Chuyển List<PoleMarker> (domain model) thành chuỗi GeoJSON để đưa vào GeoJsonSource của
// MapLibre — chỉ giữ 2 field UI cần cho match expression (fixture_status) và bottom sheet
// (pole_id), không phải bản sao đầy đủ của PoleFeatureDto.
private val renderJson = Json { encodeDefaults = true }

fun List<PoleMarker>.toGeoJson(): String =
    renderJson.encodeToString(
        RenderFeatureCollection.serializer(),
        RenderFeatureCollection(features = map { it.toRenderFeature() }),
    )

private fun PoleMarker.toRenderFeature() =
    RenderFeature(
        geometry = RenderGeometry(coordinates = listOf(lng, lat)),
        properties =
            RenderProperties(
                poleId = poleId,
                fixtureStatus = fixtureStatus.toGeoJsonValue(),
            ),
    )

private fun AssetCondition.toGeoJsonValue(): String =
    when (this) {
        AssetCondition.NORMAL -> "normal"
        AssetCondition.DIM -> "dim"
        AssetCondition.OUT -> "out"
        AssetCondition.UNKNOWN -> "unknown"
    }

@Serializable
private data class RenderFeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<RenderFeature>,
)

@Serializable
private data class RenderFeature(
    val type: String = "Feature",
    val geometry: RenderGeometry,
    val properties: RenderProperties,
)

@Serializable
private data class RenderGeometry(
    val type: String = "Point",
    val coordinates: List<Double>,
)

@Serializable
private data class RenderProperties(
    @SerialName("pole_id") val poleId: String,
    @SerialName("fixture_status") val fixtureStatus: String,
)
