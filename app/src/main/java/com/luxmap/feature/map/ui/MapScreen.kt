package com.luxmap.feature.map.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.map.MAP_STYLE_URL
import com.luxmap.core.map.VECTOR_STYLE_URL
import com.luxmap.core.map.markerColorArgb
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.feature.map.data.PoleMarker
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

// Toạ độ trung tâm và bbox của mock-poles.geojson (khu vực HCMC-lân cận, 3 segment).
private val MOCK_AREA_CENTER = LatLng(10.971, 106.497)
private const val MOCK_AREA_ZOOM = 15.0

private const val POLES_SOURCE_ID = "poles-source"
private const val POLES_CIRCLE_LAYER_ID = "poles-circle-layer"
private const val CLUSTER_CIRCLE_LAYER_ID = "poles-cluster-circle-layer"
private const val CLUSTER_COUNT_LAYER_ID = "poles-cluster-count-layer"

// Lớp "tuyến đã khảo sát" (F12) — vẽ dưới marker cột đèn nên add layer này trước trong z-order.
private const val ROAD_SEGMENTS_SOURCE_ID = "road-segments-source"
private const val ROAD_SEGMENTS_LINE_LAYER_ID = "road-segments-line-layer"

private const val LOCATE_ME_ZOOM = 17.0

// Camera zoom cap while on the satellite basemap — see the comment where
// map.setMaxZoomPreference() is called for why (rural satellite imagery gets blurry past its
// source resolution when over-zoomed). The vector basemap is drawn with lines so it doesn't
// blur, and allows zooming in much further.
private const val SATELLITE_MAX_ZOOM = 18.5
private const val VECTOR_MAX_ZOOM = 20.0

// Standard Material3 FloatingActionButton size — used to space the zoom +/- button cluster
// right above the locate-me button, so they don't overlap.
private val STANDARD_FAB_SIZE = 56.dp

