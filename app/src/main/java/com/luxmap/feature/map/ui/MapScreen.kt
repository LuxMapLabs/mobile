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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.luxmap.core.map.IOT_BADGE_ICON_ID
import com.luxmap.core.map.MapMarkerColors
import com.luxmap.core.map.POI_BADGE_ICON_ID
import com.luxmap.core.map.VECTOR_STYLE_URL
import com.luxmap.core.map.markerColorArgb
import com.luxmap.core.map.markerIconId
import com.luxmap.core.map.registerMarkerBadgeIcons
import com.luxmap.core.map.routeColorArgb
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.feature.map.data.GisMapBbox
import com.luxmap.feature.map.data.PoleMarker
import com.luxmap.feature.map.data.RoadSegmentLine
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
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
import org.maplibre.geojson.Point

// Center coordinate and bbox of mock-poles.geojson (HCMC-area mock, 3 segments).
private val MOCK_AREA_CENTER = LatLng(10.971, 106.497)
private const val MOCK_AREA_ZOOM = 15.0

private const val POLES_SOURCE_ID = "poles-source"

// Kept as "circle" in the id string even though FM-37 turned this into a SymbolLayer — renaming
// the id would be a pointless churn, it is just a style source/layer identifier string, not a
// type name.
private const val POLES_CIRCLE_LAYER_ID = "poles-circle-layer"
private const val POLES_SELECTED_HALO_LAYER_ID = "poles-selected-halo-layer"
private const val POLES_POI_BADGE_LAYER_ID = "poles-poi-badge-layer"
private const val POLES_IOT_BADGE_LAYER_ID = "poles-iot-badge-layer"
private const val CLUSTER_CIRCLE_LAYER_ID = "poles-cluster-circle-layer"
private const val CLUSTER_COUNT_LAYER_ID = "poles-cluster-count-layer"

// No real pole_id is ever empty (see mock-poles.geojson / Contract v1.1) — used as the selected
// halo layer's filter value when nothing is selected, so it matches zero features.
private const val NO_SELECTION_SENTINEL = ""

private const val MARKER_ICON_SIZE = 1.0f
private const val MARKER_ICON_SIZE_SELECTED = 24f / 18f
private const val SELECTED_HALO_RADIUS = 15f

// Badges/labels only worth showing once zoomed in enough to read them (F12/FM-37 LOD rule) —
// below this the map is zoomed out far enough that individual badges would just be clutter.
private const val BADGE_MIN_ZOOM = 13f

// Same gap from the bottom edge for the legend and the right-side buttons, so they line up.
// The MapLibre logo and attribution are hidden, so no extra space is needed for them.
private val MAP_CONTROLS_BOTTOM_PADDING = Spacing.md

// "Surveyed route" layer (F12) — drawn below the pole markers, so add this layer first in
// z-order.
private const val ROAD_SEGMENTS_SOURCE_ID = "road-segments-source"
private const val ROAD_SEGMENTS_GLOW_LINE_LAYER_ID = "road-segments-glow-line-layer"
private const val ROAD_SEGMENTS_LINE_LAYER_ID = "road-segments-line-layer"

private const val LOCATE_ME_ZOOM = 17.0
private const val LOCATE_CAMERA_TRANSITION_MS = 750L
private const val LOCATION_TIMEOUT_MS = 10_000L
private const val LOCATION_POLL_INTERVAL_MS = 500L

// Quick location for the blue dot: accept a cached location up to 60s old, wait at most 8s.
private const val QUICK_FIX_MAX_AGE_MS = 60_000L
private const val QUICK_FIX_TIMEOUT_MS = 8_000L

// Padding (px) around a tapped/searched route's bounds so it doesn't end up flush against the
// screen edge or hidden under the search bar / control stack (F12/FM-36).
private const val SEGMENT_FIT_CAMERA_PADDING = 120

// Zoom level when jumping to a pole picked from search results (F12/FM-36) — close enough to
// clearly single it out, same idea as LOCATE_ME_ZOOM above.
private const val SEARCH_RESULT_POLE_ZOOM = 18.0

