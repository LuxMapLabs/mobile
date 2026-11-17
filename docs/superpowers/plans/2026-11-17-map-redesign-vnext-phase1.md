# Bản đồ — Redesign vNext đợt 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the map chrome, pole/segment quick views and Pole detail to Design System vNext, switch pole markers to the web's flat round style, and pick markers within a 48dp tap square — no new data layer, API, Room or sync change (those are phase 2).

**Architecture:** Pure helpers first (`pickNearestFeature`, `MapMarkerColors`), then marker drawables + MapLibre layer wiring, then the Compose chrome components (search, KPI, FABs, legend), the layer sheet and quick-view sheets, and finally Pole detail (frame/content, then states + retry).

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01, M3), MapLibre Native Android, Hilt, Coroutines/Flow, JUnit + MockK + Turbine, Compose UI Test.

**Spec:** `docs/design-system/screens/MAP.md` (approved 2026-11-17, M1, M2, M4 for this phase).

## Global Constraints

- Branch `style/redesign-map-vnext` (from `dev` 339ce48, spec commit `34e10aa`). Never commit to `main`.
- Commits ≤ 400 changed lines, `type(map): description`, English, lowercase, imperative. **No Claude attribution.** No new code comments.
- No API, DTO, repository, Room, sync or navigation change. Keep: KPI total counts (FM-36), clustering, segment colors by `has_active_segment_fault`, location/permission handling, vector default + satellite option, every existing action.
- Only theme tokens in `feature/map/ui/`: no `Success600`, `Rose600`, `Amber500`, `Gray500`, `Blue*`, `Navy*`, `Color(0x…)`, `Radius.*`, legacy `*TextStyle`, `MessageState`. **Exception:** map marker/line colors live only in `core/map/MapMarkerColors.kt` and are referenced from map layers, legend and chart.
- Marker colors (exact, from web `markerUtils.ts`): normal `#10B981`, dim `#F59E0B`, out `#F43F5E`, unknown `#64748B`; POI badge `#F59E0B` + white star; marker edge `#0F172A`; selection ring vector `#1F3864`, satellite `#9DB7E0`.
- Touch targets ≥ 48dp; margin 16dp.
- Copy (exact): legend title `Chú giải`; legend rows `Đạt chuẩn`, `Đèn mờ`, `Hỏng/Tắt`, `Chưa quét`, `Gần trường, cầu`, `Có thiết bị IoT`, `Tuyến bình thường`, `Tuyến đang có sự cố`; basemap FAB description `Nền bản đồ: Vector. Chạm để đổi sang Vệ tinh` / `Nền bản đồ: Vệ tinh. Chạm để đổi sang Vector`; location FAB `Định vị vị trí hiện tại`; filter button `Lọc và lớp bản đồ`, while filtering `Lọc và lớp bản đồ. Đang lọc: ẩn N trạng thái`; layer sheet `Hiển thị trên bản đồ`, groups `Lớp bản đồ`, `Trạng thái cột`, switches `Cột đèn`, `Tuyến đường`; quick view actions `Đường đi`, `Chi tiết`, close `Đóng`; Pole detail title `Chi tiết cột đèn`, IoT `Trực tuyến` / `Mất kết nối`, loading `Đang tải chi tiết cột`, empty `Không tìm thấy cột đèn này`, error title `Không tải được chi tiết cột` + `Kiểm tra kết nối rồi thử lại.` / `Vui lòng thử lại sau ít phút.`.
- Unit: `./gradlew testLocalDebugUnitTest --tests "<fqcn>"`. Device (SM-A075F): `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"`, then always `adb reverse tcp:5141 tcp:5141`. Run tests and `./gradlew ktlintCheck` as **separate** commands. Stop after each task for the user.

## Review Focus

1. **Two markers close together inside one 48dp tap square:** the one nearest the finger opens; a tap on empty map near nothing still falls through to cluster/segment handling. Unit test (Task 1).
2. **Basemap switch:** the FAB always describes the current basemap, and after switching the pole/segment layers and the selected marker are restored (style reload). Manual check + UI test of the description (Task 3).
3. **Filtering hides statuses:** the filter button shows a dot and announces how many statuses are hidden; KPI chips still show total counts. UI test (Task 3/4).
4. **Pole detail offline:** an IOException shows the network copy with `Thử lại`, and retry re-subscribes once (no stacked collectors). VM test (Task 6).
5. **Satellite basemap legibility:** markers keep a visible edge on both basemaps (white ring + dark edge). Manual check (Task 7).