// clusterProperties computes the "highest severity in the cluster" (Design System section
// 6.10): out=3, dim=2, normal=1, unknown=0 — this property only exists on cluster features.
private const val CLUSTER_MAX_SEVERITY_PROPERTY = "max_severity"
private const val CLUSTER_MAX_ZOOM = 14
private const val CLUSTER_RADIUS = 50

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val mapView = rememberMapViewWithLifecycle()
    // Tham chiếu map thật, cập nhật đúng 1 lần khi sẵn sàng — các LaunchedEffect bên dưới đọc
    // lại state (uiState/showFixtures/...) mỗi lần đổi và tự áp trực tiếp lên map qua tham
    // chiếu này. KHÔNG dựa vào việc AndroidView gọi lại `update` mỗi lần recompose: Compose
    // nhớ lại (memoize) lambda `update` vì mọi biến nó bắt đều là State ổn định, nên `update`
    // trong thực tế chỉ chạy đúng 1 lần — set trực tiếp trong đó sẽ không phản ứng lại sau này.
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedPole by remember { mutableStateOf<PoleMarker?>(null) }
    var showFixtures by remember { mutableStateOf(true) }
    var showRoadSegments by remember { mutableStateOf(true) }
    // Satellite is the initial default (matches "look like the web app"); the basemap toggle
    // lets the user switch to vector when a sharper view is needed (see VECTOR_STYLE_URL in
    // MapLibreConfig.kt).
    var isSatelliteBasemap by remember { mutableStateOf(true) }
    var locateTarget by remember { mutableStateOf<LatLng?>(null) }
    var showLocationPermissionDenied by remember { mutableStateOf(false) }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.onLocateMeClicked()
            } else {
                showLocationPermissionDenied = true
            }
        }

    // One-shot: mỗi lần bấm nút định vị chỉ bay camera đúng 1 lần, không phát lại khi recompose.
    LaunchedEffect(Unit) {
        viewModel.locateMeEvent.collect { latLng -> locateTarget = latLng }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.getMapAsync { map ->
                    if (map.style == null) {
                        map.setMaxZoomPreference(SATELLITE_MAX_ZOOM)
                        map.cameraPosition =
                            CameraPosition.Builder()
                                .target(MOCK_AREA_CENTER)
                                .zoom(MOCK_AREA_ZOOM)
                                .build()
                        map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                            setupMapLayers(style, uiState, showFixtures, showRoadSegments)
                            // Style/source/layer are only guaranteed ready here (inside the
                            // onStyleLoaded callback) — assign maplibreMap at this point so the
                            // LaunchedEffects reacting to state don't run before the layers exist.
                            maplibreMap = map
                        }
                        map.addOnMapClickListener { latLng ->
                            val screenPoint = map.projection.toScreenLocation(latLng)
                            val tappedPoleId =
                                map
                                    .queryRenderedFeatures(screenPoint, POLES_CIRCLE_LAYER_ID)
                                    .firstOrNull()
                                    ?.getStringProperty("pole_id")
                            val tappedPole =
                                tappedPoleId?.let { id ->
                                    uiState.polesOrEmpty().firstOrNull { it.poleId == id }
                                }
                            if (tappedPole != null) {
                                selectedPole = tappedPole
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        maplibreMap = map
                    }
                }
            },
        )

        // Phản ứng state — không dựa vào `update` của AndroidView chạy lại (xem comment ở
        // khai báo maplibreMap phía trên).
        LaunchedEffect(maplibreMap, uiState) {
            val style = maplibreMap?.style ?: return@LaunchedEffect
            (style.getSource(POLES_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(uiState.polesOrEmpty().toGeoJson())
            (style.getSource(ROAD_SEGMENTS_SOURCE_ID) as? GeoJsonSource)
                ?.setGeoJson(uiState.roadSegmentsOrEmpty().toGeoJson())
        }

        LaunchedEffect(maplibreMap, showFixtures, showRoadSegments) {
            val style = maplibreMap?.style ?: return@LaunchedEffect
            applyLayerVisibility(style, showFixtures, showRoadSegments)
        }

        LaunchedEffect(locateTarget) {
            val target = locateTarget ?: return@LaunchedEffect
            maplibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, LOCATE_ME_ZOOM))
            locateTarget = null
        }

        // Satellite <-> vector basemap toggle (F12) — the free satellite imagery is only sharp
        // in urban areas, rural areas (LuxMap's actual scope) have lower-resolution source
        // imagery so it looks noticeably softer/blurrier. There is no way to fix this in code
        // because the source imagery just doesn't have that resolution — this button lets the
        // field crew switch to the vector basemap (drawn with lines, always sharp) when needed.
        FloatingActionButton(
            onClick = {
                isSatelliteBasemap = !isSatelliteBasemap
                val map = maplibreMap ?: return@FloatingActionButton
                map.setMaxZoomPreference(if (isSatelliteBasemap) SATELLITE_MAX_ZOOM else VECTOR_MAX_ZOOM)
                val newStyleUrl = if (isSatelliteBasemap) MAP_STYLE_URL else VECTOR_STYLE_URL
                map.setStyle(Style.Builder().fromUri(newStyleUrl)) { style ->
                    setupMapLayers(style, uiState, showFixtures, showRoadSegments)
                }
            },
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.lg),
        ) {
            Text(
                text = if (isSatelliteBasemap) "Vector" else "Vệ tinh",
                style = MaterialTheme.typography.labelMedium,
            )
        }

        MapLegend(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.lg),
        )

        MapLayerToggle(
            showFixtures = showFixtures,
            onShowFixturesChange = { showFixtures = it },
            showRoadSegments = showRoadSegments,
            onShowRoadSegmentsChange = { showRoadSegments = it },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.lg),
        )

        // Zoom +/- buttons (F12) — MapLibre Native has no built-in widget like maplibre-gl JS's
        // NavigationControl on the web, so add 2 plain FABs (56dp, meets the 48dp minimum touch
        // target from the Design System) that call CameraUpdateFactory.zoomIn()/zoomOut()
        // directly. Pinch-to-zoom still works as usual, this is just an extra option.
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = Spacing.lg + STANDARD_FAB_SIZE + Spacing.sm, end = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FloatingActionButton(onClick = { maplibreMap?.animateCamera(CameraUpdateFactory.zoomIn()) }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Phóng to")
            }
            FloatingActionButton(onClick = { maplibreMap?.animateCamera(CameraUpdateFactory.zoomOut()) }) {
                // Icons.Filled.Remove is not in material-icons-core (only in the extended
                // package, not in our dependencies) — use the "−" character instead of adding
                // a new library.
                Text(text = "−", style = MaterialTheme.typography.headlineSmall)
            }
        }

        FloatingActionButton(
            onClick = {
                showLocationPermissionDenied = false
                val granted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                if (granted) {
                    viewModel.onLocateMeClicked()
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.lg),
        ) {
            Icon(imageVector = Icons.Filled.LocationOn, contentDescription = "Định vị vị trí hiện tại")
        }

        when (uiState) {
            is MapUiState.Loading -> LoadingOverlay()
            is MapUiState.Empty -> MessageOverlay(text = "Không có cột đèn trong khu vực này")
            is MapUiState.Error -> MessageOverlay(text = (uiState as MapUiState.Error).message)
            is MapUiState.Success -> Unit
        }

        if (showLocationPermissionDenied) {
            MessageOverlay(text = "Chưa cấp quyền vị trí")
        }
    }

    selectedPole?.let { pole ->
        PoleQuickViewBottomSheet(pole = pole, onDismiss = { selectedPole = null })
    }
}

