package com.luxmap.feature.map.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.map.IOT_BADGE_ICON_ID
import com.luxmap.core.map.MAP_STYLE_URL
import com.luxmap.core.map.POI_BADGE_ICON_ID
import com.luxmap.core.map.VECTOR_STYLE_URL
import com.luxmap.core.map.markerColorArgb
import com.luxmap.core.map.registerMarkerBadgeIcons
import com.luxmap.core.map.routeColorArgb
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.map.data.PoleMarker
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
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

// Center coordinate and bbox of mock-poles.geojson (HCMC-area mock, 3 segments).
private val MOCK_AREA_CENTER = LatLng(10.971, 106.497)
private const val MOCK_AREA_ZOOM = 15.0

private const val POLES_SOURCE_ID = "poles-source"
private const val POLES_GLOW_CIRCLE_LAYER_ID = "poles-glow-circle-layer"
private const val POLES_CIRCLE_LAYER_ID = "poles-circle-layer"
private const val POLES_LABEL_LAYER_ID = "poles-label-layer"
private const val POLES_POI_BADGE_LAYER_ID = "poles-poi-badge-layer"
private const val POLES_IOT_BADGE_LAYER_ID = "poles-iot-badge-layer"
private const val CLUSTER_CIRCLE_LAYER_ID = "poles-cluster-circle-layer"
private const val CLUSTER_COUNT_LAYER_ID = "poles-cluster-count-layer"

// "Surveyed route" layer (F12) — drawn below the pole markers, so add this layer first in
// z-order.
private const val ROAD_SEGMENTS_SOURCE_ID = "road-segments-source"
private const val ROAD_SEGMENTS_LINE_LAYER_ID = "road-segments-line-layer"

private const val LOCATE_ME_ZOOM = 17.0
private const val LOCATE_CAMERA_TRANSITION_MS = 750L
private const val LOCATION_TIMEOUT_MS = 10_000L
private const val LOCATION_POLL_INTERVAL_MS = 500L