// Request both — coarse alone is still enough to show a location dot, just with a wider
// accuracy circle (see the FAB permission check below).
private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private const val VECTOR_MAX_ZOOM = 20.0

// What the KPI "cần xử lý" chip filters to (FM-36) — poles that need a field visit, tapping the
// chip again restores the empty set (no filter).
private val NEEDS_ATTENTION_STATUSES = setOf(AssetCondition.DIM, AssetCondition.OUT)

// clusterProperties computes the "highest severity in the cluster" (Design System section
// 6.10): out=3, dim=2, normal=1, unknown=0 — this property only exists on cluster features.
private const val CLUSTER_MAX_SEVERITY_PROPERTY = "max_severity"
private const val CLUSTER_MAX_ZOOM = 14
private const val CLUSTER_RADIUS = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenPoleDetail: (poleId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val uiState by viewModel.uiState.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val mapView = rememberMapViewWithLifecycle()
    // Reference to the real map, set exactly once when ready — the LaunchedEffects below read
    // state (uiState/showFixtures/...) on every change and apply it directly to the map through
    // this reference. Do NOT rely on AndroidView calling `update` again on recompose: Compose
    // memoizes the `update` lambda because every variable it captures is stable State, so
    // `update` in practice only runs once — setting things directly in it won't react later.
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedPole by remember { mutableStateOf<PoleMarker?>(null) }
    var selectedSegment by remember { mutableStateOf<RoadSegmentLine?>(null) }
    // These four use rememberSaveable so they survive a tab switch: the MapView is created again
    // each time the user comes back to the map tab, and without this the view would reset to the
    // default camera and layer settings.
    var showFixtures by rememberSaveable { mutableStateOf(true) }
    var showRoadSegments by rememberSaveable { mutableStateOf(true) }
    // Last camera as [latitude, longitude, zoom, bearing]; null until the map has been moved once.
    var savedCamera by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    // True once the camera has been auto-moved to the real map_bounds.center — guards against
    // re-centering every time uiState re-emits (e.g. after a search/filter change).
    var hasCenteredOnRealData by rememberSaveable { mutableStateOf(false) }
    // True only once the user has actually dragged/pinched the map — distinct from savedCamera,
    // which also gets set by the idle event the very first programmatic camera placement below
    // triggers, so savedCamera alone can't tell "the user moved it" from "we moved it".
    var userMovedCamera by rememberSaveable { mutableStateOf(false) }
    var showLocationPermissionDenied by remember { mutableStateOf(false) }
    var showLocationPermissionSettingsHint by remember { mutableStateOf(false) }
    var showCoarseLocationNotice by remember { mutableStateOf(false) }
    var showLocationTimeout by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var isFollowingUser by remember { mutableStateOf(false) }
    var showLayerFilterSheet by remember { mutableStateOf(false) }
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
        maplibreMap?.let { showQuickLocationFix(context, it) }
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
            modifier =
                Modifier
                    .fillMaxSize()
                    // The map is a plain Android View, so Compose does not know about taps on
                    // it. Watch the touch (Initial pass, without consuming it) and drop the
                    // search bar focus, so its highlight and the keyboard go away.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    },
            factory = { mapView },
            update = { view ->
                view.getMapAsync { map ->
                    if (map.style == null) {
                        // Hide the MapLibre logo and the attribution button. The tile providers
                        // (OpenStreetMap) ask for credit in their terms, so show it
                        // somewhere else in the app (for example the Profile screen).
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        // The map only shows poles and routes, so keep it facing north. With
                        // rotation off the compass is not needed.
                        map.uiSettings.isRotateGesturesEnabled = false
                        map.uiSettings.isCompassEnabled = false
                        map.setMaxZoomPreference(VECTOR_MAX_ZOOM)
                        val camera = savedCamera
                        map.cameraPosition =
                            if (camera != null) {
                                CameraPosition.Builder()
                                    .target(LatLng(camera[0], camera[1]))
                                    .zoom(camera[2])
                                    .bearing(camera[3])
                                    .build()
                            } else {
                                CameraPosition.Builder()
                                    .target(MOCK_AREA_CENTER)
                                    .zoom(MOCK_AREA_ZOOM)
                                    .build()
                            }
                        map.addOnCameraIdleListener {
                            val position = map.cameraPosition
                            position.target?.let { target ->
                                savedCamera =
                                    doubleArrayOf(target.latitude, target.longitude, position.zoom, position.bearing)
                            }
                        }
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                userMovedCamera = true
                            }
                        }
                        map.setStyle(Style.Builder().fromUri(VECTOR_STYLE_URL)) { style ->
                            setupMapLayers(context, style, uiState, showFixtures, showRoadSegments)
                            // Style/source/layer are only guaranteed ready here (inside the
                            // onStyleLoaded callback) — assign maplibreMap at this point so the
                            // LaunchedEffects reacting to state don't run before the layers exist.
                            maplibreMap = map
                        }
                        map.addOnMapClickListener addOnMapClickListener@{ latLng ->
                            val screenPoint = map.projection.toScreenLocation(latLng)
                            val tappedPoleId =
                                pickNearestFeature(
                                    screenPoint.x,
                                    screenPoint.y,
                                    map
                                        .queryRenderedFeatures(
                                            tapSquare(
                                                screenPoint.x,
                                                screenPoint.y,
                                                context.resources.displayMetrics.density,
                                            ),
                                            POLES_CIRCLE_LAYER_ID,
                                        ).mapNotNull { feature ->
                                            val id = feature.getStringProperty("pole_id") ?: return@mapNotNull null
                                            val point = feature.geometry() as? Point ?: return@mapNotNull null
                                            val screen =
                                                map.projection.toScreenLocation(
                                                    LatLng(point.latitude(), point.longitude()),
                                                )
                                            TapCandidate(id, screen.x, screen.y)
                                        },
                                )
                            val tappedPole =
                                tappedPoleId?.let { id ->
                                    uiState.polesOrEmpty().firstOrNull { it.poleId == id }
                                }
                            if (tappedPole != null) {
                                selectedPole = tappedPole
                                return@addOnMapClickListener true
                            }

                            val tappedCluster =
                                map.queryRenderedFeatures(screenPoint, CLUSTER_CIRCLE_LAYER_ID).firstOrNull()
                            if (tappedCluster != null) {
                                val clusterSource = map.style?.getSource(POLES_SOURCE_ID) as? GeoJsonSource
                                val expansionZoom = clusterSource?.getClusterExpansionZoom(tappedCluster)
                                val clusterCenter =
                                    (tappedCluster.geometry() as? Point)?.let {
                                        LatLng(
                                            it.latitude(),
                                            it.longitude(),
                                        )
                                    }
                                if (expansionZoom != null && clusterCenter != null) {
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(clusterCenter, expansionZoom.toDouble()),
                                    )
                                }
                                return@addOnMapClickListener true
                            }

                            val tappedSegmentId =
                                map
                                    .queryRenderedFeatures(screenPoint, ROAD_SEGMENTS_LINE_LAYER_ID)
                                    .firstOrNull()
                                    ?.getStringProperty("segment_id")
                            val tappedSegment =
                                tappedSegmentId?.let { id ->
                                    uiState.roadSegmentsOrEmpty().firstOrNull { it.segmentId == id }
                                }
                            if (tappedSegment != null) {
                                selectedSegment = tappedSegment
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngBounds(
                                        tappedSegment.toLatLngBounds(),
                                        SEGMENT_FIT_CAMERA_PADDING,
                                    ),
                                )
                                return@addOnMapClickListener true
                            }

                            false
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

        // Moves the camera to the real area the first time it's known (FM-15 "Hướng B") — only
        // when the user has not already moved the map (savedCamera stays null until the first
        // camera idle event) and only once, so this never fights a camera position the user chose.
        // Fits to the commune's real bbox instead of a fixed zoom — a commune's actual extent
        // varies far more than one hardcoded zoom level can cover, and zooming tighter than the
        // real data forces poles that are genuinely only 10-20m apart (a normal rural spacing) to
        // render a few screen pixels apart, well past where they'd otherwise cluster.
        LaunchedEffect(maplibreMap, uiState) {
            val map = maplibreMap ?: return@LaunchedEffect
            if (hasCenteredOnRealData || userMovedCamera) return@LaunchedEffect
            val bbox = uiState.bboxOrNull()
            if (bbox != null) {
                hasCenteredOnRealData = true
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bbox.toLatLngBounds(), SEGMENT_FIT_CAMERA_PADDING))
                return@LaunchedEffect
            }
            val center = uiState.centerOrNull() ?: return@LaunchedEffect
            hasCenteredOnRealData = true
            map.cameraPosition =
                CameraPosition.Builder()
                    .target(LatLng(center.first, center.second))
                    .zoom(MOCK_AREA_ZOOM)
                    .build()
        }

        LaunchedEffect(maplibreMap, showFixtures, showRoadSegments) {
            val style = maplibreMap?.style ?: return@LaunchedEffect
            applyLayerVisibility(style, showFixtures, showRoadSegments)
        }

        // Selected marker gets a bigger icon + halo ring (F12/FM-37) — re-styles the existing
        // layers in place, does not touch the GeoJSON or rebuild the style.
        LaunchedEffect(maplibreMap, selectedPole) {
            val style = maplibreMap?.style ?: return@LaunchedEffect
            updateSelectedPoleStyle(style, selectedPole?.poleId)
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
        // up under the status bar. The bottom inset is skipped because the map already sits above
        // the app's bottom navigation bar (Scaffold adds it), so adding it again would push the
        // controls too high. The keyboard inset is skipped too, so the bottom buttons stay in
        // place when the search keyboard opens (the search bar is at the top, so it is not hidden).
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
        ) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                MapSearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onFilterClick = { showLayerFilterSheet = true },
                    hiddenStatusCount = AssetCondition.entries.size - statusFilter.size,
                )
                if (searchQuery.isNotBlank()) {
                    MapSearchResultsList(
                        results = searchResults,
                        onPoleClick = { pole ->
                            selectedPole = pole
                            maplibreMap?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(pole.lat, pole.lng), SEARCH_RESULT_POLE_ZOOM),
                            )
                            viewModel.setSearchQuery("")
                        },
                        onSegmentClick = { segment ->
                            selectedSegment = segment
                            maplibreMap?.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(
                                    segment.toLatLngBounds(),
                                    SEGMENT_FIT_CAMERA_PADDING,
                                ),
                            )
                            viewModel.setSearchQuery("")
                        },
                    )
                }
                MapKpiChipRow(
                    poleCount = uiState.allPolesOrEmpty().size,
                    needsAttentionCount =
                        uiState.allPolesOrEmpty().count {
                            it.fixtureStatus == AssetCondition.DIM || it.fixtureStatus == AssetCondition.OUT
                        },
                    routeCount = uiState.allRoadSegmentsOrEmpty().size,
                    needsAttentionActive = statusFilter == NEEDS_ATTENTION_STATUSES,
                    onNeedsAttentionClick = {
                        viewModel.setStatusFilter(
                            if (statusFilter == NEEDS_ATTENTION_STATUSES) {
                                AssetCondition.entries.toSet()
                            } else {
                                NEEDS_ATTENTION_STATUSES
                            },
                        )
                    },
                )
            }

            MapLegend(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Spacing.lg, end = Spacing.lg, bottom = MAP_CONTROLS_BOTTOM_PADDING),
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            start = Spacing.lg,
                            top = Spacing.lg,
                            end = Spacing.lg,
                            bottom = MAP_CONTROLS_BOTTOM_PADDING,
                        ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.End,
            ) {
                LocateMeButton(
                    isLocating = isLocating,
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
                )
            }

            if (showLayerFilterSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showLayerFilterSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                ) {
                    // Layer/display management, not a search filter (F12) — wording and layout
                    // reflect that: a title, then two labeled groups separated by a light divider.
                    // Plain Column (no LazyColumn/fillMaxHeight) so the sheet stays as short as its
                    // content, it never forces itself to nearly full screen height.
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                        Text(text = "Hiển thị trên bản đồ", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(Spacing.md))
                        LuxSectionHeader(title = "Lớp bản đồ")
                        MapLayerToggle(
                            showFixtures = showFixtures,
                            onShowFixturesChange = { showFixtures = it },
                            showRoadSegments = showRoadSegments,
                            onShowRoadSegmentsChange = { showRoadSegments = it },
                            modifier = Modifier.padding(vertical = Spacing.xs),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Spacing.sm),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        LuxSectionHeader(title = "Trạng thái cột")
                        MapStatusFilterRow(
                            selectedStatuses = statusFilter,
                            onToggle = { condition -> viewModel.setStatusFilter(statusFilter.toggled(condition)) },
                            enabled = showFixtures,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                    }
                }
            }

            // F12/FM-38 — device has no network, only the basemap tiles can't load. Pole/route
            // data keeps rendering as usual (uiState below is independent of this), so this is
            // just a heads-up, not an error state.
            if (!isOnline) {
                MessageOverlay(text = "Không tải được nền bản đồ · Dữ liệu cột vẫn khả dụng")
            }

            when (uiState) {
                is MapUiState.Loading -> LoadingOverlay()
                is MapUiState.Empty -> MessageOverlay(text = "Không có cột đèn trong khu vực này")
                is MapUiState.Error ->
                    MessageOverlay(
                        text = (uiState as MapUiState.Error).message,
                        actionLabel = "Thử lại",
                        onAction = viewModel::retry,
                    )
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
        PoleQuickViewBottomSheet(
            pole = pole,
            onDismiss = { selectedPole = null },
            onOpenDetail = {
                selectedPole = null
                onOpenPoleDetail(pole.poleId)
            },
        )
    }

    selectedSegment?.let { segment ->
        RoadSegmentQuickViewBottomSheet(
            segment = segment,
            visiblePoleCount = uiState.polesOrEmpty().count { it.segmentId == segment.segmentId },
            onDismiss = { selectedSegment = null },
        )
    }
}

