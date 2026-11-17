# Pole GPS Update — Photo Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-pole "measure GPS" screen in the `pole_gps_update` work-order flow with a "take a photo of the pole, GPS attached" screen, and wire the captured photos to the backend's new `pole_id`-linked evidence endpoint, without changing the already-shipped `complete`/`pole_locations[]` reporting path.

**Architecture:** The existing `local_pole_gps_measurement` table stays the single source of truth for `lat/lng/accuracy_m/captured_at` per pole (unchanged `completionPoleLocations()`/`CompleteWorkOrderSyncHandler` logic, zero risk to already-tested code) — it gains 3 nullable columns to also track the captured photo's file path and upload status. The existing `local_work_order_evidence` table/`UploadWorkOrderEvidenceSyncHandler` (built for F10's repair/inspection photos) is reused as-is for pole_gps_update's photos too, with one new nullable `poleId` column — this is the same table and sync handler EV-3/EV-4 already extended server-side for exactly this purpose. Per the user's explicit choice, photo uploads are enqueued only when "Gửi báo cáo" is pressed (not immediately on capture), matching this feature's own existing deferred-submit pattern, not the survey pole-photo feature's immediate-upload pattern.

**Tech Stack:** Same as the rest of this codebase — Kotlin, Jetpack Compose, Room, Hilt, Retrofit/OkHttp multipart, WorkManager sync_queue. System camera via Intent (`ActivityResultContracts.TakePicture()`), matching `PolePhotoCaptureScreen`/`EvidenceCaptureScreen` — no CameraX, no new library.

