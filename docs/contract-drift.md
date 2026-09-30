# Contract drift log

Temporary names, assumptions, and anything still pending Backend/WP2/WP5/firmware
sign-off, introduced while building the survey capture feature
(`docs/superpowers/specs/2026-09-28-survey-capture-design.md`).

Room table names below are **official** (WP6 authority per spec C8.5) — listed here
only for traceability, not because they are pending.

| Item | Current value in code | Status | Owner |
|---|---|---|---|
| `sync_queue` entity type for a survey session package | `survey_session_package` (string literal) | Internal WP6 choice, not yet reviewed by anyone else | WP6 |
| BLE service/characteristic UUID | placeholder constants in `LuxSensorBleClient` | Proposal only, not confirmed with firmware | WP6 + firmware owner |
| BLE packet byte layout (`seq`/`module_ms`/`lux` types, endianness) | uint16 LE / uint32 LE / float32 LE (proposed) | Proposal only | WP6 + firmware owner |
| BLE `boot_id` field | assumed present, format TBD with firmware | Proposal only | WP6 + firmware owner |
| "Kỹ sư bảo trì" == `Manager` role | assumed equivalent in F03 copy/comments | Assumption, not confirmed | thinh2509 |
| Upload endpoint for a session package | none — `UploadRepository` has no real implementation yet | Blocked on Backend | WP2/WP5 |
| Server-side field names for a session package | none | Blocked on Backend | WP2/WP5 |
| NOISE_REDUCTION_MODE / EDGE_MODE for video capture | left at camera default (neither forced OFF) | Pending WP4 decision — see ExposureLockController | WP4 |
| GPS-lost warning wiring in `RealSurveyCaptureController` | `onServiceDisconnected` now calls `bindService` again to reconnect | Fixed, not yet verified on a real device — no automated test possible (real `Intent`/`bindService`, same reason as `VideoCaptureSession`); see the real-device checklist in `2026-09-29-capture-viewfinder-implementation.md` | WP6 |
| `heading_log.ndjson` produces zero samples on some field devices | `LocationHeadingRecorder.start()` calls `sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)` with no null check before `registerListener` | Confirmed real on a real-device test (2026-09-29, Samsung SM-A075F): `adb shell dumpsys sensorservice` shows this device has no `rotation_vector` sensor and no magnetometer/gyroscope to synthesize one — `getDefaultSensor` returns `null`, `registerListener(listener, null, ...)` silently no-ops (no crash, no log), so the whole session's `heading_log.ndjson` has zero data lines. This was already flagged as a parked Minor during Task 17b's review ("can return null on rare low-end hardware") — now confirmed reproducible, not theoretical. Needs a decision (WP2/WP5/project owner) on a minimum hardware spec for Field Engineer devices, or a UI warning/readiness-check addition — not fixed yet, per explicit instruction | WP2/WP5 + project owner |
| F02 (Home) `WorkOrderCard` shows fewer fields than Design System v2.0 section 6.4's anatomy | `WorkOrderSummaryItem`/`WorkOrderCard` carry only `work_order_id`, `wo_status`, `due_date`, `priority_score` (raw number, not the Low/Normal/High/Urgent badge) | Real backend gap, not a naming placeholder — `GET /work-orders` (list) only returns those fields; `address`/`fault type`/`distance` per the Design System anatomy only exist on `GET /work-orders/{id}` (one order at a time), which F02's list view does not call per order (would be N+1 requests). `clusterLabel` is also the raw `commune_id` (e.g. `COM-001`), not a resolved commune name — no endpoint to look that up yet. Deliberate simplification, confirmed with the project owner (2026-09-29) | WP2/WP5 (endpoint), project owner (UI scope confirmed) |
