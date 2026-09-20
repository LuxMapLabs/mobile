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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.luxmap.core.map.markerIconId
import com.luxmap.core.map.registerMarkerBadgeIcons
import com.luxmap.core.map.routeColorArgb
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton
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
private const val POLES_GLOW_CIRCLE_LAYER_ID = "poles-glow-circle-layer"

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

// Marker icons are 24dp source vectors (see ic_marker_*.xml), so size 1.0f renders at their full
// 24dp and 1.3f at ~31dp for the selected pole — bumped up from the original 0.5f/0.75f, which
// made the icon detail (checkmark/x shape) too small to read on a real device.
private const val MARKER_ICON_SIZE = 1.0f
private const val MARKER_ICON_SIZE_SELECTED = 1.3f
private const val SELECTED_HALO_RADIUS = 20f

// Badges/labels only worth showing once zoomed in enough to read them (F12/FM-37 LOD rule) —
// below this the map is zoomed out far enough that individual badges would just be clutter.
private const val BADGE_MIN_ZOOM = 13f

// Extra bottom clearance so the legend never overlaps MapLibre's own attribution/logo control,
// which the map draws at the same bottom-start corner (F12/FM-37: "chừa vùng đáy trái cho
// attribution"). This is an estimate of the attribution row's height, not a measured value — it
// still needs a visual check on a real device/emulator.
private val LEGEND_BOTTOM_SAFE_PADDING = 32.dp

// "Surveyed route" layer (F12) — drawn below the pole markers, so add this layer first in
// z-order.
private const val ROAD_SEGMENTS_SOURCE_ID = "road-segments-source"
private const val ROAD_SEGMENTS_GLOW_LINE_LAYER_ID = "road-segments-glow-line-layer"
private const val ROAD_SEGMENTS_LINE_LAYER_ID = "road-segments-line-layer"

private const val LOCATE_ME_ZOOM = 17.0
private const val LOCATE_CAMERA_TRANSITION_MS = 750L
private const val LOCATION_TIMEOUT_MS = 10_000L
private const val LOCATION_POLL_INTERVAL_MS = 500L

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