**Spec:** This plan has no separate spec file — the full business requirement and the exact backend contract were established and confirmed directly in chat across this session (confirmed from `luxmap_backend` source at commit `3dc41c4` on `dev`, PRs #251/#255, drift entries EV-3/EV-4) and from `luxmap-web` source (`WorkOrderPoleLocation` type, `FieldReportDetailModal.tsx`). The prior design spec `docs/superpowers/specs/2026-11-13-pole-gps-update-design.md` describes the OLD (GPS-only, no photo) flow and is now partially superseded by this plan for Task 3 of its section 3.4 ("Measure screen") — the rest of that doc (PDF download, attachments, Room v16→v17, detail-screen layout) is unaffected and still accurate.

## Global Constraints

- Backend contract (confirmed from source, not guessed):
  - `POST /work-orders/{id}/evidence` (multipart/form-data): `file` (JPEG, ≤16MiB), `kind` (must be `"observation"` for this task kind — `before`/`after` → 400 `EVIDENCE_KIND_NOT_ALLOWED`), `captured_at`, `lat`, `lng` (all 4 required, missing any → 400 `VALIDATION_FAILED`), `client_op_id` (optional, idempotency — replay matches parent + kind + `pole_id` exactly, including `NULL`; mismatch → 409 `IDEMPOTENCY_CONFLICT`), `pole_id` (NEW, optional — must be a target pole of the order via the same `WorkOrderPoleService.Targets(id)` mechanism `pole_locations` already uses, and in the order's commune; accepts a pole with `geom = null`; wrong pole → 404 `POLE_NOT_FOUND` or 409 `POLE_NOT_IN_WORK_ORDER`). Response (`EvidenceItem`) now carries `pole_id` (nullable). **No `accuracy_m` field exists on this endpoint** — accuracy stays solely in `pole_locations[]`.
  - `complete`'s `pole_locations[{pole_id, lat, lng, accuracy_m, captured_at}]` is **unchanged** — do not touch `PoleGpsRepository.completionPoleLocations()`, `CompleteWorkOrderSyncHandler`, or their tests in this plan.
- No accuracy gate, no hold-timer, no `PoleFixGate`/`FixQuality` in the new capture screen — the user explicitly asked to drop this. A photo is taken and accepted on the spot; whatever GPS accuracy is available at that moment is recorded (and still sent in `pole_locations[]`), never blocks the shutter.
- Photo uploads are enqueued to `sync_queue` only when "Gửi báo cáo" is pressed, not immediately on capture (explicit user choice, confirmed in chat) — capture itself only writes Room.
- File/route names stay as `PoleGpsMeasure*`/`Routes.PoleGpsMeasure` — this plan rewrites their internals (measuring → photo capture), not their names, to avoid an unrelated rename touching `NavGraph.kt`, `WorkOrderDetailScreen.kt`'s callback, and `Routes.kt` for no functional reason.
- Reuse existing patterns exactly as the codebase already does them: `PolePhotoCaptureScreen`'s camera-intent + preview + retake UX (closest existing precedent — per-pole photo capture) for the new screen; `UploadWorkOrderEvidenceSyncHandler`'s existing multipart upload code for the new sync call (just add one field); `AppMigrations.kt`'s additive-ALTER-TABLE style for the new migration.
- ktlint after every task. No new library.

## Review Focus

1. A pole that already had a photo captured, then gets retaken before submit: the old file is replaced and only ONE evidence upload is ever enqueued for that pole (not two), matching the "REPLACE" conflict strategy already used by `upsertDeviceMeasurement` (Task 3 repository test).
2. Capturing a photo with no location permission granted does not crash — `lat/lng/accuracy_m` fall back to `0.0`/`0.0`/`0.0`, matching `EvidenceCaptureViewModel`'s existing `?: 0.0` precedent (Task 4 ViewModel test).
3. Submitting a report with some poles never photographed still succeeds (partial reporting, PG-10, unchanged `canSubmit` rule) — only poles that DO have a captured photo get an evidence-upload op enqueued; the missing ones are simply absent from both `pole_locations[]` (unchanged) and the evidence uploads (Task 3 repository test).
4. A second "Gửi báo cáo" tap (e.g. after a `return` and a partial recapture) does not re-enqueue an evidence upload for a pole whose photo already finished uploading — only pending ones are picked up (Task 3 repository test).
5. The CAMERA permission is a hard requirement (some OEM camera apps crash the whole process via `SecurityException` if the calling app lacks it — already known from `EvidenceCaptureScreen`'s own comment) — the new screen must request it exactly the same way, never skip it (Task 5 screen test / manual check).

---

### Task 1: Room schema — link evidence to a pole, track a measurement's photo

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/entity/LocalWorkOrderEvidenceEntity.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsEntities.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/dao/WorkOrderCompletionDao.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsDao.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppMigrations.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt` (version bump, no entity list change needed — both entities already registered)
- Modify: `app/src/main/java/com/luxmap/di/DatabaseModule.kt` (register the new migration)
- Test: `app/src/androidTest/java/com/luxmap/core/database/MigrationTest.kt` (or wherever the existing 16→17 migration test lives — find it with the brief's own grep below)

**Interfaces:**
- Produces: `LocalWorkOrderEvidenceEntity.poleId: String?`; `LocalPoleGpsMeasurementEntity.photoFilePath: String?`, `.evidenceClientOpId: String?`, `.evidenceUploadStatus: String?` (values used later: `null` = no photo, `"pending"` = captured, not yet queued, `"queued"` = enqueued, `"synced"` = upload confirmed done); `PoleGpsDao.updateEvidenceUploadStatus(workOrderId, poleId, status)`; `PoleGpsDao.pendingPhotoMeasurements(workOrderId): List<LocalPoleGpsMeasurementEntity>` (rows with `evidenceUploadStatus = "pending"`); `WorkOrderCompletionDao.insertEvidence(evidence)` (unchanged signature, entity gains the field); `MIGRATION_17_18`.
- Consumes: nothing new from other tasks (first task).

- [ ] **Step 1: Find the existing migration test**

Run: `grep -rln "MIGRATION_16_17" app/src/androidTest`

Read whatever file(s) it finds in full before Step 2 — this plan's new test (Step 2) must follow that file's exact existing conventions (test class, helper functions, `MigrationTestHelper` usage) rather than inventing a new style.

- [ ] **Step 2: Write the failing migration test**

In the file found by Step 1, add (adjust the exact helper/assert calls to match that file's established style once you've read it — the shape below is what must be proven, not literal copy-paste):

```kotlin
@Test
fun migrate17To18_addsPhotoColumnsWithoutLosingData() {
    helper.createDatabase(TEST_DB, 17).apply {
        execSQL(
            "INSERT INTO local_pole_gps_measurement (workOrderId, poleId, lat, lng, accuracyM, capturedAt, source) " +
                "VALUES ('WO-1', 'P-1', 10.0, 106.0, 5.0, 1000, 'device')",
        )
        execSQL(
            "INSERT INTO local_work_order_evidence (clientOpId, workOrderId, kind, filePath, capturedAt, lat, lng, uploadStatus) " +
                "VALUES ('OP-1', 'WO-1', 'observation', '/f/a.jpg', 1000, 10.0, 106.0, 'pending')",
        )
        close()
    }
    val db = helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)
    val measurement = db.query("SELECT photoFilePath, evidenceClientOpId, evidenceUploadStatus FROM local_pole_gps_measurement WHERE poleId = 'P-1'")
    assertTrue(measurement.moveToFirst())
    assertTrue(measurement.isNull(0))
    assertTrue(measurement.isNull(1))
    assertTrue(measurement.isNull(2))
    val evidence = db.query("SELECT poleId FROM local_work_order_evidence WHERE clientOpId = 'OP-1'")
    assertTrue(evidence.moveToFirst())
    assertTrue(evidence.isNull(0))
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<the fully-qualified test class from Step 1>`
Expected: FAIL — `MIGRATION_17_18` does not exist, or the `18` version/migration isn't registered yet (compile error or runtime "no migration found").

- [ ] **Step 4: Implement**

`LocalWorkOrderEvidenceEntity.kt` — add one field:

```kotlin
@Entity(tableName = "local_work_order_evidence")
data class LocalWorkOrderEvidenceEntity(
    @PrimaryKey val clientOpId: String,
    val workOrderId: String,
    val kind: String,
    val filePath: String,
    val capturedAt: Instant,
    val lat: Double,
    val lng: Double,
    val uploadStatus: String,
    val poleId: String? = null,
)
```

`PoleGpsEntities.kt` — extend the measurement entity:

```kotlin
@Entity(tableName = "local_pole_gps_measurement", primaryKeys = ["workOrderId", "poleId"])
data class LocalPoleGpsMeasurementEntity(
    val workOrderId: String,
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
    val source: String,
    val photoFilePath: String? = null,
    val evidenceClientOpId: String? = null,
    val evidenceUploadStatus: String? = null,
)
```

`AppMigrations.kt` — add after `MIGRATION_16_17`:

```kotlin
val MIGRATION_17_18: Migration =
    object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_pole_gps_measurement ADD COLUMN photoFilePath TEXT")
            db.execSQL("ALTER TABLE local_pole_gps_measurement ADD COLUMN evidenceClientOpId TEXT")
            db.execSQL("ALTER TABLE local_pole_gps_measurement ADD COLUMN evidenceUploadStatus TEXT")
            db.execSQL("ALTER TABLE local_work_order_evidence ADD COLUMN poleId TEXT")
        }
    }
```

`AppDatabase.kt` — bump `version = 17` to `version = 18`.

`DatabaseModule.kt` — add `MIGRATION_17_18` next to `MIGRATION_16_17` in both the import and the `.addMigrations(...)` call.

`PoleGpsDao.kt` — add:

```kotlin
    @Query(
        "UPDATE local_pole_gps_measurement SET evidenceUploadStatus = :status " +
            "WHERE workOrderId = :workOrderId AND poleId = :poleId",
    )
    abstract suspend fun updateEvidenceUploadStatus(
        workOrderId: String,
        poleId: String,
        status: String,
    )

    @Query(
        "SELECT * FROM local_pole_gps_measurement WHERE workOrderId = :workOrderId AND evidenceUploadStatus = 'pending'",
    )
    abstract suspend fun pendingPhotoMeasurements(workOrderId: String): List<LocalPoleGpsMeasurementEntity>
```

`WorkOrderCompletionDao.kt` — no signature change needed; `insertEvidence(evidence: LocalWorkOrderEvidenceEntity)` already accepts the entity, which now simply carries the new nullable field.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<the fully-qualified test class from Step 1>`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/data/entity/LocalWorkOrderEvidenceEntity.kt app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsEntities.kt app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsDao.kt app/src/main/java/com/luxmap/core/database/AppMigrations.kt app/src/main/java/com/luxmap/core/database/AppDatabase.kt app/src/main/java/com/luxmap/di/DatabaseModule.kt app/src/androidTest/java/com/luxmap/core/database/MigrationTest.kt
git commit -m "feat(pole-gps): add Room v18 columns linking evidence to a pole"
```

(Adjust the test file path in the `git add` to whatever Step 1 actually found.)

---

### Task 2: Backend wiring — `pole_id` on the evidence upload

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandler.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandlerTest.kt`

**Interfaces:**
- Consumes: `LocalWorkOrderEvidenceEntity.poleId` (Task 1).
- Produces: `WorkOrdersApi.uploadEvidence(..., poleId: RequestBody? = null)` — the new param is optional/defaulted so F10's existing call sites (repair/inspection "after"/"observation" evidence, which never pass a pole) need no change at all.

- [ ] **Step 1: Write the failing test**

Find the existing test for this handler (it already exists — this plan extends it, does not replace it):

```bash
grep -n "fun \`" app/src/test/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandlerTest.kt
```

Read the whole file, then add (matching its existing mock/assertion style exactly):

```kotlin
@Test
fun `a pole-linked evidence row sends its pole_id`() =
    runTest {
        val evidence = evidenceRow(poleId = "P-1")
        coEvery { dao.evidenceByClientOpId(evidence.clientOpId) } returns evidence
        handler.handle(payload(evidence.clientOpId)) {}
        coVerify {
            api.uploadEvidence(
                id = evidence.workOrderId,
                file = any(),
                kind = any(),
                capturedAt = any(),
                lat = any(),
                lng = any(),
                clientOpId = any(),
                poleId = withArg { assertEquals("P-1", it.string()) },
            )
        }
    }

@Test
fun `an evidence row with no pole sends no pole_id`() =
    runTest {
        val evidence = evidenceRow(poleId = null)
        coEvery { dao.evidenceByClientOpId(evidence.clientOpId) } returns evidence
        handler.handle(payload(evidence.clientOpId)) {}
        coVerify { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any(), poleId = null) }
    }
```

Use whatever this file's existing helpers are named for building a row/payload and reading a `RequestBody`'s string (`.string()` via `okhttp3.RequestBody.string()`-equivalent test helper, or `Buffer`) — read the file first; do not invent a second helper if one already exists for the other 5 required fields.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*UploadWorkOrderEvidenceSyncHandlerTest*"`
Expected: FAIL — compile error, `uploadEvidence(...)` has no `poleId` parameter yet.

- [ ] **Step 3: Implement**

`WorkOrdersApi.kt` — add one optional part to the existing method:

```kotlin
    @Multipart
    @POST("api/v1/work-orders/{id}/evidence")
    suspend fun uploadEvidence(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Part("kind") kind: RequestBody,
        @Part("captured_at") capturedAt: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("client_op_id") clientOpId: RequestBody,
        @Part("pole_id") poleId: RequestBody? = null,
    ): EvidenceItemDto
```

`UploadWorkOrderEvidenceSyncHandler.kt` — pass it through:

```kotlin
                api.uploadEvidence(
                    id = evidence.workOrderId,
                    file = filePart,
                    kind = evidence.kind.toRequestBody(),
                    capturedAt = evidence.capturedAt.toString().toRequestBody(),
                    lat = evidence.lat.toString().toRequestBody(),
                    lng = evidence.lng.toString().toRequestBody(),
                    clientOpId = evidence.clientOpId.toRequestBody(),
                    poleId = evidence.poleId?.toRequestBody(),
                )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*UploadWorkOrderEvidenceSyncHandlerTest*"`
Expected: PASS, including the pre-existing tests in this file (unaffected by an optional, defaulted new parameter).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt app/src/main/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandler.kt app/src/test/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandlerTest.kt
git commit -m "feat(pole-gps): send pole_id on evidence uploads that have one"
```

---

### Task 3: Repository — capture a photo, enqueue uploads at submit time

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepositoryTest.kt`

**Interfaces:**
- Consumes: `PoleGpsDao.updateEvidenceUploadStatus`, `.pendingPhotoMeasurements` (Task 1); `WorkOrderCompletionDao.insertEvidence` (Task 1, now accepting `poleId`).
- Produces: `PoleGpsRepository.saveCapturedPhoto(workOrderId, poleId, filePath, lat, lng, accuracyM, capturedAt)` (replaces `saveDeviceMeasurement` as the only way a device measurement gets created — delete `saveDeviceMeasurement`, nothing else calls it after Task 4); `PoleGpsRepository.enqueuePendingPhotoUploads(workOrderId)`; `PoleMeasurement.photoFilePath: String?` (so the detail screen can tell "has a photo" apart from "server-prefilled, no photo").

This repository needs `WorkOrderCompletionDao` and `SyncQueueManager` injected — neither is currently a constructor parameter. Both are already provided elsewhere in this module graph (`WorkOrderCompletionDao` via whatever module provides it for `RealWorkOrderCompletionRepository`; `SyncQueueManager` is a plain `@Inject`-constructed class already used by `RealPolePhotoRepository`) — adding them as new constructor parameters needs no new Hilt module code, only updating this class's constructor and its 2 call sites (`RepositoryModule`'s binding, and this test file).