// What the map actually renders and hit-tests against — the FILTERED subset (FM-36: "Map chỉ
// nhận danh sách đã lọc"). Use allPolesOrEmpty() below for counts that must ignore the filter
// (the KPI chip row).
private fun MapUiState.polesOrEmpty() = (this as? MapUiState.Success)?.filteredDataset?.poles.orEmpty()

private fun MapUiState.roadSegmentsOrEmpty() = (this as? MapUiState.Success)?.filteredDataset?.segments.orEmpty()

// Full, unfiltered dataset — the KPI chip row always shows the true total/needs-attention count
// regardless of which status filter is currently active on the map.
private fun MapUiState.allPolesOrEmpty() = (this as? MapUiState.Success)?.dataset?.poles.orEmpty()

private fun MapUiState.allRoadSegmentsOrEmpty() = (this as? MapUiState.Success)?.dataset?.segments.orEmpty()

private fun MapUiState.centerOrNull() = (this as? MapUiState.Success)?.dataset?.center

private fun MapUiState.bboxOrNull() = (this as? MapUiState.Success)?.dataset?.bbox

private fun GisMapBbox.toLatLngBounds(): LatLngBounds =
    LatLngBounds.Builder()
        .include(LatLng(minLat, minLng))
        .include(LatLng(maxLat, maxLng))
        .build()

