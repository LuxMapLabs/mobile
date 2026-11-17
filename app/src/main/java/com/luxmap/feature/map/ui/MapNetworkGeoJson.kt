package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.CabinetMarker
import com.luxmap.feature.map.data.CabinetTopology
import com.luxmap.feature.map.data.CommuneOutline
import com.luxmap.feature.map.data.FeederPower
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun List<CabinetMarker>.toCabinetGeoJson(): String =
    featureCollection(
        map { cabinet ->
            feature(
                geometry = geometry("Point", position(cabinet.lng to cabinet.lat)),
                properties =
                    buildJsonObject {
                        put("cabinet_id", cabinet.cabinetId)
                        put("has_power_off", cabinet.hasPowerOff)
                    },
            )
        },
    )

fun CabinetTopology.toTopologyGeoJson(cabinet: CabinetMarker): String {
    val powerByFeeder = cabinet.feeders.associate { it.feederId to it.power }
    return featureCollection(
        edges.map { edge ->
            feature(
                geometry = geometry("LineString", JsonArray(edge.coordinates.map(::position))),
                properties =
                    buildJsonObject {
                        put("to_pole_id", edge.toPoleId)
                        put("power", (powerByFeeder[edge.feederId] ?: FeederPower.UNKNOWN).name.lowercase())
                    },
            )
        },
    )
}

fun List<CommuneOutline>.toCommuneGeoJson(): String =
    featureCollection(
        map { commune ->
            feature(
                geometry =
                    geometry(
                        "MultiPolygon",
                        JsonArray(
                            commune.polygons.map { polygon ->
                                JsonArray(polygon.map { ring -> JsonArray(ring.map(::position)) })
                            },
                        ),
                    ),
                properties = buildJsonObject { put("commune_id", commune.communeId) },
            )
        },
    )

private fun position(point: Pair<Double, Double>) =
    JsonArray(
        listOf(JsonPrimitive(point.first), JsonPrimitive(point.second)),
    )

private fun geometry(
    type: String,
    coordinates: JsonArray,
) = buildJsonObject {
    put("type", type)
    put("coordinates", coordinates)
}

private fun feature(
    geometry: JsonObject,
    properties: JsonObject,
) = buildJsonObject {
    put("type", "Feature")
    put("geometry", geometry)
    put("properties", properties)
}

private fun featureCollection(features: List<JsonObject>): String =
    buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(features))
    }.toString()