// Camera zoom cap while on the satellite basemap — see the comment where
// map.setMaxZoomPreference() is called for why (rural satellite imagery gets blurry past its
// source resolution when over-zoomed). The vector basemap is drawn with lines so it doesn't
// blur, and allows zooming in much further.
private const val SATELLITE_MAX_ZOOM = 18.5
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
    var showFixtures by remember { mutableStateOf(true) }
    var showRoadSegments by remember { mutableStateOf(true) }
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
                            setupMapLayers(context, style, uiState, showFixtures, showRoadSegments)
                            // Style/source/layer are only guaranteed ready here (inside the
                            // onStyleLoaded callback) — assign maplibreMap at this point so the
                            // LaunchedEffects reacting to state don't run before the layers exist.
                            maplibreMap = map
                        }
                        map.addOnMapClickListener addOnMapClickListener@{ latLng ->
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
        // up under the status bar or the gesture navigation bar.
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Top area (F12/FM-36) — single search bar + KPI summary, replaces the old separate
            // basemap-toggle FAB and layer-toggle card that used to sit at TopStart/TopEnd.
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
                        .padding(start = Spacing.lg, end = Spacing.lg, bottom = LEGEND_BOTTOM_SAFE_PADDING),
            )

            // Right-side control stack (F12) — exactly 4 floating controls, declutters what used to
            // be 4 separate pieces scattered across all 4 corners: basemap, zoom in, zoom out,
            // locate, top to bottom. One shared Column + spacedBy() instead of each button computing
            // its own manual padding to avoid overlapping its neighbor.
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.End,
            ) {
                // Satellite <-> vector basemap toggle — the free satellite imagery is only sharp in
                // urban areas, rural areas (LuxMap's actual scope) have lower-resolution source
                // imagery so it looks noticeably softer/blurrier. There is no way to fix this in code
                // because the source imagery just doesn't have that resolution — this button lets
                // the field crew switch to the vector basemap (drawn with lines, always sharp) when
                // needed.
                FloatingActionButton(
                    onClick = {
                        isSatelliteBasemap = !isSatelliteBasemap
                        val map = maplibreMap ?: return@FloatingActionButton
                        map.setMaxZoomPreference(if (isSatelliteBasemap) SATELLITE_MAX_ZOOM else VECTOR_MAX_ZOOM)
                        val newStyleUrl = if (isSatelliteBasemap) MAP_STYLE_URL else VECTOR_STYLE_URL
                        map.setStyle(Style.Builder().fromUri(newStyleUrl)) { style ->
                            setupMapLayers(context, style, uiState, showFixtures, showRoadSegments)
                            // setStyle() drops the LocationComponent along with the rest of the old
                            // style — re-enable it here or the blue dot disappears after toggling
                            // basemap (see the NOTE on enableLocationComponent).
                            if (hasLocationPermission) {
                                enableLocationComponent(context, map, style)
                            }
                        }
                    },
                ) {
                    Text(
                        text = if (isSatelliteBasemap) "Vector" else "Vệ tinh",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                // Zoom +/- buttons — MapLibre Native has no built-in widget like maplibre-gl JS's
                // NavigationControl on the web, so add 2 plain FABs (56dp, meets the 48dp minimum
                // touch target from the Design System) that call
                // CameraUpdateFactory.zoomIn()/zoomOut() directly. Pinch-to-zoom still works as
                // usual, this is just an extra option.
                FloatingActionButton(onClick = { maplibreMap?.animateCamera(CameraUpdateFactory.zoomIn()) }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Phóng to")
                }
                FloatingActionButton(onClick = { maplibreMap?.animateCamera(CameraUpdateFactory.zoomOut()) }) {
                    // Icons.Filled.Remove is not in material-icons-core (only in the extended
                    // package, not in our dependencies) — use the "−" character instead of adding
                    // a new library.
                    Text(text = "−", style = MaterialTheme.typography.headlineSmall)
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
                ) {
                    Icon(imageVector = Icons.Filled.LocationOn, contentDescription = "Định vị vị trí hiện tại")
                }
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
                        Text(text = "Lớp bản đồ", style = MaterialTheme.typography.labelLarge)
                        MapLayerToggle(
                            showFixtures = showFixtures,
                            onShowFixturesChange = { showFixtures = it },
                            showRoadSegments = showRoadSegments,
                            onShowRoadSegmentsChange = { showRoadSegments = it },
                            modifier = Modifier.padding(vertical = Spacing.xs),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Spacing.sm),
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(text = "Hiển thị trạng thái cột", style = MaterialTheme.typography.labelLarge)
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

private fun Set<AssetCondition>.toggled(condition: AssetCondition): Set<AssetCondition> =
    if (condition in this) this - condition else this + condition

private fun RoadSegmentLine.toLatLngBounds(): LatLngBounds {
    val builder = LatLngBounds.Builder()
    coordinates.forEach { (lng, lat) -> builder.include(LatLng(lat, lng)) }
    return builder.build()
}

// Add every F12 source/layer onto a freshly loaded Style — used both for the first load AND
// every time the user taps the satellite/vector basemap toggle, because map.setStyle() replaces
// the whole style so all old sources/layers are gone and must be added again from scratch.
private fun setupMapLayers(
    context: Context,
    style: Style,
    uiState: MapUiState,
    showFixtures: Boolean,
    showRoadSegments: Boolean,
) {
    registerMarkerBadgeIcons(context, style)

    val segmentSource = GeoJsonSource(ROAD_SEGMENTS_SOURCE_ID, uiState.roadSegmentsOrEmpty().toGeoJson())
    style.addSource(segmentSource)
    // Add before the pole layer so the route draws below, markers on top. Glow first so the
    // solid core sits on top of it, same order as the pole glow/dot pair below.
    style.addLayer(buildRoadSegmentsGlowLineLayer())
    style.addLayer(buildRoadSegmentsLineLayer())

    val source = GeoJsonSource(POLES_SOURCE_ID, uiState.polesOrEmpty().toGeoJson(), buildClusterOptions())
    style.addSource(source)
    style.addLayer(buildClusterCircleLayer())
    style.addLayer(buildClusterCountLayer())
    // Halo below the glow (biggest radius at the bottom), glow below the icon — same idea as a
    // physical stack, each layer visible only where the one above it doesn't cover it.
    style.addLayer(buildPoleSelectedHaloLayer())
    style.addLayer(buildPoleGlowCircleLayer())
    style.addLayer(buildPoleIconLayer())
    style.addLayer(buildPoiBadgeLayer())
    style.addLayer(buildIotBadgeLayer())

    applyLayerVisibility(style, showFixtures, showRoadSegments)
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

// "Surveyed route" (F12/FM-37) — colored by has_active_segment_fault to match how Web GIS shows
// grid faults (Rose600 when faulted, Blue500 when normal). Does NOT show a fault detail panel on
// tap — that is still Web GIS scope, mobile only needs the color as a visual warning for the
// field crew. Drawn as glow (wide, blurred, translucent) + core (narrow, solid) — same 2-layer
// idea as the pole glow/dot pair, so routes read with the same visual language as markers.
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

// Re-apply visibility every time a toggle changes or AndroidView's update runs again — much
// cheaper than add/remove layer, and no need to reset the GeoJSON.
private fun applyLayerVisibility(
    style: Style,
    showFixtures: Boolean,
    showRoadSegments: Boolean,
) {
    val fixtureVisibility = if (showFixtures) Property.VISIBLE else Property.NONE
    val roadSegmentVisibility = if (showRoadSegments) Property.VISIBLE else Property.NONE
    style.getLayer(POLES_SELECTED_HALO_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(POLES_GLOW_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(POLES_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(POLES_POI_BADGE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(POLES_IOT_BADGE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(CLUSTER_CIRCLE_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(CLUSTER_COUNT_LAYER_ID)?.setProperties(PropertyFactory.visibility(fixtureVisibility))
    style.getLayer(ROAD_SEGMENTS_GLOW_LINE_LAYER_ID)?.setProperties(PropertyFactory.visibility(roadSegmentVisibility))
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
            // Lowered from 0.35f so the status icon reads as the focal point, not the glow behind
            // it, now that the icon itself is bigger (see MARKER_ICON_SIZE).
            PropertyFactory.circleOpacity(0.22f),
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
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).apply { setFilter(Expression.not(Expression.has("point_count"))) }

// Ring drawn only around the currently selected pole (F12/FM-37: "marker được chọn lớn hơn và có
// halo") — filtered to a single pole_id, re-applied whenever the selection changes (see
// updateSelectedPoleStyle). Starts filtered to NO_SELECTION_SENTINEL so it draws nothing before
// anything is selected.
private fun buildPoleSelectedHaloLayer(): CircleLayer =
    CircleLayer(POLES_SELECTED_HALO_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(SELECTED_HALO_RADIUS),
            PropertyFactory.circleColor("#FFFFFFFF"),
            PropertyFactory.circleOpacity(0.25f),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(
                Expression.match(
                    Expression.get("fixture_status"),
                    Expression.color(AssetCondition.UNKNOWN.markerColorArgb()),
                    Expression.stop("normal", Expression.color(AssetCondition.NORMAL.markerColorArgb())),
                    Expression.stop("dim", Expression.color(AssetCondition.DIM.markerColorArgb())),
                    Expression.stop("out", Expression.color(AssetCondition.OUT.markerColorArgb())),
                ),
            ),
        ).apply { setFilter(selectedPoleFilter(NO_SELECTION_SENTINEL)) }

private fun selectedPoleFilter(selectedPoleId: String): Expression =
    Expression.eq(Expression.get("pole_id"), Expression.literal(selectedPoleId))

private fun selectedIconSizeExpression(selectedPoleId: String): Expression =
    Expression.match(
        Expression.get("pole_id"),
        Expression.literal(MARKER_ICON_SIZE),
        Expression.stop(selectedPoleId, Expression.literal(MARKER_ICON_SIZE_SELECTED)),
    )

// Re-applied every time `selectedPole` changes (LaunchedEffect in MapScreen) — a Style's layers
// are mutable in place, no need to rebuild the whole style like the basemap toggle does.
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

// "Near sensitive area" badge (F12, near_sensitive_poi) — top-right corner of the dot. IoT
// badge goes bottom-right so a pole that is both near a POI and has an IoT node doesn't overlap
// badges. Badge icons are 24dp source vectors too, same as the marker, so 0.7f here against
// MARKER_ICON_SIZE = 1.0f keeps the badge at ~70% of the marker's size — big enough to actually
// read on a real device, still clearly smaller/secondary to the status icon. Offset scaled up to
// match, so the bigger badge still sits tucked at the marker's corner instead of drifting toward
// its center.
private fun buildPoiBadgeLayer(): SymbolLayer =
    SymbolLayer(POLES_POI_BADGE_LAYER_ID, POLES_SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(POI_BADGE_ICON_ID),
            PropertyFactory.iconSize(0.7f),
            PropertyFactory.iconOffset(arrayOf(9f, -9f)),
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
            PropertyFactory.iconSize(0.7f),
            PropertyFactory.iconOffset(arrayOf(9f, 9f)),
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