private fun Set<AssetCondition>.toggled(condition: AssetCondition): Set<AssetCondition> =
    if (condition in this) this - condition else this + condition

private fun RoadSegmentLine.toLatLngBounds(): LatLngBounds {
    val builder = LatLngBounds.Builder()
    coordinates.forEach { (lng, lat) -> builder.include(LatLng(lat, lng)) }
    return builder.build()
}

private fun setupMapLayers(
    context: Context,
    style: Style,
    uiState: MapUiState,
    showFixtures: Boolean,
    showRoadSegments: Boolean,
) {
    registerMarkerBadgeIcons(context, style)

    // Sources are added once here and never removed — only their layers get added/removed on a
    // toggle (see applyLayerVisibility), so re-adding a GeoJsonSource with the same id never
    // happens.
    style.addSource(GeoJsonSource(ROAD_SEGMENTS_SOURCE_ID, uiState.roadSegmentsOrEmpty().toGeoJson()))
    style.addSource(GeoJsonSource(POLES_SOURCE_ID, uiState.polesOrEmpty().toGeoJson(), buildClusterOptions()))

    if (showRoadSegments) addRoadSegmentLayers(style)
    if (showFixtures) addFixtureLayers(style)
}

// Turns on MapLibre's own "blue dot" (LocationComponent) instead of a hand-built GeoJSON
// marker — draws the dot, accuracy ring and pulse itself, and keeps working across pan/zoom.
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