private fun MapUiState.polesOrEmpty() = (this as? MapUiState.Success)?.poles.orEmpty()

private fun MapUiState.roadSegmentsOrEmpty() = (this as? MapUiState.Success)?.roadSegments.orEmpty()

// Add every F12 source/layer onto a freshly loaded Style — used both for the first load AND
// every time the user taps the satellite/vector basemap toggle, because map.setStyle() replaces
// the whole style so all old sources/layers are gone and must be added again from scratch.
private fun setupMapLayers(
    style: Style,
    uiState: MapUiState,
    showFixtures: Boolean,
    showRoadSegments: Boolean,
) {
    val segmentSource = GeoJsonSource(ROAD_SEGMENTS_SOURCE_ID, uiState.roadSegmentsOrEmpty().toGeoJson())
    style.addSource(segmentSource)
    // Add before the pole layer so the route draws below, markers on top.
    style.addLayer(buildRoadSegmentsLineLayer())

    val source = GeoJsonSource(POLES_SOURCE_ID, uiState.polesOrEmpty().toGeoJson(), buildClusterOptions())
    style.addSource(source)
    style.addLayer(buildClusterCircleLayer())
    style.addLayer(buildClusterCountLayer())
    style.addLayer(buildPoleCircleLayer())

    applyLayerVisibility(style, showFixtures, showRoadSegments)
}

// Surveyed route (F12) — one neutral color, not colored by fault status (that is Web GIS
// analysis, out of scope for mobile, see RoadSegmentLine.kt).
private fun buildRoadSegmentsLineLayer(): LineLayer =
    LineLayer(ROAD_SEGMENTS_LINE_LAYER_ID, ROAD_SEGMENTS_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor("#3E86C9"),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineOpacity(0.7f),
        )