---

### Task 1: Tap picking and marker colors

**Files:**
- Create: `app/src/main/java/com/luxmap/core/map/MapMarkerColors.kt`, `app/src/main/java/com/luxmap/feature/map/ui/MapTapPicking.kt`
- Create: `app/src/test/java/com/luxmap/feature/map/ui/MapTapPickingTest.kt`, `app/src/test/java/com/luxmap/core/map/MapMarkerColorsTest.kt`
- Modify: `app/src/main/java/com/luxmap/core/map/MapLibreConfig.kt` (`markerColor()` reads `MapMarkerColors`)

**Interfaces — Produces:**
- `object MapMarkerColors { val Normal; val Dim; val Out; val Unknown; val PoiBadge; val Edge; val SelectionVector; val SelectionSatellite }` (all `androidx.compose.ui.graphics.Color`) and `fun AssetCondition.markerFill(): Color`
- `data class TapCandidate(val id: String, val x: Float, val y: Float)`; `fun pickNearestFeature(tapX: Float, tapY: Float, candidates: List<TapCandidate>): String?`; `fun tapSquare(tapX: Float, tapY: Float, density: Float): RectF` (48dp side)

- [ ] **Step 1: Failing tests**

```kotlin
class MapTapPickingTest {
    @Test
    fun `no candidate picks nothing`() {
        assertNull(pickNearestFeature(10f, 10f, emptyList()))
    }

    @Test
    fun `the candidate nearest the tap wins`() {
        val picked =
            pickNearestFeature(
                100f,
                100f,
                listOf(TapCandidate("FAR", 130f, 100f), TapCandidate("NEAR", 104f, 97f), TapCandidate("MID", 90f, 112f)),
            )
        assertEquals("NEAR", picked)
    }

    @Test
    fun `equal distances pick the smallest id so the result is stable`() {
        assertEquals("A", pickNearestFeature(0f, 0f, listOf(TapCandidate("B", 3f, 4f), TapCandidate("A", -3f, -4f))))
    }
}
```

`MapMarkerColorsTest`: `assertEquals(Color(0xFF10B981), AssetCondition.NORMAL.markerFill())` and the other three statuses. (`tapSquare` uses `android.graphics.RectF` — not unit-tested on the JVM; covered by the manual check.)

- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement**

```kotlin
fun pickNearestFeature(tapX: Float, tapY: Float, candidates: List<TapCandidate>): String? =
    candidates
        .minWithOrNull(compareBy<TapCandidate>({ (it.x - tapX) * (it.x - tapX) + (it.y - tapY) * (it.y - tapY) }, { it.id }))
        ?.id

fun tapSquare(tapX: Float, tapY: Float, density: Float): RectF {
    val half = 24f * density
    return RectF(tapX - half, tapY - half, tapX + half, tapY + half)
}
```

`MapMarkerColors` with the exact hex values from Global Constraints; `markerColor()` in `MapLibreConfig.kt` returns `markerFill()`.
- [ ] **Step 4: Run both tests + ktlint; PASS.**
- [ ] **Step 5: Commit** `feat(map): pick markers within a 48dp tap square and add web marker colors`

---

### Task 2: Flat round markers and map wiring

**Files:** `res/drawable/ic_marker_{normal,dim,out,unknown}.xml` (rewrite), `res/drawable/ic_poi_badge.xml` (rewrite), delete `res/drawable/ic_marker_badge_{dim,out,unknown}.xml`; `core/map/MapMarkerIcons.kt` (drop badge ids/registration); `feature/map/ui/MapScreen.kt` (`addOnMapClickListener` pole branch, `buildPoleIconLayer`, `buildStatusBadgeLayer` removal, `buildPoiBadgeLayer`/`buildIotBadgeLayer` offsets, `selectedIconSizeExpression`, `buildPoleSelectedHaloLayer` color by basemap, cluster ring color via `markerFill()`).