// A cold GPS can take a long time to give its first location, and the blue dot only shows once a
// location exists. So ask for a fast, less exact location (Wi-Fi/cell, about 1-2 seconds) and show
// the dot with it. The GPS location replaces it as soon as it arrives.
@SuppressLint("MissingPermission")
private fun showQuickLocationFix(
    context: Context,
    map: MapLibreMap,
) {
    val request =
        CurrentLocationRequest
            .Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(QUICK_FIX_MAX_AGE_MS)
            .setDurationMillis(QUICK_FIX_TIMEOUT_MS)
            .build()
    LocationServices
        .getFusedLocationProviderClient(context)
        .getCurrentLocation(request, null)
        .addOnSuccessListener { location ->
            // Skip if GPS already gave a location, it is more exact than this one.
            // runCatching: the map may be destroyed (tab switch) before this callback runs.
            runCatching {
                val component = map.locationComponent
                if (location != null && component.isLocationComponentActivated && component.lastKnownLocation == null) {
                    component.forceLocationUpdate(location)
                }
            }
        }
}

// "Surveyed route" (F12/FM-37) — colored by has_active_segment_fault to match how Web GIS shows
// grid faults (MapMarkerColors.RouteFault / RouteNormal). Does NOT show a fault detail panel on
// tap — that is still Web GIS scope, mobile only needs the color as a visual warning for the
// field crew. Drawn as glow (wide, blurred, translucent) + core (narrow, solid).
private fun routeLineColorExpression(): Expression =
    Expression.switchCase(
        Expression.get("has_active_segment_fault"),
        Expression.color(routeColorArgb(hasActiveSegmentFault = true)),
        Expression.color(routeColorArgb(hasActiveSegmentFault = false)),
    )

