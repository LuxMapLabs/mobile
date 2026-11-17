# Bản đồ — đợt 2: Tủ điện, sơ đồ cấp nguồn, ranh giới xã — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the three map layers the web already has — electrical cabinets, a cabinet's power diagram with feeders that lost power, and the caller's commune outlines — and fix the map's network-error detection that phase 1 left broken.

**Architecture:** Data first (DTOs + `MapApi` + domain models + mappers), then the repository (`GisMapDataset` gains cabinets and communes; topology is a separate on-demand call), then `MapViewModel` state (selected cabinet + topology), then MapLibre layers, then the Compose surfaces (cabinet quick view, layer sheet, legend, search).

**Tech Stack:** Kotlin, Compose M3, MapLibre Native Android, Retrofit + kotlinx.serialization, Hilt, Coroutines/Flow, JUnit + MockK + Turbine, Compose UI Test.

**Spec:** `docs/design-system/screens/MAP.md` section 7 and decision M3 (approved 2026-11-17).

## Backend contract (read from `luxmap_backend` dev, 2026-11-17 — SELF-SIGNED drifts CABINET, TOPO-INFER, COMMUNE-BOUNDARY)

- All three: policy `ReadNetwork` (all four roles), server already narrows to the caller's communes. JSON is snake_case; enums are snake_case strings.
- `GET /api/v1/map/cabinets?bbox=minLng,minLat,maxLng,maxLat` (bbox optional, send the same bbox as poles) → GeoJSON `FeatureCollection` of Points. `properties`: `cabinet_id`, `cabinet_name`, `commune_id`, `feeder_ids: [string]`, `feeders: [{feeder_id, relay_no?, control_mode? (on|off|auto), mode_reported_at?, power_state (unknown|on|off)}]`, `iot_node_ids: [string]`, `iot_node_id?`, `photo_count`, `cover_photo?`.
- `GET /api/v1/map/cabinets/{cabinetId}/topology` → `{type, crs_note, unlocated_pole_count, unrouted_pole_count, features: [LineString with properties {feeder_id, segment_id, branch, order, from_id, to_pole_id, feeder_source (verified|inferred)}]}`. Edges carry **no** power state — join `feeder_id` with the selected cabinet's `feeders[].power_state`. 404 = absent/out of scope; 409 `POLE_SEGMENT_REQUIRED` = poles without a segment.
- `GET /api/v1/map/communes` → `FeatureCollection` of MultiPolygons, `properties`: `commune_id`, `name`.

## Global Constraints

- Branch `feat/map-cabinets-communes` from `dev` (b0762dd). Never commit to `main`. Commits ≤ 400 lines, `type(map): …`, English. **No Claude attribution, no new code comments.**
- DTO field names exactly as above; ignore unknown keys (existing Json config).
- Cabinet markers flat (no gradient) in the web colors, hex only in `core/map/MapMarkerColors.kt`: cabinet normal `#4F46E5`, cabinet fault (any feeder `power_state == off`) `#E11D48`; feeder line powered `#4F46E5` solid, unpowered `#E11D48` dashed; commune outline `#1F3864` 2dp, transparent fill; poles not on the selected cabinet drawn at opacity 0.35.
- Failing to load cabinets or communes must **not** break the map: those layers stay empty and poles/segments still show. Topology errors show inside the cabinet sheet only.
- Copy (exact): layer switches `Tủ điện`, `Ranh giới xã`; legend `Tủ điện điều khiển`, `Sơ đồ cấp nguồn (Tủ → Cột)`, `Lộ ngắt điện / Mất nguồn`, `Ranh giới xã`; cabinet sheet: feeder rows `Lộ <feeder_id>`, `Rơ-le <n>`, mode `Bật` / `Tắt` / `Tự động` / `Chưa rõ`, power `Có nguồn` / `Mất nguồn` / `Chưa rõ`, `<n> thiết bị IoT`, `<n> cột chưa có vị trí`, `<n> cột chưa gắn tuyến`, topology errors `Tủ này không thuộc địa bàn của bạn` (404), `Có cột chưa gắn tuyến, chưa vẽ được sơ đồ` (409), `Không tải được sơ đồ cấp nguồn` (other).
- Run tests and `ktlintCheck` as separate commands; `adb reverse tcp:5141 tcp:5141` after device runs; never drive the device by coordinates. Stop after each task for the user.