// Request both — coarse alone is still enough to show a location dot, just with a wider
// accuracy circle (see the FAB permission check below).
private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

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
    // Reference to the real map, set exactly once when ready — the LaunchedEffects below read
    // state (uiState/showFixtures/...) on every change and apply it directly to the map through
    // this reference. Do NOT rely on AndroidView calling `update` again on recompose: Compose
    // memoizes the `update` lambda because every variable it captures is stable State, so
    // `update` in practice only runs once — setting things directly in it won't react later.
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedPole by remember { mutableStateOf<PoleMarker?>(null) }
    var showFixtures by remember { mutableStateOf(true) }
    var showRoadSegments by remember { mutableStateOf(true) }
    var showPoleLabels by remember { mutableStateOf(false) }
    // Vector (OpenFreeMap) is the default for field operation — always sharp, no API key needed.
    // The basemap toggle lets the user switch to satellite (MapTiler Hybrid) when they need to
    // compare against real-world imagery (see MAP_STYLE_URL/VECTOR_STYLE_URL in MapLibreConfig.kt).
    var isSatelliteBasemap by remember { mutableStateOf(false) }
    var showLocationPermissionDenied by remember { mutableStateOf(false) }
    var showLocationPermissionSettingsHint by remember { mutableStateOf(false) }
    var showCoarseLocationNotice by remember { mutableStateOf(false) }
    var showLocationTimeout by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var isFollowingUser by remember { mutableStateOf(false) }
    var hasLocationPermission by
        remember {
            mutableStateOf(
                LOCATION_PERMISSIONS.any {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                },
            )
        }

    // Switches the camera into TRACKING mode — does not request permission itself, callers
    // (FAB click, permission-granted callback, timeout retry) must already know it's granted.
    val beginTracking: () -> Unit = {
        showLocationTimeout = false
        isLocating = true
        maplibreMap?.locationComponent?.setCameraMode(
            CameraMode.TRACKING,
            LOCATE_CAMERA_TRANSITION_MS,
            LOCATE_ME_ZOOM,
            null,
            null,
            null,
        )
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            when {
                fineGranted || coarseGranted -> {
                    hasLocationPermission = true
                    showCoarseLocationNotice = !fineGranted && coarseGranted
                    beginTracking()
                }
                // Once the OS stops offering a rationale for a denied permission, the user
                // picked "don't ask again" (or is on a second denial) — a 3rd system prompt
                // won't show, only Settings can grant it from here on.
                (context as? Activity)?.let { activity ->
                    LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
                } == true -> showLocationPermissionSettingsHint = true
                else -> showLocationPermissionDenied = true
            }
        }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.getMapAsync { map ->
                    if (map.style == null) {
                        map.setMaxZoomPreference(if (isSatelliteBasemap) SATELLITE_MAX_ZOOM else VECTOR_MAX_ZOOM)
                        map.cameraPosition =
                            CameraPosition.Builder()
                                .target(MOCK_AREA_CENTER)
                                .zoom(MOCK_AREA_ZOOM)
                                .build()
                        val initialStyleUrl = if (isSatelliteBasemap) MAP_STYLE_URL else VECTOR_STYLE_URL
                        map.setStyle(Style.Builder().fromUri(initialStyleUrl)) { style ->
                            setupMapLayers(context, style, uiState, showFixtures, showRoadSegments, showPoleLabels)
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

        // Turns on the blue dot once both the style and permission are ready — fires again
        // (harmlessly, enableLocationComponent no-ops if already activated) whenever either
        // becomes available later, e.g. permission granted after the style already loaded.
        LaunchedEffect(maplibreMap, hasLocationPermission) {
            val map = maplibreMap ?: return@LaunchedEffect
            val style = map.style ?: return@LaunchedEffect
            if (hasLocationPermission) {
                enableLocationComponent(context, map, style)
            }
        }

        // React to state — do not rely on AndroidView's `update` running again (see the comment
        // where maplibreMap is declared above).
        LaunchedEffect(maplibreMap, uiState) {
            val style = maplibreMap?.style ?: return@LaunchedEffect
            (style.getSource(POLES_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(uiState.polesOrEmpty().toGeoJson())
            (style.getSource(ROAD_SEGMENTS_SOURCE_ID) as? GeoJsonSource)
                ?.setGeoJson(uiState.roadSegmentsOrEmpty().toGeoJson())
        }

        LaunchedEffect(maplibreMap, showFixtures, showRoadSegments, showPoleLabels) {
            val style = maplibreMap?.style ?: return@LaunchedEffect
            applyLayerVisibility(style, showFixtures, showRoadSegments, showPoleLabels)
        }

        // Fires whenever camera mode changes — including automatically, when the user's own
        // drag/pinch gesture breaks tracking (built into LocationCameraController, no manual
        // gesture detection needed here).
        DisposableEffect(maplibreMap) {
            val locationComponent = maplibreMap?.locationComponent
            val listener =
                object : OnCameraTrackingChangedListener {
                    override fun onCameraTrackingDismissed() {
                        isFollowingUser = false
                    }

                    override fun onCameraTrackingChanged(currentMode: Int) {
                        isFollowingUser = currentMode == CameraMode.TRACKING
                    }
                }
            locationComponent?.addOnCameraTrackingChangedListener(listener)
            onDispose { locationComponent?.removeOnCameraTrackingChangedListener(listener) }
        }

        // No-fix timeout: poll instead of hooking into transition callbacks, since
        // lastKnownLocation reflects every engine update regardless of camera mode. Polling
        // stops as soon as a fix shows up or isLocating is cleared some other way.
        LaunchedEffect(isLocating) {
            if (!isLocating) return@LaunchedEffect
            val deadline = System.currentTimeMillis() + LOCATION_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (maplibreMap?.locationComponent?.lastKnownLocation != null) {
                    isLocating = false
                    return@LaunchedEffect
                }
                delay(LOCATION_POLL_INTERVAL_MS)
            }
            isLocating = false
            showLocationTimeout = true
        }

        // Map itself stays edge-to-edge (drawn under the status/navigation bars above), but the
        // overlay controls must not — wrap them in their own inset-aware Box so a FAB never ends
        // up under the status bar or the gesture navigation bar.
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
                        setupMapLayers(context, style, uiState, showFixtures, showRoadSegments, showPoleLabels)
                        // setStyle() drops the LocationComponent along with the rest of the old
                        // style — re-enable it here or the blue dot disappears after toggling
                        // basemap (see the NOTE on enableLocationComponent).
                        if (hasLocationPermission) {
                            enableLocationComponent(context, map, style)
                        }
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
                showPoleLabels = showPoleLabels,
                onShowPoleLabelsChange = { showPoleLabels = it },
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
                    showLocationPermissionSettingsHint = false
                    val granted =
                        LOCATION_PERMISSIONS.any {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                    if (granted) {
                        hasLocationPermission = true
                        beginTracking()
                    } else {
                        locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
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

            if (showCoarseLocationNotice) {
                MessageOverlay(
                    text = "Độ chính xác vị trí có thể thấp. Bật Vị trí chính xác trong Cài đặt để có kết quả tốt hơn.",
                    actionLabel = "Bỏ qua",
                    onAction = { showCoarseLocationNotice = false },
                )
            }

            if (showLocationPermissionSettingsHint) {
                MessageOverlay(
                    text = "Chưa cấp quyền vị trí. Mở Cài đặt ứng dụng để cấp quyền.",
                    actionLabel = "Mở cài đặt",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
            } else if (showLocationPermissionDenied) {
                MessageOverlay(text = "Chưa cấp quyền vị trí")
            }

            if (showLocationTimeout) {
                MessageOverlay(
                    text = "Không lấy được vị trí. Hãy bật GPS hoặc ra nơi thoáng, rồi thử lại.",
                    actionLabel = "Thử lại",
                    onAction = beginTracking,
                )
            }
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
    context: Context,
    style: Style,
    uiState: MapUiState,
    showFixtures: Boolean,
    showRoadSegments: Boolean,
    showPoleLabels: Boolean,
) {
    registerMarkerBadgeIcons(context, style)

    val segmentSource = GeoJsonSource(ROAD_SEGMENTS_SOURCE_ID, uiState.roadSegmentsOrEmpty().toGeoJson())
    style.addSource(segmentSource)
    // Add before the pole layer so the route draws below, markers on top.
    style.addLayer(buildRoadSegmentsLineLayer())

    val source = GeoJsonSource(POLES_SOURCE_ID, uiState.polesOrEmpty().toGeoJson(), buildClusterOptions())
    style.addSource(source)
    style.addLayer(buildClusterCircleLayer())
    style.addLayer(buildClusterCountLayer())
    // Add glow before the main dot so the dot sits on top of the glow.
    style.addLayer(buildPoleGlowCircleLayer())
    style.addLayer(buildPoleCircleLayer())
    style.addLayer(buildPoleLabelLayer())
    style.addLayer(buildPoiBadgeLayer())
    style.addLayer(buildIotBadgeLayer())

    applyLayerVisibility(style, showFixtures, showRoadSegments, showPoleLabels)
}

// Turns on MapLibre's own "blue dot" (LocationComponent) instead of a hand-built GeoJSON
// marker — draws the dot, accuracy ring and pulse itself, and keeps working across pan/zoom.
// NOTE: a Style is only valid until the next map.setStyle() call — whoever adds another
// setStyle() call later (e.g. a satellite/vector basemap toggle) must call this function
// again in that same style-loaded callback, or the dot silently disappears after the switch.
@SuppressLint("MissingPermission")
private fun enableLocationComponent(
    context: Context,
    map: MapLibreMap,
    style: Style,
) {
    if (map.locationComponent.isLocationComponentActivated) return
    val options = LocationComponentOptions.builder(context).pulseEnabled(true).build()
    val activationOptions =
        LocationComponentActivationOptions
            .builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .build()
    map.locationComponent.activateLocationComponent(activationOptions)
    map.locationComponent.isLocationComponentEnabled = true
    map.locationComponent.renderMode = RenderMode.NORMAL
}

// "Surveyed route" (F12) — colored by has_active_segment_fault to match how Web GIS shows grid
// faults (Rose600 when faulted, Blue500 when normal). Does NOT show a fault detail panel on
// tap — that is still Web GIS scope, mobile only needs the color as a visual warning for the
// field crew.
private fun buildRoadSegmentsLineLayer(): LineLayer =
    LineLayer(ROAD_SEGMENTS_LINE_LAYER_ID, ROAD_SEGMENTS_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor(
                Expression.switchCase(
                    Expression.get("has_active_segment_fault"),
                    Expression.color(routeColorArgb(hasActiveSegmentFault = true)),
                    Expression.color(routeColorArgb(hasActiveSegmentFault = false)),
                ),
            ),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineOpacity(0.7f),
        )

// Re-apply visibility every time a toggle changes or AndroidView's update runs again — much
// cheaper than add/remove layer, and no need to reset the GeoJSON.
private fun applyLayerVisibility(
    style: Style,
    showFixtures: Boolean,
    showRoadSegments: Boolean,
    showPoleLabels: Boolean,
) {
    val fixtureVisibility = if (showFixtures) Property.VISIBLE else Property.NONE
    val roadSegmentVisibility = if (showRoadSegments) Property.VISIBLE else Property.NONE
    // Labels only show when BOTH are true — no point showing labels for hidden markers.
    val labelVisibility = if (showFixtures && showPoleLabels) Property.VISIBLE else Property.NONE
    style.getLayer(POLES_GLOW_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(POLES_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(POLES_LABEL_LAYER_ID)?.setProperties(PropertyFactory.visibility(labelVisibility))
    style.getLayer(POLES_POI_BADGE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(POLES_IOT_BADGE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(ROAD_SEGMENTS_LINE_LAYER_ID)?.setProperties(PropertyFactory.visibility(roadSegmentVisibility))
}

// Soft glow around each pole marker (F12) — matches the glow effect on Web GIS. Static, no
// blinking: web only pulses the "out" state with a CSS animation, but adding a loop animation
// on native MapLibre just for looks is over-engineering for F12 scope.
private fun buildPoleGlowCircleLayer(): CircleLayer =
    CircleLayer(POLES_GLOW_CIRCLE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(14f),
            PropertyFactory.circleBlur(1f),
            PropertyFactory.circleOpacity(0.35f),
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

// Only draw a marker for features NOT grouped into a cluster (point_count only exists on
// clusters).
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

// pole_id label (F12, "Show pole labels" toggle) — same as the "Show labels" toggle on Web GIS,
// off by default because showing every label at once is cluttered when poles are close
// together. Do NOT turn on textAllowOverlap/textIgnorePlacement — poles along a road are often
// very close, forcing every label to show makes text overlap and unreadable (looks "blurry").
// Let MapLibre's default collision detection hide overlapping labels — that is what we want here.
private fun buildPoleLabelLayer(): SymbolLayer =
    SymbolLayer(POLES_LABEL_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.textField(Expression.get("pole_id")),
            PropertyFactory.textSize(10f),
            PropertyFactory.textColor("#0F172A"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.2f),
            PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
        ).apply { setFilter(Expression.not(Expression.has("point_count"))) }

// "Near sensitive area" badge (F12, near_sensitive_poi) — top-right corner of the dot. IoT
// badge goes bottom-right so a pole that is both near a POI and has an IoT node doesn't overlap
// badges.
private fun buildPoiBadgeLayer(): SymbolLayer =
    SymbolLayer(POLES_POI_BADGE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(POI_BADGE_ICON_ID),
            PropertyFactory.iconSize(0.5f),
            PropertyFactory.iconOffset(arrayOf(6f, -6f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).apply {
            setFilter(
                Expression.all(
                    Expression.not(Expression.has("point_count")),
                    Expression.eq(Expression.get("near_sensitive_poi"), Expression.literal(true)),
                ),
            )
        }

private fun buildIotBadgeLayer(): SymbolLayer =
    SymbolLayer(POLES_IOT_BADGE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(IOT_BADGE_ICON_ID),
            PropertyFactory.iconSize(0.5f),
            PropertyFactory.iconOffset(arrayOf(6f, 6f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).apply {
            setFilter(
                Expression.all(
                    Expression.not(Expression.has("point_count")),
                    Expression.eq(Expression.get("has_iot_node"), Expression.literal(true)),
                ),
            )
        }

// clusterProperties: mapExpression computes a numeric severity (0..3) from each point's
// fixture_status before clustering; reduceExpression takes the running max — matches the
// requirement "cluster shows count and highest severity" (Design System section 6.10).
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
private fun BoxScope.MessageOverlay(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(Spacing.lg)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Dimens.radiusMedium),
                ).padding(Spacing.md),
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurface)
        if (actionLabel != null && onAction != null) {
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

// MapView is a plain Android View, its lifecycle (onStart/onResume/...) must be forwarded
// manually from Compose to MapView — MapLibre has no official Compose interop yet.
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
