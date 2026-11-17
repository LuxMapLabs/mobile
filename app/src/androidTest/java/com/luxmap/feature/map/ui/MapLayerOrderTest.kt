package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.luxmap.core.map.initMapLibre
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.Style
import java.util.concurrent.atomic.AtomicReference

class MapLayerOrderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun reenabled_layers_keep_their_order_above_the_basemap() {
        val loadedStyle = AtomicReference<Style>()
        composeRule.setContent {
            initMapLibre(LocalContext.current)
            val mapView = rememberMapViewWithLifecycle()
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) { view ->
                view.getMapAsync { map ->
                    val basemap =
                        """{"version":8,"sources":{},"layers":[{"id":"basemap","type":"background"}]}"""
                    map.setStyle(Style.Builder().fromJson(basemap)) { style ->
                        setupMapLayers(view.context, style, MapUiState.Empty, true, true, true, true)
                        loadedStyle.set(style)
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { loadedStyle.get() != null }

        composeRule.runOnIdle {
            val style = checkNotNull(loadedStyle.get())

            fun layerIds() = style.layers.map { it.id }.filterNot { it == "org.maplibre.annotations.points" }

            val expectedOrder =
                listOf(
                    "basemap",
                    "communes-fill-layer",
                    "communes-line-layer",
                    "road-segments-glow-line-layer",
                    "road-segments-line-layer",
                    "topology-line-layer",
                    "topology-line-layer-off",
                    "poles-cluster-circle-layer",
                    "poles-cluster-count-layer",
                    "poles-selected-halo-layer",
                    "poles-circle-layer",
                    "poles-poi-badge-layer",
                    "cabinets-layer",
                )
            assertEquals(expectedOrder, layerIds())

            applyLayerVisibility(style, false, false, true, false)
            applyLayerVisibility(style, false, false, true, true)
            assertEquals(
                expectedOrder.filterNot { it.startsWith("road-") || it.startsWith("poles-") },
                layerIds(),
            )

            applyLayerVisibility(style, true, true, true, true)
            assertEquals(expectedOrder, layerIds())
        }
    }
}