## Review Focus

1. **Cabinets/communes endpoint fails (500, 403) while poles succeed:** map still renders poles; no error banner. Repository test (Task 3).
2. **Offline map load:** `MapUiState.Error.isNetworkError == true` so the network copy shows. Repository + VM test (Task 1).
3. **Selecting another cabinet while a topology call is in flight:** the older response must not overwrite the newer cabinet's diagram. VM test (Task 4).
4. **Cabinet with no feeders / topology with zero edges:** sheet shows the cabinet, no crash, no diagram. Mapper + UI test (Tasks 2, 6).
5. **Deselecting a cabinet** (close sheet, tap elsewhere) clears the diagram and pole dimming. Manual + VM test (Tasks 4, 5).

---

### Task 1: Network errors reach the map

**Files:** `feature/map/data/RealMapRepository.kt`, `RealMapRepositoryTest.kt`, `MapViewModelTest.kt`.
- [ ] Failing test: `an offline IOException is still an IOException with a friendly message` — `mapApi.poles` throws `IOException` → the flow throws an `IOException` whose message is `Không có kết nối mạng. Vui lòng kiểm tra lại.` (replaces the old assertion that only checked the message).
- [ ] Implement `class MapNetworkException(cause: IOException) : IOException("Không có kết nối mạng. Vui lòng kiểm tra lại.", cause)`; the `IOException` branch throws it.
- [ ] Run `RealMapRepositoryTest`, `MapViewModelTest`, ktlint. Commit `fix(map): keep offline map errors recognisable as network errors`.

### Task 2: DTOs, API and domain models

**Files:** create `feature/map/data/dto/CabinetFeatureDto.kt`, `CabinetTopologyDto.kt`, `CommuneFeatureDto.kt`; create `feature/map/data/CabinetMarker.kt` (`CabinetMarker`, `CabinetFeeder`, `FeederMode`, `FeederPower`), `CabinetTopology.kt` (`CabinetTopology(edges, unlocatedPoleCount, unroutedPoleCount)`, `TopologyEdge(feederId, toPoleId, coordinates)`), `CommuneOutline.kt` (`CommuneOutline(communeId, name, polygons: List<List<List<Pair<Double,Double>>>>)`); modify `core/network/MapApi.kt`; tests `CabinetMappingTest.kt`, `CommuneMappingTest.kt`, `CabinetTopologyMappingTest.kt`.
- [ ] Failing tests decode a JSON sample per endpoint with the app's Json config and map to domain: cabinet with 2 feeders (`on` + `off`) → `hasPowerOff == true`, unknown enum strings map to `UNKNOWN`, feeders `[]` → `hasPowerOff == false`; topology with 0 features → empty edges + counts; commune MultiPolygon → polygons with `(lng, lat)` pairs.
- [ ] Implement DTOs + mappers (`toCabinetMarker()`, `toCabinetTopology()`, `toCommuneOutline()`), `MapApi.cabinets(bbox)`, `cabinetTopology(cabinetId)`, `communes()`.
- [ ] Run the three tests + ktlint. Commit `feat(map): add cabinet, topology and commune models and api`.

### Task 3: Repository

**Files:** `GisMapDataset.kt` (+`cabinets: List<CabinetMarker> = emptyList()`, `communes: List<CommuneOutline> = emptyList()`), `MapRepository.kt` (+`suspend fun cabinetTopology(cabinetId: String): CabinetTopology`), `RealMapRepository.kt`, `FakeMapRepository.kt`; create `CabinetTopologyException.kt` (`sealed class … NotInScope`, `PolesWithoutSegment`, `Failed`); tests in `RealMapRepositoryTest.kt`.
- [ ] Failing tests: cabinets + communes are fetched with the same bbox and land in the dataset; a 500 from `cabinets` or `communes` still emits poles/segments with that layer empty; topology 404 → `NotInScope`, 409 → `PolesWithoutSegment`, IOException → `MapNetworkException`.
- [ ] Implement (cabinets/communes via `runCatching { … }.getOrDefault(emptyList())` except `CancellationException`), Fake data: 2 cabinets (one with an `off` feeder), 1 commune square, topology linking a cabinet to 3 fake poles.
- [ ] Run tests + ktlint. Commit `feat(map): load cabinets, communes and cabinet topology`.