private fun buildRoadSegmentsGlowLineLayer(): LineLayer =
    LineLayer(ROAD_SEGMENTS_GLOW_LINE_LAYER_ID, ROAD_SEGMENTS_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor(routeLineColorExpression()),
            PropertyFactory.lineWidth(8f),
            PropertyFactory.lineBlur(4f),
            PropertyFactory.lineOpacity(0.35f),
        )

private fun buildRoadSegmentsLineLayer(): LineLayer =
    LineLayer(ROAD_SEGMENTS_LINE_LAYER_ID, ROAD_SEGMENTS_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor(routeLineColorExpression()),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineOpacity(0.9f),
        )

// Toggling a SymbolLayer's visibility property off then back on does not repaint it on a real
// device (confirmed FM-37 field test: the layer's own visibility property reads back "visible"
// correctly, but the icons never reappear) — removing and re-adding the layer instead reliably redraws it. Sources are
// never removed/re-added here: only FakeMapRepository/RealMapRepository emit new GeoJSON, and
// re-adding a GeoJsonSource with the same id would throw.
private fun applyLayerVisibility(
    style: Style,
    showFixtures: Boolean,
    showRoadSegments: Boolean,
) {
    val fixturesPresent = style.getLayer(POLES_CIRCLE_LAYER_ID) != null
    when {
        showFixtures && !fixturesPresent -> addFixtureLayers(style)
        !showFixtures && fixturesPresent -> removeFixtureLayers(style)
    }
    val roadSegmentsPresent = style.getLayer(ROAD_SEGMENTS_LINE_LAYER_ID) != null
    when {
        showRoadSegments && !roadSegmentsPresent -> addRoadSegmentLayers(style)
        !showRoadSegments && roadSegmentsPresent -> removeRoadSegmentLayers(style)
    }
}