- [ ] **Step 1: Drawables** — each `ic_marker_*.xml`: `width/height 18dp`, viewport 18; one circle path r=7.25 centered (9,9) with `fillColor` = status hex, `strokeColor #FFFFFFFF`, `strokeWidth 2`; a second circle r=8.5 with `strokeColor #FF0F172A`, `strokeWidth 1`, transparent fill (dark edge). `ic_poi_badge.xml`: 10dp circle `#FFF59E0B` with white 1dp stroke + white 5-point star path.
- [ ] **Step 2: Wiring**
  - Tap: replace the single-point pole query with `tapSquare(screenPoint.x, screenPoint.y, density)` → `queryRenderedFeatures(rect, POLES_CIRCLE_LAYER_ID)` → map each feature with a `Point` geometry to `TapCandidate(pole_id, projected x, projected y)` → `pickNearestFeature`. Cluster and segment branches stay point-based and unchanged.
  - Selected size: `selectedIconSizeExpression` uses `24f / 18f` for the selected pole, `1f` otherwise.
  - Halo color: pass `isSatelliteBasemap` into `setupMapLayers` / `updateSelectedPoleStyle`; `MapMarkerColors.SelectionSatellite` vs `SelectionVector`.
  - Remove the status badge layer and its add/remove calls; keep POI and IoT badges with offsets moved to the circle's top-right / bottom-left.
  - Out poles on top: `symbolSortKey` = 3 out, 2 dim, 1 normal, 0 unknown.
- [ ] **Step 3: Build + run `MapViewModelTest` + install and launch on device; check markers render on vector and satellite** (manual — no MapLibre UI test exists). Ledger what was checked.
- [ ] **Step 4: Commit** `style(map): draw pole markers as flat web-style circles`

---

### Task 3: Chrome — search, KPI, basemap and location buttons

**Files:** `MapSearchBar.kt`, `MapKpiChipRow.kt`, `MapScreen.kt` (FAB block); create `app/src/androidTest/java/com/luxmap/feature/map/ui/MapChromeTest.kt`.

**Interfaces — Produces:** `MapSearchBar(query, onQueryChange, onFilterClick, hiddenStatusCount: Int = 0, modifier)`; `@Composable fun BasemapToggleButton(isSatellite: Boolean, onToggle: () -> Unit, modifier)` (new, in `MapControls.kt`); `@Composable fun LocateMeButton(isLocating: Boolean, onClick: () -> Unit, modifier)`.

- [ ] **Step 1: Failing UI tests** (`MapChromeTest`)
  - `basemap_button_describes_the_current_basemap`: `BasemapToggleButton(isSatellite = false)` → node with description `Nền bản đồ: Vector. Chạm để đổi sang Vệ tinh`, `assertTouchTargetIsAtLeast(48.dp)`; click calls `onToggle`.
  - `filter_button_announces_hidden_statuses`: `MapSearchBar(hiddenStatusCount = 2)` → description `Lọc và lớp bản đồ. Đang lọc: ẩn 2 trạng thái` and tag `map_filter_dot` exists; with 0 → `Lọc và lớp bản đồ`, no dot.
  - `kpi_chips_keep_48dp_targets`: `MapKpiChipRow(...)` chip `Cần chú ý` (existing label — read the file) ≥ 48dp.
  - `locate_button_is_56dp_and_labelled`: `LocateMeButton(isLocating = false)` description `Định vị vị trí hiện tại`, size ≥ 56dp.
- [ ] **Step 2: Implement**
  - Search bar: `Surface(shape = shapes.medium, color = surface, shadowElevation = 3.dp, border = BorderStroke(1.dp, outlineVariant))` containing the existing text field + trailing `IconButton(Icons.Filled.Layers)` with the description above and a 8dp `error`-colored dot (tag `map_filter_dot`) when `hiddenStatusCount > 0`.
  - KPI chips: `Surface` chips 32dp high in a `heightIn(min = 48.dp)` click area, `surface` + `outlineVariant` border, colored dot from `MapMarkerColors` for status chips, count `labelLarge` in a `widthIn(min = 20.dp)` box, label `labelMedium`. Keep existing chips and their behavior.
  - `BasemapToggleButton`: `SmallFloatingActionButton(containerColor = surface, contentColor = onSurface)` with icon `Icons.Filled.Map` (vector) / `Icons.Filled.Satellite` (satellite) and the description; `LocateMeButton`: `FloatingActionButton(containerColor = surface)` 56dp, icon `Icons.Filled.MyLocation`, spinner when locating. `MapScreen` uses both; `hiddenStatusCount = AssetCondition.entries.size - statusFilter.size`.