### Task 4: ViewModel state

**Files:** `MapViewModel.kt`, create `CabinetSelection.kt` (`data class CabinetSelection(cabinet: CabinetMarker, topology: TopologyState)`, `sealed interface TopologyState { Loading; Loaded(CabinetTopology); Error(message) }`), `MapSearchResults.kt` (+`cabinets`), `MapViewModelTest.kt`.
- [ ] Failing tests: `selectCabinet(id)` → `Loading` then `Loaded`; selecting B while A's call is suspended ends on B's topology (cancel the previous job); `clearCabinet()` → null; errors map to the exact copy; search matches `cabinet_name`/`cabinet_id`.
- [ ] Implement `val cabinetSelection: StateFlow<CabinetSelection?>`, `selectCabinet`, `clearCabinet` (a `topologyJob`).
- [ ] Run tests + ktlint. Commit `feat(map): hold the selected cabinet and its power diagram`.

### Task 5: Map layers

**Files:** `MapScreen.kt`, `core/map/MapMarkerColors.kt` (+cabinet/feeder/commune colors), `core/map/MapMarkerIcons.kt` (+cabinet icons), create `res/drawable/ic_cabinet_normal.xml`, `ic_cabinet_fault.xml` (flat 22dp rounded square, white lightning bolt, white ring + `#0F172A` edge).
- [ ] Layers (bottom → top): commune fill (opacity 0) + commune line; segments; topology lines (match `power` property: `off` → dashed `#E11D48`, else solid `#4F46E5`, width 3); pole layers (icon opacity 0.35 when a cabinet is selected and the pole is not a `to_pole_id`); cabinet symbol layer (icon by `has_power_off`); remove the IoT badge layer.
- [ ] Tap: candidates include cabinets (`TapCandidate` ids prefixed `cab:`), nearest wins; cabinet tap → `selectCabinet`; closing the sheet or tapping empty map → `clearCabinet`.
- [ ] Layer toggles `showCabinets`, `showCommunes` (rememberSaveable, default true) wired to add/remove like fixtures.
- [ ] Build, unit tests, install; **manual check by the user** (cabinet markers, diagram, dashed power-off lines, dimming, commune outline). Commit `feat(map): draw cabinets, the power diagram and commune outlines`.

### Task 6: Cabinet sheet, layer sheet, legend, search

**Files:** create `CabinetQuickViewBottomSheet.kt`; modify `MapLayerToggle.kt` (+2 switches), `MapLegend.kt` (+4 rows, −`Có thiết bị IoT`), `MapSearchResultsList.kt` (+cabinet section), `MapScreen.kt` wiring; tests `MapQuickViewTest.kt`, `MapChromeTest.kt`.
- [ ] Failing UI tests: sheet shows name, commune, one row per feeder with mode + power badge (icon + label), IoT count, `Đường đi`, `Đóng`; Loading shows `LuxLoadingRegion("Đang tải sơ đồ cấp nguồn")`; 409 copy; `unlocated_pole_count > 0` line; layer sheet has `Tủ điện` and `Ranh giới xã` switches; legend has the 4 new rows and no IoT row; search lists a cabinet and tapping it calls the callback.
- [ ] Implement with `QuickViewHeader`, `QuickViewFactRow`, `StatusBadge`, `LuxBanner`, `LuxSecondaryButton`.
- [ ] Run device `map.ui` + ktlint. Commit `feat(map): add the cabinet quick view, layer switches, legend rows and cabinet search`.

### Task 7: Large font, cleanup, spec status

- [ ] Large-font test for the cabinet sheet (feeder rows readable, `Đường đi`/`Đóng` ≥ 48dp).
- [ ] Device `map.ui` + unit `map.*`, `core.map.*` + ktlint (separately). Cleanup grep in `feature/map/ui`.
- [ ] Spec status line → `ĐỢT 1 VÀ ĐỢT 2 ĐÃ CODE XONG …`. Commit `test(map): cover the cabinet sheet at large font`.
