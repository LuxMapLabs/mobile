package com.luxmap.feature.map.ui

import androidx.compose.ui.graphics.toArgb
import com.luxmap.core.map.CABINET_FAULT_ICON_ID
import com.luxmap.core.map.CABINET_NORMAL_ICON_ID
import com.luxmap.core.map.MapMarkerColors
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

internal const val CABINETS_LAYER_ID = "cabinets-layer"
private const val CABINETS_SOURCE_ID = "cabinets-source"
private const val COMMUNES_SOURCE_ID = "communes-source"
private const val COMMUNES_FILL_LAYER_ID = "communes-fill-layer"
private const val COMMUNES_LINE_LAYER_ID = "communes-line-layer"
private const val TOPOLOGY_SOURCE_ID = "topology-source"
private const val TOPOLOGY_LINE_LAYER_ID = "topology-line-layer"
private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
private const val DIMMED_POLE_OPACITY = 0.35f

internal fun addNetworkSources(
    style: Style,
    cabinetsGeoJson: String,
    communesGeoJson: String,
) {
    style.addSource(GeoJsonSource(COMMUNES_SOURCE_ID, communesGeoJson))
    style.addSource(GeoJsonSource(TOPOLOGY_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
    style.addSource(GeoJsonSource(CABINETS_SOURCE_ID, cabinetsGeoJson))
}

internal fun setNetworkGeoJson(
    style: Style,
    cabinetsGeoJson: String,
    communesGeoJson: String,
) {
    (style.getSource(CABINETS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(cabinetsGeoJson)
    (style.getSource(COMMUNES_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(communesGeoJson)
}

internal fun setTopologyGeoJson(
    style: Style,
    topologyGeoJson: String?,
) {
    (style.getSource(TOPOLOGY_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(topologyGeoJson ?: EMPTY_FEATURE_COLLECTION)
}

internal fun addTopologyLayer(
    style: Style,
    belowLayerId: String?,
) {
    val layer =
        LineLayer(TOPOLOGY_LINE_LAYER_ID, TOPOLOGY_SOURCE_ID).withProperties(
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineColor(MapMarkerColors.FeederPowered.toArgb()),
        )
    val dashed =
        LineLayer("$TOPOLOGY_LINE_LAYER_ID-off", TOPOLOGY_SOURCE_ID)
            .withProperties(
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineColor(MapMarkerColors.FeederUnpowered.toArgb()),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
            ).apply { setFilter(Expression.eq(Expression.get("power"), Expression.literal("off"))) }
    layer.setFilter(Expression.neq(Expression.get("power"), Expression.literal("off")))
    if (belowLayerId != null && style.getLayer(belowLayerId) != null) {
        style.addLayerBelow(layer, belowLayerId)
        style.addLayerBelow(dashed, belowLayerId)
    } else {
        style.addLayer(layer)
        style.addLayer(dashed)
    }
}

internal fun showCommuneLayers(
    style: Style,
    show: Boolean,
    belowLayerId: String?,
) {
    val present = style.getLayer(COMMUNES_LINE_LAYER_ID) != null
    if (show && !present) {
        val fill =
            FillLayer(COMMUNES_FILL_LAYER_ID, COMMUNES_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(MapMarkerColors.CommuneOutline.toArgb()),
                PropertyFactory.fillOpacity(0f),
            )
        val line =
            LineLayer(COMMUNES_LINE_LAYER_ID, COMMUNES_SOURCE_ID).withProperties(
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineColor(MapMarkerColors.CommuneOutline.toArgb()),
            )
        val below = belowLayerId?.takeIf { style.getLayer(it) != null }
        if (below != null) {
            style.addLayerBelow(fill, below)
            style.addLayerBelow(line, below)
        } else {
            style.addLayer(fill)
            style.addLayer(line)
        }
    } else if (!show && present) {
        style.removeLayer(COMMUNES_LINE_LAYER_ID)
        style.removeLayer(COMMUNES_FILL_LAYER_ID)
    }
}

internal fun showCabinetLayer(
    style: Style,
    show: Boolean,
) {
    val present = style.getLayer(CABINETS_LAYER_ID) != null
    if (show && !present) {
        style.addLayer(
            SymbolLayer(CABINETS_LAYER_ID, CABINETS_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(
                    Expression.switchCase(
                        Expression.get("has_power_off"),
                        Expression.literal(CABINET_FAULT_ICON_ID),
                        Expression.literal(CABINET_NORMAL_ICON_ID),
                    ),
                ),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    } else if (!show && present) {
        style.removeLayer(CABINETS_LAYER_ID)
    }
}

internal fun poleOpacityExpression(highlightedPoleIds: Set<String>): Expression =
    if (highlightedPoleIds.isEmpty()) {
        Expression.literal(1f)
    } else {
        Expression.match(
            Expression.get("pole_id"),
            Expression.literal(DIMMED_POLE_OPACITY),
            *highlightedPoleIds.map { Expression.stop(it, Expression.literal(1f)) }.toTypedArray(),
        )
    }