- [ ] **Step 3: Run `MapChromeTest` on device + ktlint; PASS.**
- [ ] **Step 4: Commit** `style(map): rebuild the search bar, kpi chips and map buttons`

---

### Task 4: Legend and layer sheet

**Files:** `MapLegend.kt`, `MapLayerToggle.kt`, `MapStatusFilterRow.kt`, `MapScreen.kt` (layer sheet block); `MapChromeTest.kt`.

- [ ] **Step 1: Failing UI tests**
  - `legend_lists_the_web_labels_with_real_marker_icons`: expand `MapLegend` (tap `Chú giải`), assert the 8 labels from Global Constraints exist; the header row has `Role.Button` and `stateDescription` `Đang mở` after tap.
  - `layer_switches_are_switch_roles`: `MapLayerToggle` rows `Cột đèn`, `Tuyến đường` have `Role.Switch` and toggle callbacks.
  - `status_filters_are_lux_filter_chips_with_labels`: `MapStatusFilterRow` shows `Đạt chuẩn`/`Đèn mờ`/`Hỏng/Tắt`/`Chưa quét`, selected ones `assertIsSelected()`.
- [ ] **Step 2: Implement**
  - Legend: collapsed `Surface` card (surface + border + 3dp shadow) with `Chú giải` `labelLarge` + chevron, `clickable(role = Role.Button)` + `stateDescription`; expanded rows draw `painterResource(ic_marker_*)` / `ic_poi_badge` / `ic_iot_badge` at 18dp + label `labelMedium`; route rows draw a 24×4dp line in the segment colors (normal = `MapMarkerColors` route normal = existing Blue500 value moved into `MapMarkerColors.RouteNormal`, fault = `MapMarkerColors.RouteFault`, the existing Rose600 value) — move both route colors from `MapScreen.routeLineColorExpression()` into `MapMarkerColors`.
  - Layer toggle: rows 56dp `Modifier.toggleable(role = Role.Switch)` + `Switch`, labels `Cột đèn`, `Tuyến đường`.
  - Status filter: `LuxFilterChip(label = <legend label>, selected, onClick)` with a leading colored dot; keep `enabled`.
  - Layer sheet in `MapScreen`: title `Hiển thị trên bản đồ` `titleMedium`, group headers via `LuxSectionHeader("Lớp bản đồ")`, `LuxSectionHeader("Trạng thái cột")`.
- [ ] **Step 3: Run `MapChromeTest` + ktlint; PASS.**
- [ ] **Step 4: Commit** `style(map): rebuild the legend and the layer sheet`

---

### Task 5: Quick-view sheets and map overlays

**Files:** `PoleQuickViewBottomSheet.kt`, `RoadSegmentQuickViewBottomSheet.kt`, `MapScreen.kt` (`LoadingOverlay`, `MessageOverlay`); create `app/src/androidTest/java/com/luxmap/feature/map/ui/MapQuickViewTest.kt`.

**Interfaces — Changes:** `PoleQuickViewBottomSheet(pole, onDismiss, onOpenDetail, onNavigate: () -> Unit = {})` — `MapScreen` passes `openInMaps(context, pole.lat, pole.lng)` (reuse the helper used by Pole detail; read it first). If the sheet already has a navigate action, keep it and only restyle.

- [ ] **Step 1: Failing UI tests**
  - Pole sheet: pole id `titleLarge`, status badge label, `Đóng` button calls `onDismiss`, `Đường đi` and `Chi tiết` exist and call their callbacks, each ≥ 48dp.
  - Segment sheet: segment name, `Đóng` calls `onDismiss`.