// Áp lại visibility mỗi lần toggle đổi hoặc mỗi lần AndroidView update chạy lại — rẻ hơn nhiều
// so với add/remove layer, và không cần set lại GeoJSON.
private fun applyLayerVisibility(
    style: Style,
    showFixtures: Boolean,
    showRoadSegments: Boolean,
) {
    val fixtureVisibility = if (showFixtures) Property.VISIBLE else Property.NONE
    val roadSegmentVisibility = if (showRoadSegments) Property.VISIBLE else Property.NONE
    style.getLayer(POLES_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(ROAD_SEGMENTS_LINE_LAYER_ID)?.setProperties(PropertyFactory.visibility(roadSegmentVisibility))
}

// Chỉ vẽ marker từng điểm cho feature KHÔNG bị gộp cụm (point_count chỉ tồn tại trên cluster).
private fun buildPoleCircleLayer(): CircleLayer =
    CircleLayer(POLES_CIRCLE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("fixture_status"),
                    Expression.color(AssetCondition.UNKNOWN.markerColorArgb()),
                    Expression.stop("normal", Expression.color(AssetCondition.NORMAL.markerColorArgb())),
                    Expression.stop("dim", Expression.color(AssetCondition.DIM.markerColorArgb())),
                    Expression.stop("out", Expression.color(AssetCondition.OUT.markerColorArgb())),
                ),
            ),
        ).apply { setFilter(Expression.not(Expression.has("point_count"))) }

// clusterProperties: mapExpression tính severity số (0..3) từ fixture_status của MỖI điểm
// trước khi gộp cụm; reduceExpression lấy max tích luỹ — đúng yêu cầu "Cluster hiển thị số
// lượng và mức nghiêm trọng cao nhất" (Design System §6.10).
private fun buildClusterOptions(): GeoJsonOptions =
    GeoJsonOptions()
        .withCluster(true)
        .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
        .withClusterRadius(CLUSTER_RADIUS)
        .withClusterProperty(
            CLUSTER_MAX_SEVERITY_PROPERTY,
            Expression.max(Expression.accumulated(), Expression.get(CLUSTER_MAX_SEVERITY_PROPERTY)),
            Expression.toNumber(
                Expression.match(
                    Expression.get("fixture_status"),
                    Expression.literal(0),
                    Expression.stop("out", Expression.literal(3)),
                    Expression.stop("dim", Expression.literal(2)),
                    Expression.stop("normal", Expression.literal(1)),
                ),
            ),
        )

private fun buildClusterCircleLayer(): CircleLayer =
    CircleLayer(CLUSTER_CIRCLE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(18f),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleColor(
                Expression.match(
                    Expression.toNumber(Expression.get(CLUSTER_MAX_SEVERITY_PROPERTY)),
                    Expression.color(AssetCondition.UNKNOWN.markerColorArgb()),
                    Expression.stop(3, Expression.color(AssetCondition.OUT.markerColorArgb())),
                    Expression.stop(2, Expression.color(AssetCondition.DIM.markerColorArgb())),
                    Expression.stop(1, Expression.color(AssetCondition.NORMAL.markerColorArgb())),
                ),
            ),
        ).apply { setFilter(Expression.has("point_count")) }

private fun buildClusterCountLayer(): SymbolLayer =
    SymbolLayer(CLUSTER_COUNT_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textAllowOverlap(true),
        ).apply { setFilter(Expression.has("point_count")) }

@Composable
private fun BoxScope.LoadingOverlay() {
    CircularProgressIndicator(
        modifier = Modifier.align(Alignment.Center),
    )
}

@Composable
private fun BoxScope.MessageOverlay(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(Spacing.lg)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Dimens.radiusMedium),
                ).padding(Spacing.md),
    )
}

// MapView là Android View thuần, cần tự chuyển tiếp lifecycle (onStart/onResume/...) từ
// Compose sang MapView — MapLibre chưa có Compose interop chính thức.
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, mapView) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = mapView.lifecycleObserver()
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return mapView
}

private fun MapView.lifecycleObserver(): LifecycleEventObserver =
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> onCreate(null)
            Lifecycle.Event.ON_START -> onStart()
            Lifecycle.Event.ON_RESUME -> onResume()
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_STOP -> onStop()
            Lifecycle.Event.ON_DESTROY -> onDestroy()
            else -> Unit
        }
    }