- [ ] **Step 1: Write the failing tests**

Add to `PoleGpsRepositoryTest.kt` (read the file first — it already has a `row(...)` helper and the `repository` field; extend the constructor call and mocks to match, do not duplicate):

```kotlin
@Test
fun `saveCapturedPhoto stores a device measurement with the photo path and a pending upload status`() =
    runTest {
        val saved = slot<LocalPoleGpsMeasurementEntity>()
        coEvery { dao.upsertDeviceMeasurement(capture(saved)) } returns Unit
        repository.saveCapturedPhoto("WO-1", "P-1", "/f/p1.jpg", 10.0, 106.0, 5.0, Instant.ofEpochSecond(9))
        assertEquals("/f/p1.jpg", saved.captured.photoFilePath)
        assertEquals("pending", saved.captured.evidenceUploadStatus)
        assertEquals(MeasurementSourceColumn.DEVICE, saved.captured.source)
    }

@Test
fun `enqueuePendingPhotoUploads inserts evidence rows and marks them queued, skipping measurements with no photo`() =
    runTest {
        val withPhoto =
            row("P-1", MeasurementSourceColumn.DEVICE).copy(photoFilePath = "/f/p1.jpg", evidenceUploadStatus = "pending")
        coEvery { dao.pendingPhotoMeasurements("WO-1") } returns listOf(withPhoto)
        repository.enqueuePendingPhotoUploads("WO-1")
        coVerify {
            completionDao.insertEvidence(
                withArg {
                    assertEquals("WO-1", it.workOrderId)
                    assertEquals("P-1", it.poleId)
                    assertEquals("/f/p1.jpg", it.filePath)
                    assertEquals("observation", it.kind)
                    assertEquals("pending", it.uploadStatus)
                },
            )
        }
        coVerify { syncQueueManager.enqueue(opType = "upload_work_order_evidence", payloadJson = any(), clientOpId = any()) }
        coVerify { dao.updateEvidenceUploadStatus("WO-1", "P-1", "queued") }
    }

@Test
fun `enqueuePendingPhotoUploads does nothing when there are no pending photos`() =
    runTest {
        coEvery { dao.pendingPhotoMeasurements("WO-1") } returns emptyList()
        repository.enqueuePendingPhotoUploads("WO-1")
        coVerify(exactly = 0) { completionDao.insertEvidence(any()) }
        coVerify(exactly = 0) { syncQueueManager.enqueue(any(), any(), any(), any()) }
    }
```

