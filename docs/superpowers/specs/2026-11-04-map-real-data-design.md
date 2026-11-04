# F12 Map — Connect to Real GIS Data (Design Spec)

**Goal:** Replace `FakeMapRepository` (reads a bundled static `mock-poles.geojson`/`mock-segments.geojson`) with a `RealMapRepository` that calls the real backend GIS API, so F12 (Bản đồ hiện trường) shows the field's actual poles and road segments instead of 103 fixed fake ones.

**Spec status:** Architectural change — confirmed with the project owner across several turns of this conversation (the "Hướng B" decision below), written up here per `CLAUDE.md`'s step-by-step/no-silent-code-change rule before planning the implementation.

## Problem

`MapRepository` is bound to `FakeMapRepository` in `RepositoryModule.kt`. It reads two GeoJSON files from `app/src/main/assets/` — always the same 103 poles and 3 segments, regardless of which account logs in or what the real backend holds. The real backend already has a working, authenticated GIS API (confirmed live on `https://luxmap.com.vn`, returns `401` without a token, not `404`) that the web app already uses successfully. Mobile has simply never had a `RealMapRepository` written for it.

## Scope

- **In scope:** `RealMapRepository` for the poles + road segments layers only (`GET /map/poles`, `GET /map/segments`), wired into the existing `MapScreen`/`MapViewModel`/`MapUiState` (search, status filter, KPI chips, legend — all of that logic is real already, just fed by fake data today).
- **Out of scope (explicitly, not silently dropped):**
  - `PoleDetailRepository` (F12's "Chi tiết cột đèn" screen) — stays `Fake` for now; it already has 7 dead action buttons unrelated to its data source, a separate known gap.
  - `GET /map/iot-nodes`, `GET /map/cabinets`, `GET /map/cabinets/{id}/topology` — not used by any current mobile screen; not fetched.
  - Dynamic viewport-based re-fetching while panning/zooming ("Hướng A" from the brainstorm) — rejected in favor of the fixed-area approach below.
  - Any backend change — confirmed none is needed (see below).

## Approach — "Hướng B": one fixed bbox per login session, from data the app already has

The real `/map/poles` and `/map/segments` endpoints **require** a `bbox` query param (`minLng,minLat,maxLng,maxLat`) — there is no "fetch everything" mode, by backend design (CLAUDE.md anti-pattern: an unbounded query can't meet the 500ms budget). Mobile needs exactly one bbox to use for the whole map session, not a bbox per pan/zoom gesture.

**Found (no new backend work needed):** `GET /api/v1/auth/me` — the endpoint mobile already calls on every login/session refresh via `UserApi.me()` — already returns two fields mobile currently ignores:

```
"communes": [{ "commune_id", "name", "bbox": {min_lng,min_lat,max_lng,max_lat} | null, "center": {lat,lng} | null }, ...],
"map_bounds": { "bbox": {min_lng,min_lat,max_lng,max_lat}, "center": {lat,lng} } | null
```

`map_bounds` is the backend-computed union of every commune the caller is assigned to, plus its centre point — confirmed from `luxmap_backend`'s own doc comment: *"A user's map opens on the frames of their communes."* This is exactly the one bbox the map screen needs. No new endpoint, no mobile-side bbox computation, no extra network call (the response is already being fetched).

**Known gap this approach does not solve:** `bbox` on a commune (and therefore `map_bounds`) is `null` until a System Admin sets one for that commune on Web GIS. If every commune the user is assigned to has no frame set, `map_bounds` is `null`. Handled explicitly below (Empty state), not a crash.

### Data flow

1. `RealMapRepository` calls `UserApi.me()` to get `map_bounds`, the same way `RealHomeRepository`, `RealSurveyRepository`, and `RealProfileRepository` already each independently call it — there is no shared current-user cache anywhere in the app today (confirmed: all three call `userApi.me()` fresh), so this follows the established pattern rather than introducing new caching infrastructure. One extra `/auth/me` call per map open, consistent with the app's existing behavior elsewhere.
2. If `map_bounds` is `null` → `MapUiState.Empty` with a message explaining why (no fake "0 poles" — a real, distinct reason: *"Khu vực của bạn chưa được thiết lập khung bản đồ — liên hệ Quản trị viên"*), not `MapUiState.Error`.
3. Otherwise, call `GET /map/poles?bbox=<map_bounds.bbox>` and `GET /map/segments?bbox=<map_bounds.bbox>` (server already narrows results to the caller's own communes via `CommuneFilter.Narrow` — mobile does **not** need to also pass `commune_id`).
4. **413 `BBOX_TOO_LARGE`** (more than 2000 poles in the box): surfaced as `MapUiState.Error` with the real message from the error envelope (*"Zoom in"* — there is no client-side zoom to narrow by in this fixed-bbox approach, so this is reported as a real error for now, not silently retried; see Review Focus below) — not treated as a generic network failure.
5. Map `PoleFeatureDto`/`RoadSegmentFeatureDto` → `PoleMarker`/`RoadSegmentLine` (existing domain models, existing mapper extension functions already in the codebase) → `GisMapDataset`, same as `FakeMapRepository` does today. Existing search/filter/KPI code is untouched — it already operates on `GisMapDataset`.
6. Open the map camera centred on `map_bounds.center` (new — `FakeMapRepository`'s consumer currently has no real centre to open on).

### DTO corrections required (the current DTOs are stale — written against an older mock contract, not the real backend)

Confirmed by reading `luxmap_backend`'s real `PoleProperties`/`SegmentProperties` (`MapProperties.cs`) against mobile's current `PoleFeatureDto.kt`/`RoadSegmentFeatureDto.kt`:

- Mobile's comment says `GET /api/v1/poles` — the real route is `GET /api/v1/map/poles` (moved under `/map` by a backend drift note, "MAP-1").
- `segment_id` on a pole: mobile has it as non-null `String`; real backend has it nullable (a pole can exist without a segment — confirmed separately in this session's "POLE-SEGMENT-NULL" backend work). Must become `String?`.
- `power_source`, `fixture_type`, `lamp_watt`, `install_date`, `warranty_expiry`, `last_seen_at`, `last_sweep_id`: all non-null in mobile's DTO today; all **nullable** in the real backend (e.g. a pole with no lamp installed yet has no `power_source`). Must become nullable.
- Missing entirely from mobile's `PolePropertiesDto`: `lamp_count` (nullable `Int?`) and `warranty_status` (required `String`, maps to the same `WarrantyStatus` enum family as elsewhere) — both real fields, not currently parsed at all.
- `RoadSegmentPropertiesDto.controllerNodeId` (singular `String`) — the real field is `controller_node_ids` (plural array, `List<String>`), an explicitly documented **breaking** change on the backend side (old field was always null, so no real client ever depended on its value). `RoadSegmentLine` domain model doesn't currently use this field at all (per its own comment, "F12 mobile has no such panel so it's not needed") — so the DTO still needs the field renamed/retyped to deserialize correctly, but the domain mapper does not need to start exposing it.
- `length_m`: mobile has `Int`; real backend is `double` (declared length, not derived — can be fractional). Must become `Double`.

The existing `PoleMarker`/`RoadSegmentLine` domain models and their `toPoleMarker()`/`toRoadSegmentLine()` mappers only read a subset of these fields (`poleId`, `segmentId`, `fixtureStatus`, `powerSource`, `hasIotNode`, `nearSensitivePoi` for poles; `segmentId`, `name`, `hasActiveSegmentFault`, `lengthM`, `poleCount` for segments) — those mappers need small updates for the now-nullable fields they already touch (`segmentId`, `powerSource`), but do **not** need to start exposing every new field (`lamp_count`, `warranty_status`, etc.) unless a later task asks for it — YAGNI, matches the existing "domain model trimmed from DTO" pattern already documented in both files.

### What does NOT change

- `MapScreen.kt`, `MapViewModel.kt`'s search/filter/KPI logic, `MapUiState`'s 4-state shape, `GisMapDataset`, `PoleMarker`, `RoadSegmentLine` — all already real, working code against the (now-corrected) domain model. Only the DTOs and the repository implementation change.
- `PoleDetailRepository` stays Fake (out of scope, see above).

## Review Focus (for the implementation plan and its final review)

- **`map_bounds: null` must render `Empty` with the real reason, never silently fall back to showing fake/zero data.**
- **413 `BBOX_TOO_LARGE` must surface as a real, distinct error** — the existing `MapUiState.Error(message)` is reused, but the message shown must come from the server's real error body (*"That area holds N poles, more than 2000... Zoom in"*), not a generic "failed to load" string, since the current design has no pan/zoom-driven re-query to act on that advice yet (flagged as a known limitation of the fixed-bbox approach, not silently hidden).
- **Every nullable field the DTO correction introduces must actually be read as nullable through to the domain mapper** — a field changed to `String?` in the DTO but then force-unwrapped (`!!`) in `toPoleMarker()` is the same bug as today, just moved one layer down.
- **GeoJSON coordinate order** (`[lng, lat]`, not `[lat, lng]`) — already correctly handled by the existing mapper, must not regress when the DTO is touched.
- **`RealMapRepository` must call the real `UserApi.me()`, not a stale/mocked current-user source** — matches the exact pattern `RealHomeRepository`/`RealSurveyRepository`/`RealProfileRepository` already use; no new shared-cache infrastructure should be introduced for this task (out of scope, YAGNI).

---
