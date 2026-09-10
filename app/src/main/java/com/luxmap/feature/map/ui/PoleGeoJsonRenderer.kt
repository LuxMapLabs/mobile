package com.luxmap.feature.map.ui

import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.PoleMarker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Turn List<PoleMarker> (domain model) into a GeoJSON string for MapLibre's GeoJsonSource —
// keep only the UI fields needed for the match expression (fixture_status), the bottom sheet
// (pole_id), and the 2 POI/IoT badge markers (has_iot_node, near_sensitive_poi) — not a full
// copy of PoleFeatureDto.
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
                hasIotNode = hasIotNode,
                nearSensitivePoi = nearSensitivePoi,
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
    @SerialName("has_iot_node") val hasIotNode: Boolean,
    @SerialName("near_sensitive_poi") val nearSensitivePoi: Boolean,
)