Update this file's `repository` construction to pass 2 new mocks (`completionDao`, `syncQueueManager`) matching whatever this plan's Step 3 ends up naming the new constructor parameters — and delete the now-obsolete `saveDeviceMeasurement`-specific test (`saving a device measurement stores it as DEVICE with the fix values`), since Step 3 removes that method.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*PoleGpsRepositoryTest*"`
Expected: FAIL — compile error, `saveCapturedPhoto`/`enqueuePendingPhotoUploads` don't exist, constructor arity mismatch.

- [ ] **Step 3: Implement**

In `PoleGpsRepository.kt`:

```kotlin
@Singleton
class PoleGpsRepository
    @Inject
    constructor(
        private val dao: PoleGpsDao,
        private val downloader: AttachmentDownloader,
        private val scheduler: AttachmentDownloadScheduler,
        private val detailSync: PoleGpsDetailSync,
        private val poleCache: PoleListCacheDao,
        private val completionDao: com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao,
        private val syncQueueManager: com.luxmap.core.sync.SyncQueueManager,
    ) {
```

Replace `saveDeviceMeasurement` with:

```kotlin
        suspend fun saveCapturedPhoto(
            workOrderId: String,
            poleId: String,
            filePath: String,
            lat: Double,
            lng: Double,
            accuracyM: Double,
            capturedAt: Instant,
        ) {
            dao.upsertDeviceMeasurement(
                LocalPoleGpsMeasurementEntity(
                    workOrderId = workOrderId,
                    poleId = poleId,
                    lat = lat,
                    lng = lng,
                    accuracyM = accuracyM,
                    capturedAt = capturedAt,
                    source = MeasurementSourceColumn.DEVICE,
                    photoFilePath = filePath,
                    evidenceUploadStatus = "pending",
                ),
            )
        }

        suspend fun enqueuePendingPhotoUploads(workOrderId: String) {
            dao.pendingPhotoMeasurements(workOrderId).forEach { measurement ->
                val clientOpId = java.util.UUID.randomUUID().toString()
                completionDao.insertEvidence(
                    com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity(
                        clientOpId = clientOpId,
                        workOrderId = workOrderId,
                        kind = "observation",
                        filePath = measurement.photoFilePath!!,
                        capturedAt = measurement.capturedAt,
                        lat = measurement.lat,
                        lng = measurement.lng,
                        uploadStatus = "pending",
                        poleId = measurement.poleId,
                    ),
                )
                syncQueueManager.enqueue(
                    opType = "upload_work_order_evidence",
                    payloadJson = kotlinx.serialization.json.Json.encodeToString(
                        com.luxmap.feature.workorder.data.sync.EvidenceSyncPayload(clientOpId),
                    ),
                    clientOpId = clientOpId,
                )
                dao.updateEvidenceUploadStatus(workOrderId, measurement.poleId, "queued")
            }
        }
```