- [ ] **Step 2: Implement** — header row (title + `IconButton(Close, "Đóng")`), `StatusBadge` for asset status, existing stats in `LuxCard`-like rows (`bodyMedium`/`onSurfaceVariant`), actions `LuxSecondaryButton("Đường đi")` + `PrimaryButton("Chi tiết")` in a row with `weight(1f)`. Overlays: `LoadingOverlay` → `LuxLoadingRegion("Đang tải bản đồ")` on a `surface` card; `MessageOverlay` → `LuxBanner(message, tone = LuxBannerTone.INFO)` (permission/offline messages keep their copy).
- [ ] **Step 3: Run `MapQuickViewTest` + ktlint; PASS.**
- [ ] **Step 4: Commit** `style(map): rebuild the quick-view sheets and map overlays`

---

### Task 6: Pole detail — frame, content, states and retry

**Files:** `PoleDetailScreen.kt`, `PoleDetailViewModel.kt`, `PoleDetailUiState.kt`, `LuminanceTrendChart.kt`; create `app/src/test/java/com/luxmap/feature/map/ui/PoleDetailViewModelTest.kt`, `app/src/androidTest/java/com/luxmap/feature/map/ui/PoleDetailScreenTest.kt`.

**Interfaces — Produces:** `PoleDetailUiState.Error(message, isNetworkError = false)`; `PoleDetailViewModel.retry()`; `PoleDetailScreen(uiState, onBack, modifier, onNavigateToPole, onRetry: () -> Unit = {})`.

- [ ] **Step 1: Failing tests**
  - VM: repository flow throws `IOException` → `Error(isNetworkError = true)`; `retry()` → `Loading` then `Success` from a second flow (stub `observePoleDetail` with `returnsMany`); retry cancels the previous collector (keep a `Job`).
  - UI: `LuxChildTopBar` title `Chi tiết cột đèn` + subtitle pole id in Success; Loading description `Đang tải chi tiết cột`; Empty `Không tìm thấy cột đèn này`; Error network copy + `Thử lại` calls `onRetry`, no raw message; IoT online shows `Trực tuyến`; `Đường đi` ≥ 48dp.
- [ ] **Step 2: Implement** — VM `loadJob` + `retry()` + `isNetworkError = e is IOException`; screen: `LuxChildTopBar`, sections via `LuxSectionHeader` + `LuxCard`, `PriorityAlertCard` → `LuxBanner` (out = ERROR, dim = WARNING), `NavigateToPoleButton` → `LuxSecondaryButton("Đường đi", leadingIcon = Directions)`, delete `SecondaryOutlinedButton`, `LoadingState`, `MessageState`; IoT label `Trực tuyến`; `LuminanceTrendChart` colors from `markerFill()` (and the threshold line from `MapMarkerColors.Dim`). Keep previews compiling (update their calls). Split into 2 commits if > 400 lines (frame+content, then states+VM).
- [ ] **Step 3: Run VM test, `PoleDetailScreenTest` on device, ktlint; PASS.**
- [ ] **Step 4: Commit** `style(map): rebuild pole detail with vnext components and retry`

---

### Task 7: Large font, device run, cleanup, spec status

- [ ] **Step 1:** `MapLargeFontTest` (1.5×, 360dp): `MapSearchBar` filter button, `BasemapToggleButton`, pole quick view `Chi tiết`, Pole detail `Đường đi` all ≥ 48dp and displayed.
- [ ] **Step 2:** Run device packages `com.luxmap.feature.map.ui` + unit `com.luxmap.feature.map.*`, `com.luxmap.core.map.*` + ktlint (separately). All PASS.
- [ ] **Step 3:** Cleanup grep in `feature/map/ui/` for `Success600|Rose600|Amber500|Gray500|Blue[0-9]|Navy|Color\(0x|Radius\.|TextStyle|MessageState`; only `core/map/MapMarkerColors.kt` may hold hex.
- [ ] **Step 4:** Manual on device (ledger it): markers on vector + satellite, tap between two close markers, basemap switch keeps selection, filter dot, legend.
- [ ] **Step 5:** Spec status → `**ĐỢT 1 ĐÃ CODE XONG** trên nhánh style/redesign-map-vnext. Đợt 2 (tủ điện, sơ đồ cấp nguồn, ranh giới xã) chưa làm. Còn nợ kiểm tra tay (Light/Night Field, 200%).`
- [ ] **Step 6: Commit** `test(map): cover the map chrome and pole detail at large font`