private fun addFixtureLayers(style: Style) {
    style.addLayer(buildClusterCircleLayer())
    style.addLayer(buildClusterCountLayer())
    style.addLayer(buildPoleSelectedHaloLayer())
    style.addLayer(buildPoleIconLayer())
    style.addLayer(buildPoiBadgeLayer())
    style.addLayer(buildIotBadgeLayer())
}

private fun removeFixtureLayers(style: Style) {
    style.removeLayer(CLUSTER_CIRCLE_LAYER_ID)
    style.removeLayer(CLUSTER_COUNT_LAYER_ID)
    style.removeLayer(POLES_SELECTED_HALO_LAYER_ID)
    style.removeLayer(POLES_CIRCLE_LAYER_ID)
    style.removeLayer(POLES_POI_BADGE_LAYER_ID)
    style.removeLayer(POLES_IOT_BADGE_LAYER_ID)
}

// Routes must sit below the pole layers — if fixtures are currently shown, insert directly
// below their bottom-most layer (the cluster circle) instead of appending on top of everything.
private fun addRoadSegmentLayers(style: Style) {
    val insertBelowId = CLUSTER_CIRCLE_LAYER_ID.takeIf { style.getLayer(it) != null }
    if (insertBelowId != null) {
        style.addLayerBelow(buildRoadSegmentsGlowLineLayer(), insertBelowId)
        style.addLayerBelow(buildRoadSegmentsLineLayer(), insertBelowId)
    } else {
        style.addLayer(buildRoadSegmentsGlowLineLayer())
        style.addLayer(buildRoadSegmentsLineLayer())
    }
}

private fun removeRoadSegmentLayers(style: Style) {
    style.removeLayer(ROAD_SEGMENTS_GLOW_LINE_LAYER_ID)
    style.removeLayer(ROAD_SEGMENTS_LINE_LAYER_ID)
}

// Marker shape per fixture_status (F12/FM-37, Design System 3.4) — SymbolLayer + the 4
// ic_marker_*.xml icons registered in registerMarkerBadgeIcons(), replacing the old flat-color
// CircleLayer dot. Icon size doubles as the "selected" state (see updateSelectedPoleStyle) via a
// separate match on pole_id, re-applied whenever the selection changes.
private fun buildPoleIconLayer(): SymbolLayer =
    SymbolLayer(POLES_CIRCLE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(
                Expression.match(
                    Expression.get("fixture_status"),
                    Expression.literal(AssetCondition.UNKNOWN.markerIconId()),
                    Expression.stop("normal", Expression.literal(AssetCondition.NORMAL.markerIconId())),
                    Expression.stop("dim", Expression.literal(AssetCondition.DIM.markerIconId())),
                    Expression.stop("out", Expression.literal(AssetCondition.OUT.markerIconId())),
                ),
            ),
            PropertyFactory.iconSize(selectedIconSizeExpression(NO_SELECTION_SENTINEL)),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.symbolSortKey(
                Expression.match(
                    Expression.get("fixture_status"),
                    Expression.literal(0),
                    Expression.stop("out", Expression.literal(3)),
                    Expression.stop("dim", Expression.literal(2)),
                    Expression.stop("normal", Expression.literal(1)),
                ),
            ),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).apply { setFilter(Expression.not(Expression.has("point_count"))) }

private fun buildPoleSelectedHaloLayer(): CircleLayer =
    CircleLayer(POLES_SELECTED_HALO_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(SELECTED_HALO_RADIUS),
            PropertyFactory.circleOpacity(0f),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeOpacity(1f),
            PropertyFactory.circleStrokeColor(MapMarkerColors.SelectionVector.toArgb()),
        ).apply { setFilter(selectedPoleFilter(NO_SELECTION_SENTINEL)) }

private fun selectedPoleFilter(selectedPoleId: String): Expression =
    Expression.eq(Expression.get("pole_id"), Expression.literal(selectedPoleId))

private fun selectedIconSizeExpression(selectedPoleId: String): Expression =
    Expression.match(
        Expression.get("pole_id"),
        Expression.literal(MARKER_ICON_SIZE),
        Expression.stop(selectedPoleId, Expression.literal(MARKER_ICON_SIZE_SELECTED)),
    )