(Check `EvidenceSyncPayload`'s real package/shape with `grep -rn "class EvidenceSyncPayload" app/src/main/java` before writing the import — use the exact existing type, do not redeclare it.)

Add `photoFilePath` to the domain `PoleMeasurement` data class and its `toDomain()` mapper:

```kotlin
data class PoleMeasurement(
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
    val source: MeasurementSource,
    val photoFilePath: String? = null,
)
```

```kotlin
private fun LocalPoleGpsMeasurementEntity.toDomain() =
    PoleMeasurement(
        poleId = poleId,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        capturedAt = capturedAt,
        source = if (source == MeasurementSourceColumn.DEVICE) MeasurementSource.DEVICE else MeasurementSource.SERVER,
        photoFilePath = photoFilePath,
    )
```

Update `RepositoryModule.kt`'s (or wherever `PoleGpsRepository` is constructed — it is `@Inject`-constructed directly via Hilt, so likely no explicit `@Provides`/`@Binds` for it exists; if `grep -n "PoleGpsRepository" app/src/main/java/com/luxmap/di/*.kt` finds nothing, there is nothing to change here — Hilt resolves the 2 new constructor params automatically since both types are already available in the graph).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*PoleGpsRepositoryTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepositoryTest.kt
git commit -m "feat(pole-gps): capture a pole's photo and enqueue its upload at submit time"
```

---

### Task 4: Measure ViewModel + UiState — photo capture instead of a GPS gate

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModel.kt`
- Delete: `app/src/main/java/com/luxmap/feature/workorder/polegps/domain/PoleFixGate.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModelTest.kt` (full rewrite)
- Delete: `app/src/test/java/com/luxmap/feature/workorder/polegps/domain/PoleFixGateTest.kt` (if it exists — `find app/src/test -iname "*PoleFixGate*"` to check)

**Interfaces:**
- Consumes: `PoleGpsRepository.saveCapturedPhoto` (Task 3); `GpsFixSource.fixes()` (unchanged, already injected).
- Produces: `PoleGpsMeasureUiState.Success(poleLabel, previous)` (drops `liveFix`/`quality`/`canSave`/`saved`/`stored` entirely); `PoleGpsMeasureViewModel.outputFile(filesDir)`; `PoleGpsMeasureViewModel.onPhotoCaptured(file, hasLocationPermission, onDone)`.

- [ ] **Step 1: Write the failing tests**

Replace `PoleGpsMeasureViewModelTest.kt` entirely with:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PoleGpsMeasureViewModelTest {
    private val repository = mockk<PoleGpsRepository>(relaxed = true)
    private var fixesFlow: Flow<GpsFix> = flowOf(GpsFix(10.1, 106.1, 4.0, Instant.ofEpochSecond(1)))
    private val source =
        object : GpsFixSource {
            override fun fixes(): Flow<GpsFix> = fixesFlow
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 3, 10.0, 106.0)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        PoleGpsMeasureViewModel(SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")), repository, source)

    @Test
    fun `starts in Loading then shows the pole label and previous position once permission is granted`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals("Cột 3 · P-1", state.poleLabel)
            assertEquals(3, state.previous?.position)
        }

    @Test
    fun `a pole outside the order is Empty`() =
        runTest {
            coEvery { repository.poleReference("WO-1", "P-1") } returns null
            val vm = viewModel()
            vm.onPermissionGranted()
            assertEquals(PoleGpsMeasureUiState.Empty, vm.state.value)
        }

    @Test
    fun `denied permission is an Error that explains the cause`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionDenied()
            assertTrue((vm.state.value as PoleGpsMeasureUiState.Error).message.contains("quyền vị trí"))
        }

    @Test
    fun `onPhotoCaptured with location permission saves the fix from the gps source`() =
        runTest {
            fixesFlow = flowOf(GpsFix(10.5, 106.5, 7.0, Instant.ofEpochSecond(42)))
            val vm = viewModel()
            var done = false
            vm.onPhotoCaptured(File("/f/p1.jpg"), hasLocationPermission = true) { done = true }
            coVerify {
                repository.saveCapturedPhoto("WO-1", "P-1", "/f/p1.jpg", 10.5, 106.5, 7.0, Instant.ofEpochSecond(42))
            }
            assertTrue(done)
        }

    @Test
    fun `onPhotoCaptured without location permission saves zeroed coordinates instead of crashing`() =
        runTest {
            val vm = viewModel()
            var done = false
            vm.onPhotoCaptured(File("/f/p1.jpg"), hasLocationPermission = false) { done = true }
            coVerify { repository.saveCapturedPhoto("WO-1", "P-1", "/f/p1.jpg", 0.0, 0.0, 0.0, any()) }
            assertTrue(done)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*PoleGpsMeasureViewModelTest*"`
Expected: FAIL — compile error, old `PoleGpsMeasureUiState.Success` fields/`onSave`/`onMeasureAgain` referenced by the OLD test are gone from this new test (fine, it's a full replacement), and the new methods don't exist on the ViewModel yet.

- [ ] **Step 3: Implement**

`PoleGpsMeasureUiState.kt` — replace entirely:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import com.luxmap.feature.workorder.polegps.data.PoleReference

sealed interface PoleGpsMeasureUiState {
    data object Loading : PoleGpsMeasureUiState

    data class Success(
        val poleLabel: String,
        val previous: PoleReference?,
    ) : PoleGpsMeasureUiState

    data object Empty : PoleGpsMeasureUiState

    data class Error(
        val message: String,
    ) : PoleGpsMeasureUiState
}
```

`PoleGpsMeasureViewModel.kt` — replace entirely:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PoleGpsMeasureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PoleGpsRepository,
        private val fixSource: GpsFixSource,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle["workOrderId"])
        private val poleId: String = checkNotNull(savedStateHandle["poleId"])
        private val _state = MutableStateFlow<PoleGpsMeasureUiState>(PoleGpsMeasureUiState.Loading)
        val state: StateFlow<PoleGpsMeasureUiState> = _state.asStateFlow()

        fun onPermissionGranted() {
            viewModelScope.launch {
                val reference = repository.poleReference(workOrderId, poleId)
                _state.value =
                    if (reference == null) {
                        PoleGpsMeasureUiState.Empty
                    } else {
                        PoleGpsMeasureUiState.Success(poleLabel(reference), reference)
                    }
            }
        }

        fun onPermissionDenied() {
            _state.value = PoleGpsMeasureUiState.Error("Cần cấp quyền vị trí để chụp ảnh cột đèn.")
        }

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "pole-gps/$workOrderId")
            dir.mkdirs()
            return File(dir, "$poleId-${UUID.randomUUID()}.jpg")
        }

        fun onPhotoCaptured(
            file: File,
            hasLocationPermission: Boolean,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                val fix = if (hasLocationPermission) fixSource.fixes().first() else null
                repository.saveCapturedPhoto(
                    workOrderId = workOrderId,
                    poleId = poleId,
                    filePath = file.absolutePath,
                    lat = fix?.lat ?: 0.0,
                    lng = fix?.lng ?: 0.0,
                    accuracyM = fix?.accuracyM ?: 0.0,
                    capturedAt = fix?.capturedAt ?: Instant.now(),
                )
                onDone()
            }
        }

        private fun poleLabel(reference: PoleReference): String =
            reference.position?.let { "Cột $it · ${reference.poleId}" } ?: reference.poleId
    }
```

Delete `PoleFixGate.kt` and its test (if any — check Step 1's finding).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*PoleGpsMeasureViewModelTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureUiState.kt app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModel.kt app/src/test/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModelTest.kt
git add -u app/src/main/java/com/luxmap/feature/workorder/polegps/domain/
git commit -m "feat(pole-gps): replace the GPS accuracy gate with a photo capture step"
```

---

### Task 5: Measure screen — camera capture, preview, retake

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureScreen.kt` (full rewrite of the body; `PoleGpsMeasureRoute`'s signature is unchanged — still just `onBack: () -> Unit`)
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureScreenTest.kt` (full rewrite)

**Interfaces:**
- Consumes: `PoleGpsMeasureViewModel.outputFile`, `.onPhotoCaptured`, `.onPermissionGranted`, `.onPermissionDenied` (Task 4); `PoleGpsMeasureUiState.Success/Empty/Error/Loading` (Task 4).
- Produces: nothing new consumed elsewhere — `PoleGpsMeasureRoute` is already wired into `NavGraph.kt` from the earlier plan and needs no change there.

- [ ] **Step 1: Write the failing tests**

Replace `PoleGpsMeasureScreenTest.kt` entirely with:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.polegps.data.PoleReference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PoleGpsMeasureScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        state: PoleGpsMeasureUiState,
        onRetryPermission: () -> Unit = {},
    ) = rule.setContent {
        LuxMapTheme { PoleGpsMeasureContent(state, onBack = {}, onRetryPermission = onRetryPermission) }
    }

    @Test
    fun loading_state_is_announced() {
        show(PoleGpsMeasureUiState.Loading)
        rule.onNodeWithText("Đang chuẩn bị", substring = true).assertExists()
    }

    @Test
    fun empty_state_explains_the_pole_is_not_in_the_order() {
        show(PoleGpsMeasureUiState.Empty)
        rule.onNodeWithText("không thuộc lệnh này", substring = true).assertExists()
    }

    @Test
    fun error_state_offers_a_retry() {
        var retried = false
        show(PoleGpsMeasureUiState.Error("Cần cấp quyền vị trí"), onRetryPermission = { retried = true })
        rule.onNodeWithText("Thử lại").performClick()
        assertTrue(retried)
    }

    @Test
    fun back_button_invokes_onBack() {
        var backed = false
        rule.setContent {
            LuxMapTheme {
                PoleGpsMeasureContent(PoleGpsMeasureUiState.Loading, onBack = { backed = true }, onRetryPermission = {})
            }
        }
        rule.onNodeWithContentDescription("Quay lại").performClick()
        assertTrue(backed)
    }

    @Test
    fun top_bar_shows_the_pole_label_once_ready() {
        show(PoleGpsMeasureUiState.Success("Cột 3 · P-1", PoleReference("P-1", 3, 10.0, 106.0)))
        rule.onNodeWithText("Cột 3 · P-1").assertExists()
    }
}
```

The `Success` state's own body (camera auto-launching, preview, retake/confirm) is driven by `PoleGpsMeasureRoute`'s local Compose state (`pendingFile`/`previewFile`, same architecture as `PolePhotoCaptureRoute`) wired to real `ActivityResultContracts.TakePicture()` launchers — these cannot run in a `createComposeRule()` unit-style test (no real camera app on CI/no fixture), so this plan does NOT add an automated test for the capture/preview/retake interaction itself; it is covered by the real-device manual check in Task 7, the same scope limit this codebase already accepted for `PolePhotoCaptureScreen`/`PolePhotoCaptureRoute` (see `CLAUDE.md`'s own note on that decision).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.polegps.ui.measure.PoleGpsMeasureScreenTest`
Expected: FAIL — compile error, `PoleGpsMeasureContent` has a different signature (still takes `onSave`/`onMeasureAgain` from the old implementation) and no top-bar pole-label text exists yet in the `Success` branch under the old body.

- [ ] **Step 3: Implement**

Replace `PoleGpsMeasureScreen.kt` entirely:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.PrimaryButton
import java.io.File

@Composable
fun PoleGpsMeasureRoute(
    onBack: () -> Unit,
    viewModel: PoleGpsMeasureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            previewFile = if (success && file != null) file else null
        }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    // CAMERA is a hard requirement here - some OEM camera apps (confirmed on a real Samsung
    // device) deny the ACTION_IMAGE_CAPTURE intent with a SecurityException, crashing the whole
    // process, when the calling app does not hold this permission itself. ACCESS_FINE_LOCATION
    // stays best-effort, matching EvidenceCaptureScreen/PolePhotoCaptureScreen.
    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (result[Manifest.permission.CAMERA] == true) {
                viewModel.onPermissionGranted()
                launchCamera()
            } else {
                viewModel.onPermissionDenied()
            }
        }

    fun requestPermissionsAndStart() {
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        when {
            cameraGranted -> {
                viewModel.onPermissionGranted()
                launchCamera()
            }
            hasRequestedPermissions -> viewModel.onPermissionDenied()
            else -> {
                hasRequestedPermissions = true
                permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }

    LaunchedEffect(Unit) { requestPermissionsAndStart() }

    val fileToPreview = previewFile
    if (fileToPreview != null) {
        PhotoPreview(
            file = fileToPreview,
            isSaving = isSaving,
            onConfirm = {
                isSaving = true
                viewModel.onPhotoCaptured(fileToPreview, hasLocationPermission, onBack)
            },
            onRetake = {
                fileToPreview.delete()
                previewFile = null
                launchCamera()
            },
        )
    } else {
        PoleGpsMeasureContent(state = state, onBack = onBack, onRetryPermission = ::requestPermissionsAndStart)
    }
}

@Composable
fun PoleGpsMeasureContent(
    state: PoleGpsMeasureUiState,
    onBack: () -> Unit,
    onRetryPermission: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val title = if (state is PoleGpsMeasureUiState.Success) state.poleLabel else "Chụp ảnh cột đèn"
        AppTopBar(title = title, onBack = onBack)
        when (state) {
            PoleGpsMeasureUiState.Loading -> CenteredMessage("Đang chuẩn bị chụp ảnh...", showProgress = true)
            PoleGpsMeasureUiState.Empty ->
                CenteredMessage("Cột này không thuộc lệnh này.", actionLabel = "Quay lại", onAction = onBack)
            is PoleGpsMeasureUiState.Error ->
                CenteredMessage(state.message, actionLabel = "Thử lại", onAction = onRetryPermission)
            is PoleGpsMeasureUiState.Success ->
                CenteredMessage("Đang mở camera...", showProgress = true)
        }
    }
}

@Composable
private fun PhotoPreview(
    file: File,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        AsyncImage(
            model = file,
            contentDescription = "Ảnh cột đèn vừa chụp",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(Radius.card)),
        )
        CaptureActions(enabled = !isSaving, isSaving = isSaving, onRetake = onRetake, onConfirm = onConfirm)
    }
}

@Composable
private fun CaptureActions(
    enabled: Boolean,
    isSaving: Boolean,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedButton(
            onClick = onRetake,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
        ) { Text("Chụp lại") }
        Button(
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Dùng ảnh này")
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (showProgress) {
            Spacer(Modifier.height(Spacing.md))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes, then ktlint**

Run: `./gradlew :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.polegps.ui.measure.PoleGpsMeasureScreenTest`
Expected: PASS

Run: `./gradlew :app:ktlintCheck`, fix anything it flags (expect at least the unused-import cleanup noted above).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureScreen.kt app/src/androidTest/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureScreenTest.kt
git commit -m "feat(pole-gps): rebuild the measure screen as a camera capture flow"
```

---

### Task 6: Wire submit to enqueue photo uploads; update detail-screen wording

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModelTest.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt`

**Interfaces:**
- Consumes: `PoleGpsRepository.enqueuePendingPhotoUploads` (Task 3).

- [ ] **Step 1: Write the failing test**

Add to `WorkOrderDetailViewModelTest.kt` (near the other `pole_gps_update`-specific tests):

```kotlin
@Test
fun `submit for pole_gps_update enqueues pending photo uploads before completing`() =
    runTest {
        val poleGpsRepository = mockk<PoleGpsRepository>(relaxed = true)
        val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
        every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
        every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
        coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
        val vm =
            viewModel(
                detail = poleGpsDetail(),
                poleGpsRepository = poleGpsRepository,
                completionRepository = completionRepository,
            )
        vm.uiState.test { awaitItem(); awaitItem() }

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            poleGpsRepository.enqueuePendingPhotoUploads(WORK_ORDER_ID)
            completionRepository.submitCompletion(any(), any(), any(), any(), any())
        }
    }
```

Add the `coVerifyOrder` import (`io.mockk.coVerifyOrder`) if not already present.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*WorkOrderDetailViewModelTest*"`
Expected: FAIL — `enqueuePendingPhotoUploads` is never called by `submit()` yet.

- [ ] **Step 3: Implement**

In `WorkOrderDetailViewModel.kt`'s `submit()`, before the existing `completionRepository.submitCompletion(...)` call, add:

```kotlin
                if (current.detail.taskKind == "pole_gps_update") {
                    poleGpsRepository.enqueuePendingPhotoUploads(workOrderId)
                }
```

(Insert it as the first statement inside the existing `viewModelScope.launch { ... }` block of `submit()`, ahead of computing `faultOutcomes`/calling `submitCompletion`.)

In `WorkOrderDetailScreen.kt`, update the 3 "đo"-worded strings to "chụp" (same lines identified in this plan's research, read them again before editing to get the exact surrounding text right):
- `"Còn $missing cột chưa đo"` → `"Còn $missing cột chưa chụp"`
- `"Cột đã đo"` (in `submitSummaryRows`) → `"Cột đã chụp"`
- `PoleGpsRow`'s "Chưa đo" / accuracy badge → keep the accuracy badge when a measurement exists (it is still real, useful data), but change the "not yet done" label from `"Chưa đo"` to `"Chưa chụp"`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*WorkOrderDetailViewModelTest*"`
Expected: PASS

Run: `./gradlew :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.ui.detail.WorkOrderDetailScreenTest`
Expected: same pass/fail split as the project memory `project_workorder_detail_screen_test_preexisting_failures.md` already documents (5 pre-existing failures, unrelated) — if any test in THIS file asserts the literal strings changed above (e.g. `"Chưa đo"`), update that assertion to `"Chưa chụp"` first; re-run until the only failures are the 5 already-documented ones.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModelTest.kt app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt
git commit -m "feat(pole-gps): enqueue photo uploads on submit and update detail-screen wording"
```

---

### Task 7: Full suite, real-device check

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Full local unit suite**

Run: `./gradlew :app:testLocalDebugUnitTest`
Expected: BUILD SUCCESSFUL, no regressions outside the files this plan touched.

- [ ] **Step 2: ktlint**

Run: `./gradlew :app:ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Real-device manual check on `SM-A075F`**

Using a `pole_gps_update` order already assigned to `crew` (seed one via the Manager/`engineer` API flow already used earlier in this session if none exists): open the order, tap a pole, confirm the camera opens directly (CAMERA + location permission prompts appear on first use), take a photo, confirm the preview shows with "Chụp lại"/"Dùng ảnh này", confirm "Dùng ảnh này" returns to the detail screen with that pole now showing "Đã chụp"-style status, confirm "Chụp lại" discards and reopens the camera. Repeat for a 2nd pole, then submit the report and confirm (via `adb logcat` or a quick `GET /work-orders/{id}/evidence` call with the `engineer` token) that 2 evidence rows were created with the correct `pole_id` each, and that `pole_locations[]` on the order detail still carries both poles' coordinates.

- [ ] **Step 4: Clean up the test work order**

If a work order was created purely for this manual check (e.g. reusing or creating a fresh `pole_gps_update` order), leave it as-is in the local dev database (same as `WO-0047` from Task 11's check) — no cleanup endpoint exists and none is needed for a local dev backend.