private fun updateSelectedPoleStyle(
    style: Style,
    selectedPoleId: String?,
) {
    val id = selectedPoleId ?: NO_SELECTION_SENTINEL
    style.getLayer(POLES_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.iconSize(selectedIconSizeExpression(id)))
    // setFilter() is declared per-layer-subclass, not on the base Layer type style.getLayer()
    // returns — cast is needed even though we know which concrete type was added under this id.
    (style.getLayer(POLES_SELECTED_HALO_LAYER_ID) as? CircleLayer)?.setFilter(selectedPoleFilter(id))
}

private fun buildPoiBadgeLayer(): SymbolLayer =
    SymbolLayer(POLES_POI_BADGE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(POI_BADGE_ICON_ID),
            PropertyFactory.iconSize(1f),
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
            // FM-37 LOD rule: badges only worth showing once zoomed in enough to read them.
            minZoom = BADGE_MIN_ZOOM
        }

private fun buildIotBadgeLayer(): SymbolLayer =
    SymbolLayer(POLES_IOT_BADGE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(IOT_BADGE_ICON_ID),
            PropertyFactory.iconSize(0.45f),
            PropertyFactory.iconOffset(arrayOf(-13f, 13f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).apply {
            setFilter(
                Expression.all(
                    Expression.not(Expression.has("point_count")),
                    Expression.eq(Expression.get("has_iot_node"), Expression.literal(true)),
                ),
            )
            minZoom = BADGE_MIN_ZOOM
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
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor(
                Expression.match(
                    Expression.toNumber(Expression.get(CLUSTER_MAX_SEVERITY_PROPERTY)),
                    Expression.color(AssetCondition.UNKNOWN.markerColorArgb()),
                    Expression.stop(3, Expression.color(AssetCondition.OUT.markerColorArgb())),
                    Expression.stop(2, Expression.color(AssetCondition.DIM.markerColorArgb())),
                    Expression.stop(1, Expression.color(AssetCondition.NORMAL.markerColorArgb())),
                ),
            ),
        ).apply { setFilter(Expression.has("point_count")) }

// Count text switches from white to navy to stay readable on the now-white cluster fill.
// textFont must be a font this style's own glyphs endpoint actually hosts (confirmed: the
// liberty style serves "Noto Sans *", not MapLibre's SDK-default "Open Sans Regular,Arial
// Unicode MS Regular") — leaving textFont unset 404s on every glyph fetch, which silently
// fails this layer's whole symbol bucket instead of just the text.
private fun buildClusterCountLayer(): SymbolLayer =
    SymbolLayer(CLUSTER_COUNT_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#1F3864"),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textAllowOverlap(true),
        ).apply { setFilter(Expression.has("point_count")) }

@Composable
private fun BoxScope.LoadingOverlay() {
    Surface(
        modifier = Modifier.align(Alignment.Center),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        LuxLoadingRegion(description = "Đang tải bản đồ", modifier = Modifier.padding(Spacing.lg)) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun BoxScope.MessageOverlay(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    LuxBanner(
        message = text,
        tone = LuxBannerTone.INFO,
        modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.lg).fillMaxWidth(),
        actionLabel = actionLabel,
        onAction = onAction,
    )
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
        var destroyed = false
        val observer = mapView.lifecycleObserver(onDestroyed = { destroyed = true })
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // The screen can leave composition (for example when the user switches to another
            // tab) while the back stack entry is still alive. The observer is removed before it
            // gets ON_DESTROY, so without this the MapView would never be destroyed and its
            // native resources would leak on every visit. Stop it in the right order first.
            if (!destroyed) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    return mapView
}

private fun MapView.lifecycleObserver(onDestroyed: () -> Unit): LifecycleEventObserver =
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> onCreate(null)
            Lifecycle.Event.ON_START -> onStart()
            Lifecycle.Event.ON_RESUME -> onResume()
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_STOP -> onStop()
            Lifecycle.Event.ON_DESTROY -> {
                onDestroy()
                onDestroyed()
            }
            else -> Unit
        }
    }
