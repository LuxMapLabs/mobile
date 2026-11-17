# Pole GPS Update — Detail UI, PDF Viewer, Sync, Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the `pole_gps_update` work order feature started in `docs/superpowers/plans/2026-11-13-pole-gps-update.md` (Tasks 1-6, merged) by adding the parts the spec requires that are not yet built: the `complete` sync handler sending `pole_locations[]`, the F09 detail-screen branch (PDF list + pole list + report note + submit), a standalone PDF viewer screen, and the navigation wiring that connects them.

**Architecture:** Small, additive changes to the existing `WorkOrderDetailViewModel`/`WorkOrderDetailScreen`/`CompleteWorkOrderSyncHandler` (all already handle `repair`/`inspection` the same way) plus one new self-contained feature package `feature/workorder/polegps/ui/pdf` for the PDF viewer, built on `android.graphics.pdf.PdfRenderer` (platform API, no dependency). No new Room tables — everything reads from the `PoleGpsRepository`/`PoleGpsDao` Tasks 3/6 already built.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt (`@HiltViewModel`), Room (read-only here), Retrofit + kotlinx.serialization, `android.graphics.pdf.PdfRenderer`, JUnit4 + MockK + Turbine + Compose UI Test. No new library.

**Spec:** `docs/superpowers/specs/2026-11-13-pole-gps-update-design.md` (BE contract: `luxmap_backend/docs/contract-drift.md` WO-POLE-GPS PG-1..PG-13; confirmed directly against `luxmap_backend/tests/LuxMap.Api.Tests/WorkOrderPoleGpsTests.cs` and `WorkOrderService.cs`/`WorkOrderRequests.cs` on 2026-11-14 — `report_note` is unconditionally `[Required]` for every `task_kind` including this one, so the existing report-note rule needs no special case; `pole_locations` is a raw `JsonElement` field validated by `OptionalJson.Present()`, which treats an explicit JSON `null` the same as a present-but-invalid value — the request DTO must OMIT the field for other kinds, not send it as `null`, which the already-merged `kotlinx.serialization` default (`encodeDefaults = false`, confirmed from the library's own `JsonConfiguration.kt` source) already guarantees).

## Global Constraints

- No new library (CLAUDE.md "Ngăn xếp công nghệ"). PDF uses `android.graphics.pdf.PdfRenderer`; pinch-zoom uses `Modifier.pointerInput` + `detectTransformGestures` + `graphicsLayer` (all `androidx.compose.foundation`/`androidx.compose.ui`, already a dependency).
- ZERO comments in any code written for this plan (user rule, 2026-10-05).
- Each commit at most 400 changed lines; Conventional Commits in English: `type(scope): description`, scope `pole-gps`.
- Branch `feat/pole-gps-update` (same branch as the first plan), off `dev`. Never commit to `main`. No push or PR without the user's go-ahead.
- Design System v2 tokens only; no new colour token; no dynamic colour. Touch targets at least 48dp (`Dimens.minTouchTarget`).
- Every async screen has all four UiState branches (Loading / Success / Empty / Error), exhaustive `when` with no `else`.
- Offline-first: the detail screen and PDF viewer read Room-backed repositories only; the submit button enqueues through the existing `sync_queue`, never calls the API directly.
- `report_note` is required for every kind (confirmed above) — `canSubmit()`'s existing `noteValid` check applies unchanged to `pole_gps_update`.
- `pole_locations` is required (>= 1) for `pole_gps_update` and must be entirely absent from the JSON body for every other kind (confirmed above) — never send it as an explicit `null`.
- Partial reporting is allowed (PG-10): the submit button must stay enabled with as few as 1 measured pole; unmeasured poles only get a non-blocking "Còn N cột chưa đo" line.
- `./gradlew ktlintCheck` after each task. Ask the user before running the full Gradle test suite; targeted `--tests` runs and compile are fine. Use `:app:testLocalDebugUnitTest`/`:app:compileLocalDebugKotlin`/`:app:compileLocalDebugAndroidTestKotlin`/`:app:connectedLocalDebugAndroidTest`, not the bare `Debug` task names, which are ambiguous between the `local`/`server` product flavors in this project (ruling already recorded in the first plan's ledger).
- Chat prose to the user in Vietnamese; code and commits in English.

## Review Focus

1. A `pole_gps_update` order's `complete` request never includes `pole_locations` as an explicit `null`, and a `repair`/`inspection` order's `complete` request never includes the field at all (Task 7 handler test).
2. Submitting with only 1 of several poles measured still succeeds — partial reporting is allowed, not an error state (Task 8 `canSubmit` test, Task 10 UI test).
3. A PDF attachment that is `pending`/`downloading`/`failed` never crashes the viewer screen if the route is reached anyway (stale navigation, user double-tap before the row disables) — it renders the Error state, not a blank screen (Task 9 ViewModel test).
4. Returning to the detail screen after measuring a pole on `PoleGpsMeasureRoute` shows the new measurement without leaving and re-entering the screen (Task 8 ViewModel test: the measurements source is a live `Flow`, not a one-shot read).
5. A pole with no cached GIS position (`position`/`lat`/`lng` all `null`) renders "Chưa có" in the old-position column, not a crash or a "0.000000" coordinate (Task 10 UI test).

---

### Task 7: Sync handler sends `pole_locations` for `pole_gps_update` orders

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt` (add `completionPoleLocations`, add `poleReferences`, refactor `poleReference` to use it)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandler.kt` (inject `PoleGpsRepository`, pass `poleLocations`)
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepositoryTest.kt` (add cases)
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandlerTest.kt` (add cases, fix existing constructions)

**Interfaces:**
- Consumes: `PoleGpsDetailSync.cached`, `PoleListCacheDao.byWorkOrder`, `PoleGpsRepository.allMeasurements` (all from the first plan); `CompleteWorkOrderRequestDto.poleLocations`, `PoleLocationRequestDto` (first plan Task 2).
- Produces: `PoleGpsRepository.poleReferences(workOrderId): List<PoleReference>`; `PoleGpsRepository.completionPoleLocations(workOrderId): List<PoleLocationRequestDto>?` (`null` for a non-`pole_gps_update` order, otherwise every local measurement with `capturedAt` clamped to `Instant.now()` if it is in the future).

- [ ] **Step 1: Write the failing tests**

Add to `PoleGpsRepositoryTest.kt` (the class already has `dao`, `downloader`, `scheduler`, `sync`, `cache`, `repository` fields from the first plan — reuse them):

```kotlin
    @Test
    fun `completionPoleLocations is null for an order the detail sync has never cached`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns null
            assertNull(repository.completionPoleLocations("WO-1"))
        }

    @Test
    fun `completionPoleLocations sends every local measurement, clamping a future timestamp to now`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "in_progress",
                    poleIds = listOf("P-1", "P-2"),
                )
            val future = Instant.now().plusSeconds(600)
            coEvery { dao.measurements("WO-1") } returns
                listOf(
                    LocalPoleGpsMeasurementEntity("WO-1", "P-1", 10.0, 106.0, 3.0, Instant.ofEpochSecond(5), MeasurementSourceColumn.DEVICE),
                    LocalPoleGpsMeasurementEntity("WO-1", "P-2", 11.0, 107.0, 4.0, future, MeasurementSourceColumn.SERVER),
                )
            val result = repository.completionPoleLocations("WO-1")!!
            assertEquals(listOf("P-1", "P-2"), result.map { it.poleId })
            assertEquals(Instant.ofEpochSecond(5).toString(), result[0].capturedAt)
            assertTrue(Instant.parse(result[1].capturedAt).isBefore(future))
        }

    @Test
    fun `poleReferences lists every pole_id of the order, with null coordinates for an unlisted pole`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                    poleIds = listOf("P-1", "P-2"),
                )
            coEvery { cache.byWorkOrder("WO-1") } returns
                listOf(LocalPoleListItemEntity("WO-1", "P-1", 4, 10.0, 106.0, "unknown"))
            val refs = repository.poleReferences("WO-1")
            assertEquals(listOf("P-1", "P-2"), refs.map { it.poleId })
            assertEquals(4, refs[0].position)
            assertNull(refs[1].position)
            assertNull(refs[1].lat)
        }

    @Test
    fun `poleReferences is empty for an order the detail sync has never cached`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns null
            assertTrue(repository.poleReferences("WO-1").isEmpty())
        }
```

Add `import org.junit.Assert.assertTrue` to `PoleGpsRepositoryTest.kt` if absent (it already imports `assertEquals`/`assertNull`).

Add to `CompleteWorkOrderSyncHandlerTest.kt`:

```kotlin
    @Test
    fun `a pole_gps_update order sends every local measurement as pole_locations`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val poleGpsRepository = mockk<PoleGpsRepository>()
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            val locations = listOf(PoleLocationRequestDto("P-1", 10.0, 106.0, 3.0, "2026-11-13T02:00:00Z"))
            coEvery { poleGpsRepository.completionPoleLocations("WO-1") } returns locations
            val body = slot<CompleteWorkOrderRequestDto>()
            coEvery { api.complete("WO-1", capture(body)) } returns detailDto()
            val handler = CompleteWorkOrderSyncHandler(api, dao, poleGpsRepository)

            handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertEquals(locations, body.captured.poleLocations)
        }

    @Test
    fun `a repair order's complete request has no pole_locations field at all`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val poleGpsRepository = mockk<PoleGpsRepository>()
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { poleGpsRepository.completionPoleLocations("WO-1") } returns null
            val body = slot<CompleteWorkOrderRequestDto>()
            coEvery { api.complete("WO-1", capture(body)) } returns detailDto()
            val handler = CompleteWorkOrderSyncHandler(api, dao, poleGpsRepository)

            handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertNull(body.captured.poleLocations)
            assertTrue(!Json { encodeDefaults = false }.encodeToString(body.captured).contains("pole_locations"))
        }
```

Add imports to `CompleteWorkOrderSyncHandlerTest.kt`: `com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto`, `com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto`, `com.luxmap.feature.workorder.polegps.data.PoleGpsRepository`, `io.mockk.slot`, `org.junit.Assert.assertEquals`, `org.junit.Assert.assertNull`, `kotlinx.serialization.encodeToString`. Fix the 4 existing tests: add `val poleGpsRepository = mockk<PoleGpsRepository>(relaxed = true)` and pass it as the 3rd constructor argument at each of their `CompleteWorkOrderSyncHandler(api, dao)` call sites.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*PoleGpsRepositoryTest" --tests "*CompleteWorkOrderSyncHandlerTest"`
Expected: compilation FAIL — unresolved `completionPoleLocations`, `poleReferences`, and a "too many arguments"/"not enough arguments" mismatch on every `CompleteWorkOrderSyncHandler(...)` construction.

- [ ] **Step 3: Implement**

In `PoleGpsRepository.kt`, add the import `com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto` and these members inside the class, after `poleReference`:

```kotlin
        suspend fun poleReferences(workOrderId: String): List<PoleReference> {
            val order = detailSync.cached(workOrderId) ?: return emptyList()
            val rows = poleCache.byWorkOrder(workOrderId).associateBy { it.poleId }
            return order.poleIds.map { poleId ->
                val row = rows[poleId]
                PoleReference(poleId, row?.position, row?.lat, row?.lng)
            }
        }

        suspend fun completionPoleLocations(workOrderId: String): List<PoleLocationRequestDto>? {
            if (detailSync.cached(workOrderId) == null) return null
            val now = Instant.now()
            return allMeasurements(workOrderId).map {
                val capturedAt = if (it.capturedAt.isAfter(now)) now else it.capturedAt
                PoleLocationRequestDto(it.poleId, it.lat, it.lng, it.accuracyM, capturedAt.toString())
            }
        }
```

Replace the body of the existing `poleReference` with a call to the new method, keeping its signature identical:

```kotlin
        suspend fun poleReference(
            workOrderId: String,
            poleId: String,
        ): PoleReference? = poleReferences(workOrderId).firstOrNull { it.poleId == poleId }
```

In `CompleteWorkOrderSyncHandler.kt`, add the import `com.luxmap.feature.workorder.polegps.data.PoleGpsRepository`, add `private val poleGpsRepository: PoleGpsRepository` as the 3rd constructor parameter, and change the `api.complete(...)` call:

```kotlin
                api.complete(
                    id = payload.workOrderId,
                    body =
                        CompleteWorkOrderRequestDto(
                            reportNote = completion.reportNote,
                            materialsUsed = completion.materialsUsed,
                            faultOutcomes = outcomes?.map { FaultOutcomeRequestDto(it.faultId, it.outcome) },
                            poleLocations = poleGpsRepository.completionPoleLocations(payload.workOrderId),
                        ),
                )
```

No `SyncModule.kt` change is needed: `PoleGpsRepository` already has an `@Inject` constructor (first plan Task 6), so Hilt resolves the new parameter automatically.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*PoleGpsRepositoryTest" --tests "*CompleteWorkOrderSyncHandlerTest"` then `./gradlew :app:compileLocalDebugKotlin ktlintCheck`
Expected: PASS, no ktlint errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt app/src/main/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandler.kt app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepositoryTest.kt app/src/test/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandlerTest.kt
git commit -m "feat(pole-gps): send pole_locations on complete for this task kind"
```

---

### Task 8: Detail ViewModel/UiState plumbing (pole references, measurements, `canSubmit`)

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailUiState.kt` (new `Success` fields, `canSubmit` branch)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt` (inject `PoleGpsRepository`, extend `load()`)
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModelTest.kt` (add cases, fix existing `viewModel(...)` helpers)

**Interfaces:**
- Consumes: `PoleGpsRepository.observeMeasurements`, `PoleGpsRepository.observeAttachments`, `PoleGpsRepository.poleReferences` (Task 7); `PoleMeasurement`, `PoleGpsAttachment`, `PoleReference` (first plan Task 6).
- Produces: `WorkOrderDetailUiState.Success.poleReferences: List<PoleReference>`, `.poleMeasurements: List<PoleMeasurement>`, `.poleAttachments: List<PoleGpsAttachment>`; `canSubmit()` returns `true` for `pole_gps_update` once the note is valid and `poleMeasurements` is non-empty; `WorkOrderDetailViewModel.retryAttachment(attachmentId: String)` (Task 10 wires this to the PDF row's retry button).

- [ ] **Step 1: Write the failing tests**

Add to `WorkOrderDetailUiState.kt`'s own home — no test file for it alone; its behavior is exercised through `WorkOrderDetailViewModelTest.kt`. Add this helper next to `repairDetail`/`inspectionDetail` in `WorkOrderDetailViewModelTest.kt`:

```kotlin
private fun poleGpsDetail(poleIds: List<String> = listOf("P-1", "P-2")) =
    repairDetail(allowedActions = listOf("complete")).copy(taskKind = "pole_gps_update", poleIds = poleIds)
```

Change both `viewModel(...)` helper overloads to accept and wire a `PoleGpsRepository`:

```kotlin
    private fun viewModel(
        detailFlow: Flow<WorkOrderDetail?>,
        detailRepository: WorkOrderDetailRepository = mockk(),
        completionRepository: WorkOrderCompletionRepository = mockk(),
        poleGpsRepository: PoleGpsRepository = mockk(),
        isOnline: Boolean = true,
        evidence: LocalWorkOrderEvidenceEntity? = null,
        completion: LocalWorkOrderCompletionEntity? = null,
    ): WorkOrderDetailViewModel {
        every { detailRepository.observeWorkOrderDetail(WORK_ORDER_ID) } returns detailFlow
        every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(evidence)
        every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(completion)
        every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
        every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
        coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
        val connectivityObserver = mockk<ConnectivityObserver>()
        every { connectivityObserver.isOnline } returns flowOf(isOnline)
        return WorkOrderDetailViewModel(
            savedStateHandle(),
            detailRepository,
            completionRepository,
            poleGpsRepository,
            connectivityObserver,
        )
    }

    private fun viewModel(
        detail: WorkOrderDetail?,
        detailRepository: WorkOrderDetailRepository = mockk(),
        completionRepository: WorkOrderCompletionRepository = mockk(),
        poleGpsRepository: PoleGpsRepository = mockk(),
        isOnline: Boolean = true,
        evidence: LocalWorkOrderEvidenceEntity? = null,
        completion: LocalWorkOrderCompletionEntity? = null,
    ) = viewModel(flowOf(detail), detailRepository, completionRepository, poleGpsRepository, isOnline, evidence, completion)
```

Add `import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository` and `import com.luxmap.feature.workorder.polegps.data.PoleMeasurement` and `import com.luxmap.feature.workorder.polegps.data.MeasurementSource` and `import com.luxmap.feature.workorder.polegps.data.PoleReference` and `import io.mockk.coEvery` (if absent).

Add these test cases:

```kotlin
    @Test
    fun `a pole_gps_update order loads its pole references once and its measurements reactively`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>()
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns
                flowOf(listOf(PoleMeasurement("P-1", 10.0, 106.0, 3.0, Instant.ofEpochSecond(1), MeasurementSource.DEVICE)))
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns listOf(PoleReference("P-1", 4, 10.0, 106.0))
            val vm = viewModel(detail = poleGpsDetail(poleIds = listOf("P-1")), poleGpsRepository = poleGpsRepository)

            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val success = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals(listOf("P-1"), success.poleReferences.map { it.poleId })
                assertEquals(1, success.poleMeasurements.size)
            }
        }

    @Test
    fun `canSubmit for pole_gps_update needs a valid note and at least 1 measured pole`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>()
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
            val vm = viewModel(detail = poleGpsDetail(), poleGpsRepository = poleGpsRepository)

            vm.uiState.test {
                awaitItem()
                val empty = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(empty.canSubmit())
            }
        }

    @Test
    fun `canSubmit for pole_gps_update is true with partial reporting, a single measured pole is enough`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>()
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns
                flowOf(listOf(PoleMeasurement("P-1", 10.0, 106.0, 3.0, Instant.ofEpochSecond(1), MeasurementSource.DEVICE)))
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
            val vm = viewModel(detail = poleGpsDetail(poleIds = listOf("P-1", "P-2")), poleGpsRepository = poleGpsRepository)

            vm.uiState.test {
                awaitItem()
                val success = awaitItem() as WorkOrderDetailUiState.Success
                val valid = success.copy(reportNote = "Đã đo xong 1/2 cột đèn")
                assertTrue(valid.canSubmit())
            }
        }

    @Test
    fun `retryAttachment delegates to the pole GPS repository with this work order and attachment id`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>(relaxed = true)
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            coEvery { poleGpsRepository.poleReferences(WORK_ORDER_ID) } returns emptyList()
            val vm = viewModel(detail = poleGpsDetail(), poleGpsRepository = poleGpsRepository)

            vm.retryAttachment("A-1")

            coVerify { poleGpsRepository.retryAttachment(WORK_ORDER_ID, "A-1") }
        }
```

Add `import io.mockk.coVerify` if absent.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*WorkOrderDetailViewModelTest"`
Expected: compilation FAIL — unresolved `poleReferences`/`poleMeasurements` properties, unresolved `retryAttachment` method, unresolved `PoleGpsRepository` constructor parameter, "not enough arguments" on `WorkOrderDetailViewModel(...)`.

- [ ] **Step 3: Implement**

In `WorkOrderDetailUiState.kt`, add the imports `com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment`, `com.luxmap.feature.workorder.polegps.data.PoleMeasurement`, `com.luxmap.feature.workorder.polegps.data.PoleReference`. Add 3 fields to `Success` after `justSubmitted`:

```kotlin
        val poleReferences: List<PoleReference> = emptyList(),
        val poleMeasurements: List<PoleMeasurement> = emptyList(),
        val poleAttachments: List<PoleGpsAttachment> = emptyList(),
```

Add a branch to `canSubmit()`:

```kotlin
        "pole_gps_update" -> noteValid && poleMeasurements.isNotEmpty()
```

In `WorkOrderDetailViewModel.kt`, add the import `com.luxmap.feature.workorder.polegps.data.PoleGpsRepository`, add `private val poleGpsRepository: PoleGpsRepository` as the 4th constructor parameter (after `completionRepository`, before `connectivityObserver` — matches the test helper's argument order above), add a `private val poleReferencesState = MutableStateFlow<List<PoleReference>>(emptyList())` field, and replace `load()`'s body:

```kotlin
        private fun load() {
            viewModelScope.launch {
                launch {
                    val detail =
                        try {
                            detailRepository.observeWorkOrderDetail(workOrderId).first()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _uiState.value = WorkOrderDetailUiState.Error(e.message ?: "Không tải được dữ liệu lệnh")
                            return@launch
                        }
                    if (detail == null) {
                        _uiState.value = WorkOrderDetailUiState.Empty
                    } else {
                        detailState.value = detail
                        if (detail.taskKind == "pole_gps_update") {
                            poleReferencesState.value = poleGpsRepository.poleReferences(workOrderId)
                        }
                    }
                }
                combine(
                    detailState,
                    completionRepository.observeEvidence(workOrderId),
                    completionRepository.observeCompletion(workOrderId),
                    connectivityObserver.isOnline,
                ) { latestDetail, evidence, completion, isOnline -> Quad(latestDetail, evidence, completion, isOnline) }
                    .combine(poleGpsRepository.observeMeasurements(workOrderId)) { quad, measurements -> quad to measurements }
                    .combine(poleGpsRepository.observeAttachments(workOrderId)) { (quad, measurements), attachments ->
                        Triple(quad, measurements, attachments)
                    }
                    .combine(poleReferencesState) { (quad, measurements, attachments), references ->
                        Quint(quad.first, quad.second, quad.third, quad.fourth, PoleGpsData(measurements, attachments, references))
                    }
                    .collect { (latestDetail, evidence, completion, isOnline, poleGpsData) ->
                        if (latestDetail != null) {
                            val current = _uiState.value as? WorkOrderDetailUiState.Success
                            _uiState.value =
                                current?.copy(
                                    detail = latestDetail,
                                    localEvidence = evidence,
                                    localCompletion = completion,
                                    isOnline = isOnline,
                                    poleMeasurements = poleGpsData.measurements,
                                    poleAttachments = poleGpsData.attachments,
                                    poleReferences = poleGpsData.references,
                                ) ?: WorkOrderDetailUiState.Success(
                                    detail = latestDetail,
                                    localEvidence = evidence,
                                    localCompletion = completion,
                                    isOnline = isOnline,
                                    poleMeasurements = poleGpsData.measurements,
                                    poleAttachments = poleGpsData.attachments,
                                    poleReferences = poleGpsData.references,
                                )
                        }
                    }
            }
        }

        fun retryAttachment(attachmentId: String) {
            viewModelScope.launch { poleGpsRepository.retryAttachment(workOrderId, attachmentId) }
        }
```

Add the two small supporting types next to `Quad` at the bottom of the file:

```kotlin
        private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

        private data class PoleGpsData(
            val measurements: List<com.luxmap.feature.workorder.polegps.data.PoleMeasurement>,
            val attachments: List<com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment>,
            val references: List<com.luxmap.feature.workorder.polegps.data.PoleReference>,
        )
```

Add `import kotlinx.coroutines.flow.MutableStateFlow` is already present; no further import needed since `PoleGpsData` uses fully-qualified names to avoid clashing with the file's existing short names.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*WorkOrderDetailViewModelTest"` then `./gradlew :app:compileLocalDebugKotlin ktlintCheck`
Expected: PASS, no ktlint errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailUiState.kt app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModelTest.kt
git commit -m "feat(pole-gps): load pole references, measurements and attachments into F09"
```

---

### Task 9: Standalone PDF viewer screen

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/pdf/PdfPageRenderer.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/pdf/PoleGpsPdfViewerUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/pdf/PoleGpsPdfViewerViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/pdf/PoleGpsPdfViewerScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/ui/pdf/PoleGpsPdfViewerViewModelTest.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/ui/pdf/PoleGpsPdfViewerScreenTest.kt`

**Interfaces:**
- Consumes: `PoleGpsRepository.observeAttachments`, `.retryAttachment`, `PoleGpsAttachment`, `AttachmentStatus` (Task 7/first plan Task 6).
- Produces: `sealed interface PoleGpsPdfViewerUiState { Loading, data class Ready(fileName: String, filePath: String), Empty, data class Error(message: String, canRetry: Boolean) }`; `PoleGpsPdfViewerViewModel` with `state: StateFlow<PoleGpsPdfViewerUiState>`, `onRetry()`; `PoleGpsPdfViewerRoute`, `PoleGpsPdfViewerContent(state, onBack, onRetry)`.

- [ ] **Step 1: Write the failing tests**

`PoleGpsPdfViewerViewModelTest.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.lifecycle.SavedStateHandle
import com.luxmap.feature.workorder.polegps.data.AttachmentStatus
import com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WORK_ORDER_ID = "WO-1"
private const val ATTACHMENT_ID = "A-1"

private fun attachment(
    status: AttachmentStatus,
    localPath: String? = null,
) = PoleGpsAttachment(ATTACHMENT_ID, "qr.pdf", 2048L, status, localPath)

private fun viewModel(repository: PoleGpsRepository) =
    PoleGpsPdfViewerViewModel(
        SavedStateHandle(mapOf("workOrderId" to WORK_ORDER_ID, "attachmentId" to ATTACHMENT_ID)),
        repository,
    )

class PoleGpsPdfViewerViewModelTest {
    @Test
    fun `a ready attachment is Ready with its file name and local path`() {
        val repository = mockk<PoleGpsRepository>()
        every { repository.observeAttachments(WORK_ORDER_ID) } returns
            flowOf(listOf(attachment(AttachmentStatus.READY, "/files/qr.pdf")))
        val vm = viewModel(repository)

        val state = vm.state.value as PoleGpsPdfViewerUiState.Ready
        assertEquals("qr.pdf", state.fileName)
        assertEquals("/files/qr.pdf", state.filePath)
    }

    @Test
    fun `an attachment this id does not match is Empty`() {
        val repository = mockk<PoleGpsRepository>()
        every { repository.observeAttachments(WORK_ORDER_ID) } returns
            flowOf(listOf(attachment(AttachmentStatus.READY, "/files/other.pdf").copy(attachmentId = "A-OTHER")))
        val vm = viewModel(repository)

        assertEquals(PoleGpsPdfViewerUiState.Empty, vm.state.value)
    }

    @Test
    fun `a failed download is Error with retry allowed`() {
        val repository = mockk<PoleGpsRepository>()
        every { repository.observeAttachments(WORK_ORDER_ID) } returns flowOf(listOf(attachment(AttachmentStatus.FAILED)))
        val vm = viewModel(repository)

        val state = vm.state.value as PoleGpsPdfViewerUiState.Error
        assertTrue(state.canRetry)
    }

    @Test
    fun `a still-downloading attachment is Error without retry, it is already in progress`() {
        val repository = mockk<PoleGpsRepository>()
        every { repository.observeAttachments(WORK_ORDER_ID) } returns flowOf(listOf(attachment(AttachmentStatus.DOWNLOADING)))
        val vm = viewModel(repository)

        val state = vm.state.value as PoleGpsPdfViewerUiState.Error
        assertEquals(false, state.canRetry)
    }

    @Test
    fun `onRetry calls the repository with this work order and attachment id`() =
        runTest {
            val repository = mockk<PoleGpsRepository>(relaxed = true)
            every { repository.observeAttachments(WORK_ORDER_ID) } returns flowOf(listOf(attachment(AttachmentStatus.FAILED)))
            val vm = viewModel(repository)

            vm.onRetry()

            coVerify { repository.retryAttachment(WORK_ORDER_ID, ATTACHMENT_ID) }
        }
}
```

`PoleGpsPdfViewerScreenTest.kt` (androidTest; `Ready` is exercised manually on a real device, not here — opening a real PDF needs `android.graphics.pdf.PdfRenderer` against a real file, which has no JVM or synthetic-fixture seam, same class of limitation as `FusedGpsFixSource` in the first plan's Task 5):

```kotlin
package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PoleGpsPdfViewerScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: PoleGpsPdfViewerUiState, onRetry: () -> Unit = {}) =
        rule.setContent {
            LuxMapTheme {
                PoleGpsPdfViewerContent(state, onBack = {}, onRetry = onRetry)
            }
        }

    @Test
    fun loading_state_is_announced() {
        show(PoleGpsPdfViewerUiState.Loading)
        rule.onNodeWithText("Đang mở file PDF", substring = true).assertExists()
    }

    @Test
    fun empty_state_says_the_file_was_not_found() {
        show(PoleGpsPdfViewerUiState.Empty)
        rule.onNodeWithText("Không tìm thấy file này", substring = true).assertExists()
    }

    @Test
    fun error_with_retry_allowed_shows_an_enabled_retry_button() {
        var retried = false
        show(PoleGpsPdfViewerUiState.Error("Tải file lỗi, thử lại", canRetry = true), onRetry = { retried = true })
        rule.onNodeWithText("Thử lại").assertIsEnabled().performClick()
        assertTrue(retried)
    }

    @Test
    fun error_without_retry_shows_no_retry_button() {
        show(PoleGpsPdfViewerUiState.Error("Đang tải, hãy thử lại sau", canRetry = false))
        rule.onNodeWithText("Thử lại").assertDoesNotExist()
    }
}
```

Add `import androidx.compose.ui.test.assertDoesNotExist` if ktlint flags it missing after the import-sort pass.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*PoleGpsPdfViewerViewModelTest"`
Expected: compilation FAIL — unresolved `PoleGpsPdfViewerViewModel`, `PoleGpsPdfViewerUiState`.

- [ ] **Step 3: Implement**

`PoleGpsPdfViewerUiState.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.pdf

sealed interface PoleGpsPdfViewerUiState {
    data object Loading : PoleGpsPdfViewerUiState

    data class Ready(
        val fileName: String,
        val filePath: String,
    ) : PoleGpsPdfViewerUiState

    data object Empty : PoleGpsPdfViewerUiState

    data class Error(
        val message: String,
        val canRetry: Boolean,
    ) : PoleGpsPdfViewerUiState
}
```

`PoleGpsPdfViewerViewModel.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.workorder.polegps.data.AttachmentStatus
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoleGpsPdfViewerViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PoleGpsRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle["workOrderId"])
        private val attachmentId: String = checkNotNull(savedStateHandle["attachmentId"])

        val state: StateFlow<PoleGpsPdfViewerUiState> =
            repository
                .observeAttachments(workOrderId)
                .map { items -> toUiState(items.firstOrNull { it.attachmentId == attachmentId }) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PoleGpsPdfViewerUiState.Loading)

        fun onRetry() {
            viewModelScope.launch { repository.retryAttachment(workOrderId, attachmentId) }
        }

        private fun toUiState(attachment: com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment?): PoleGpsPdfViewerUiState =
            when {
                attachment == null -> PoleGpsPdfViewerUiState.Empty
                attachment.status == AttachmentStatus.READY && attachment.localPath != null ->
                    PoleGpsPdfViewerUiState.Ready(attachment.fileName, attachment.localPath)
                attachment.status == AttachmentStatus.FAILED ->
                    PoleGpsPdfViewerUiState.Error("Tải file lỗi, thử lại", canRetry = true)
                else -> PoleGpsPdfViewerUiState.Error("Đang tải, hãy thử lại sau", canRetry = false)
            }
    }
```

`PdfPageRenderer.kt` (compile-only verified here; exercised manually on a real device — `PdfRenderer` needs a real file and the device's native pdfium library, no JVM or Compose-test seam exists for it):

```kotlin
package com.luxmap.feature.workorder.polegps.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

private const val RENDER_SCALE = 2

class PdfPageRenderer(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int get() = renderer.pageCount

    fun renderPage(index: Int): Bitmap {
        val page = renderer.openPage(index)
        return try {
            val bitmap =
                Bitmap.createBitmap(page.width * RENDER_SCALE, page.height * RENDER_SCALE, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }
}
```

`PoleGpsPdfViewerScreen.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PoleGpsPdfViewerRoute(
    onBack: () -> Unit,
    viewModel: PoleGpsPdfViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    PoleGpsPdfViewerContent(state = state, onBack = onBack, onRetry = viewModel::onRetry)
}

@Composable
fun PoleGpsPdfViewerContent(
    state: PoleGpsPdfViewerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Xem file PDF", onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                PoleGpsPdfViewerUiState.Loading -> CenteredPdfMessage("Đang mở file PDF...", showProgress = true)
                PoleGpsPdfViewerUiState.Empty -> CenteredPdfMessage("Không tìm thấy file này.")
                is PoleGpsPdfViewerUiState.Error ->
                    CenteredPdfMessage(state.message, actionLabel = if (state.canRetry) "Thử lại" else null, onAction = onRetry)
                is PoleGpsPdfViewerUiState.Ready -> PdfPages(state.filePath)
            }
        }
    }
}

@Composable
private fun CenteredPdfMessage(
    text: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (showProgress) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        if (actionLabel != null) {
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PdfPages(filePath: String) {
    val renderer = remember(filePath) { PdfPageRenderer(File(filePath)) }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    val pagerState = rememberPagerState(pageCount = { renderer.pageCount })
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            PdfPage(renderer = renderer, pageIndex = page)
        }
        Text(
            text = "Trang ${pagerState.currentPage + 1}/${renderer.pageCount}",
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PdfPage(
    renderer: PdfPageRenderer,
    pageIndex: Int,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pageIndex) {
        bitmap = withContext(Dispatchers.IO) { renderer.renderPage(pageIndex) }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(pageIndex) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset += pan
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Trang ${pageIndex + 1}",
                modifier =
                    Modifier.graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }
    }
}
```

`AppTopBar(title: String, onBack: (() -> Unit)?, ...)` is the existing shared component (`core/ui/components/AppTopBar.kt`) — the call above already matches its real signature. Page navigation is swipe-only through `HorizontalPager`, no manual prev/next buttons, which is why `Icon`/`IconButton`/`ArrowBack`/`ArrowForward` are not imported here even though they appear elsewhere in this plan's other screens.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "*PoleGpsPdfViewerViewModelTest"` then `./gradlew :app:compileLocalDebugKotlin :app:compileLocalDebugAndroidTestKotlin ktlintCheck`
Expected: 5 ViewModel tests PASS; compile OK. Then, with the device `SM-A075F` attached: `./gradlew :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.polegps.ui.pdf.PoleGpsPdfViewerScreenTest`
Expected: 4 tests PASS on the device (the `Ready`/real-PDF path is a manual check, not a test in this plan).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/polegps/ui/pdf app/src/test/java/com/luxmap/feature/workorder/polegps/ui/pdf app/src/androidTest/java/com/luxmap/feature/workorder/polegps/ui/pdf
git commit -m "feat(pole-gps): add the standalone PDF viewer screen"
```

---

### Task 10: F09 detail branch — PDF list, pole list, report note, submit

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt` (new branch, CTA, submit summary, callbacks threaded through 3 layers)
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt` (add cases, fix `setContent` helper)

**Interfaces:**
- Consumes: `WorkOrderDetailUiState.Success.poleReferences/poleMeasurements/poleAttachments` (Task 8); `PoleGpsAttachment`, `AttachmentStatus`, `PoleReference`, `PoleMeasurement` (first plan Task 6).
- Produces: `WorkOrderDetailScreen`/`WorkOrderDetailContent` gain 3 new callback parameters (`onOpenPdf`, `onMeasurePole`, `onRetryAttachment`); `WorkOrderDetailRoute` gains only `onOpenPdf: (attachmentId: String) -> Unit` and `onMeasurePole: (poleId: String) -> Unit` (it resolves `onRetryAttachment` itself from `WorkOrderDetailViewModel.retryAttachment`, Task 8).

- [ ] **Step 1: Write the failing tests**

Change `WorkOrderDetailScreenTest.kt`'s `setContent` helper to accept the 3 new callbacks and forward them (`onOpenPdf: (String) -> Unit = {}`, `onMeasurePole: (String) -> Unit = {}`, `onRetryAttachment: (String) -> Unit = {}`, added as parameters and passed into the `WorkOrderDetailScreen(...)` call). Add this helper next to `repairDetail`/`inspectionDetail`:

```kotlin
private fun poleGpsDetail(
    allowedActions: List<String> = listOf("complete"),
    poleIds: List<String> = listOf("P-1", "P-2"),
) = repairDetail(allowedActions = allowedActions).copy(taskKind = "pole_gps_update", faults = emptyList(), poleIds = poleIds)
```

Add these test cases:

```kotlin
    @Test
    fun pole_with_no_cached_position_shows_chua_co() {
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1")),
                    poleReferences = listOf(PoleReference("P-1", null, null, null)),
                ),
        )
        composeRule.onNodeWithText("Chưa có", substring = true).assertExists()
    }

    @Test
    fun tapping_a_pdf_row_that_is_ready_opens_the_viewer() {
        var openedId: String? = null
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(),
                    poleAttachments = listOf(PoleGpsAttachment("A-1", "qr.pdf", 10L, AttachmentStatus.READY, "/f/qr.pdf")),
                ),
            onOpenPdf = { openedId = it },
        )
        composeRule.onNodeWithText("qr.pdf").performClick()
        assertEquals("A-1", openedId)
    }

    @Test
    fun tapping_a_pole_row_opens_the_measure_screen_for_that_pole() {
        var measuredId: String? = null
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1")),
                    poleReferences = listOf(PoleReference("P-1", 4, 10.0, 106.0)),
                ),
            onMeasurePole = { measuredId = it },
        )
        composeRule.onNodeWithText("P-1").performClick()
        assertEquals("P-1", measuredId)
    }

    @Test
    fun submit_button_is_enabled_with_only_1_of_2_poles_measured() {
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1", "P-2")),
                    reportNote = "Đã đo 1 trong 2 cột đèn hôm nay",
                    poleReferences = listOf(PoleReference("P-1", 1, 10.0, 106.0), PoleReference("P-2", 2, 11.0, 107.0)),
                    poleMeasurements =
                        listOf(
                            com.luxmap.feature.workorder.polegps.data.PoleMeasurement(
                                "P-1",
                                10.0,
                                106.0,
                                3.0,
                                java.time.Instant.ofEpochSecond(1),
                                com.luxmap.feature.workorder.polegps.data.MeasurementSource.DEVICE,
                            ),
                        ),
                ),
        )
        composeRule.onNodeWithText("Nộp báo cáo").assertIsEnabled()
    }
```

Add imports: `com.luxmap.feature.workorder.polegps.data.AttachmentStatus`, `com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment`, `com.luxmap.feature.workorder.polegps.data.PoleReference`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:compileLocalDebugAndroidTestKotlin`
Expected: compilation FAIL — the new `setContent` parameters don't exist yet on `WorkOrderDetailScreen`, and `PoleGpsAttachment`/`PoleReference` aren't threaded into any Composable yet.

- [ ] **Step 3: Implement**

Add `onOpenPdf: (attachmentId: String) -> Unit` and `onMeasurePole: (poleId: String) -> Unit` as new parameters of `WorkOrderDetailRoute` (after `onStartSurvey`) — these need real navigation, which only the `NavGraph` call site can provide, same reasoning as the existing `onCaptureEvidence`/`onStartSurvey` parameters. Do **not** add `onRetryAttachment` to `WorkOrderDetailRoute` — unlike the other two, retrying a download needs no navigation, so it resolves straight to the ViewModel method added in Task 8, the same way `onReportNoteChanged = viewModel::onReportNoteChanged` already does lower in the same function:

```kotlin
    onOpenPdf: (attachmentId: String) -> Unit,
    onMeasurePole: (poleId: String) -> Unit,
```

Add all 3 — `onOpenPdf`, `onMeasurePole`, and `onRetryAttachment: (attachmentId: String) -> Unit = {}` — as new parameters of `WorkOrderDetailScreen` (after `onRetakeEvidence`) and `WorkOrderDetailContent` (after `onRetakeEvidence`); `WorkOrderDetailScreen` needs all 3 as plain parameters so the Compose UI test (which has no ViewModel) can supply them directly, same as every other callback on that function today.

In `WorkOrderDetailRoute`'s call to `WorkOrderDetailScreen`, pass `onOpenPdf = onOpenPdf`, `onMeasurePole = onMeasurePole`, and `onRetryAttachment = viewModel::retryAttachment`. In `WorkOrderDetailScreen`'s call to `WorkOrderDetailContent`, pass all 3 straight through unchanged.

In `WorkOrderDetailContent`, change the `if (kind == WorkOrderTaskKind.SURVEY) {...} else {...}` block to a 3-way split:

```kotlin
            if (kind == WorkOrderTaskKind.SURVEY) {
                Text(text = "Lệnh khảo sát — xem trong tab Khảo sát", style = MaterialTheme.typography.bodyMedium)
            } else if (kind == WorkOrderTaskKind.POLE_GPS_UPDATE) {
                PoleGpsOverviewSection(
                    state = state,
                    onOpenPdf = onOpenPdf,
                    onMeasurePole = onMeasurePole,
                    onRetryAttachment = onRetryAttachment,
                )
                if (canComplete) {
                    PoleGpsReportSection(state = state, locked = isFormLocked, onReportNoteChanged = onReportNoteChanged)
                }
            } else {
                FaultListSection(
                    kind = kind,
                    faults = detail.faults,
                    onNavigateFault = onNavigateFault,
                    answeredFaultIds = state.faultOutcomes.keys,
                )
                if (status == WorkOrderStatus.ASSIGNED) {
                    PreStartRequirementsPreview(kind = kind)
                }
                if (canComplete) {
                    when (kind) {
                        WorkOrderTaskKind.REPAIR ->
                            RepairCompletionSection(
                                state = state,
                                locked = isFormLocked,
                                onReportNoteChanged = onReportNoteChanged,
                                onMaterialsUsedChanged = onMaterialsUsedChanged,
                                onCaptureEvidence = onCaptureEvidence,
                                onRetakeEvidence = onRetakeEvidence,
                            )
                        WorkOrderTaskKind.INSPECTION ->
                            InspectionCompletionSection(
                                state = state,
                                locked = isFormLocked,
                                onReportNoteChanged = onReportNoteChanged,
                                onFaultOutcomeSelected = onFaultOutcomeSelected,
                            )
                        WorkOrderTaskKind.SURVEY -> Unit
                        WorkOrderTaskKind.POLE_GPS_UPDATE -> Unit
                    }
                }
            }
```

Add these 2 new composables near `RepairCompletionSection`:

```kotlin
@Composable
private fun PoleGpsOverviewSection(
    state: WorkOrderDetailUiState.Success,
    onOpenPdf: (String) -> Unit,
    onMeasurePole: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CompletionSectionHeader(
            icon = Icons.Filled.StickyNote2,
            title = "Tệp PDF",
            pillText = "${state.poleAttachments.size} file",
            pillColors = WorkOrderStatus.ASSIGNED.badgeColors(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.poleAttachments.forEach { attachment ->
                PdfAttachmentRow(attachment = attachment, onOpen = { onOpenPdf(attachment.attachmentId) }, onRetry = { onRetryAttachment(attachment.attachmentId) })
            }
        }
        val measuredIds = state.poleMeasurements.map { it.poleId }.toSet()
        CompletionSectionHeader(
            icon = Icons.Filled.Navigation,
            title = "Cột đèn",
            pillText = "${measuredIds.size}/${state.poleReferences.size} đã đo",
            pillColors = WorkOrderStatus.ASSIGNED.badgeColors(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.poleReferences.forEach { reference ->
                PoleGpsRow(
                    reference = reference,
                    measurement = state.poleMeasurements.firstOrNull { it.poleId == reference.poleId },
                    onClick = { onMeasurePole(reference.poleId) },
                )
            }
        }
    }
}

@Composable
private fun PdfAttachmentRow(
    attachment: com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val (label, canTap, onTap) =
        when (attachment.status) {
            com.luxmap.feature.workorder.polegps.data.AttachmentStatus.READY -> Triple("Sẵn sàng", true, onOpen)
            com.luxmap.feature.workorder.polegps.data.AttachmentStatus.FAILED -> Triple("Lỗi · Nhấn để thử lại", true, onRetry)
            com.luxmap.feature.workorder.polegps.data.AttachmentStatus.DOWNLOADING -> Triple("Đang tải", false, {})
            com.luxmap.feature.workorder.polegps.data.AttachmentStatus.PENDING -> Triple("Chờ tải", false, {})
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .then(if (canTap) Modifier.clip(RoundedCornerShape(Radius.card)) else Modifier)
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(imageVector = Icons.Filled.StickyNote2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = attachment.fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onTap, enabled = canTap) { Text(label) }
    }
}

@Composable
private fun PoleGpsRow(
    reference: com.luxmap.feature.workorder.polegps.data.PoleReference,
    measurement: com.luxmap.feature.workorder.polegps.data.PoleMeasurement?,
    onClick: () -> Unit,
) {
    val oldPosition =
        if (reference.lat != null && reference.lng != null) {
            "%.6f, %.6f".format(reference.lat, reference.lng)
        } else {
            "Chưa có"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .clickable(onClick = onClick)
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = reference.poleId, style = MaterialTheme.typography.titleMedium)
            Text(text = "GPS cũ: $oldPosition", style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (measurement != null) {
            StatusBadge(text = "±${"%.1f".format(measurement.accuracyM)} m", colors = WorkOrderStatus.VERIFIED.badgeColors())
        } else {
            StatusBadge(text = "Chưa đo", colors = WorkOrderStatus.OPEN.badgeColors())
        }
    }
}

@Composable
private fun PoleGpsReportSection(
    state: WorkOrderDetailUiState.Success,
    locked: Boolean,
    onReportNoteChanged: (String) -> Unit,
) {
    val noteValid = state.reportNote.trim().length >= 10
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CompletionSectionHeader(
            icon = Icons.Filled.FactCheck,
            title = "Báo cáo",
            pillText = if (noteValid) "Đã đủ" else "Cần ghi chú",
            pillColors = if (noteValid) WorkOrderStatus.VERIFIED.badgeColors() else WorkOrderStatus.ASSIGNED.badgeColors(),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                    .padding(Spacing.md),
        ) {
            WorkflowStepRow(
                number = 1,
                title = "Kết quả đo GPS",
                required = true,
                done = noteValid,
                detail = "${state.reportNote.trim().length}/10 ký tự tối thiểu",
                isLast = true,
            ) {
                OutlinedTextField(
                    value = state.reportNote,
                    onValueChange = onReportNoteChanged,
                    placeholder = { Text("VD: Đã đo xong các cột, 1 cột chưa bắt được tín hiệu GPS") },
                    enabled = !locked,
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

Add a guard clause to `workOrderCtaState` right before the `workOrderStatusFromWire(...) == DONE` line:

```kotlin
        "complete" in detail.allowedActions && kind == WorkOrderTaskKind.POLE_GPS_UPDATE -> {
            val measured = state.poleMeasurements.map { it.poleId }.toSet().size
            val missing = state.poleReferences.size - measured
            WorkOrderCtaState(
                text = "Nộp báo cáo",
                icon = if (canSubmitNow) Icons.Filled.Send else Icons.Filled.Lock,
                enabled = canSubmitNow,
                onClick = onSubmit,
                hint =
                    when {
                        scheduleWindow.isOverdueToComplete -> "Đã quá hạn nộp báo cáo · liên hệ Kỹ sư bảo trì để xử lý"
                        missing > 0 -> "Còn $missing cột chưa đo"
                        else -> null
                    },
                hintColor = if (scheduleWindow.isOverdueToComplete) Danger600 else DueTodayColor,
                requiresConfirmation = true,
            )
        }
```

Add a branch to `submitSummaryRows`:

```kotlin
private fun submitSummaryRows(
    kind: WorkOrderTaskKind,
    state: WorkOrderDetailUiState.Success,
): List<SubmitSummaryRow> =
    if (kind == WorkOrderTaskKind.INSPECTION) {
        ...
    } else if (kind == WorkOrderTaskKind.POLE_GPS_UPDATE) {
        val measured = state.poleMeasurements.map { it.poleId }.toSet().size
        listOf(
            SubmitSummaryRow(Icons.Filled.Navigation, Navy700, "Cột đã đo", "$measured/${state.poleReferences.size}"),
            SubmitSummaryRow(Icons.Filled.StickyNote2, Navy700, "Tệp PDF", state.poleAttachments.size.toString()),
            SubmitSummaryRow(Icons.Filled.EditNote, Navy700, "Ghi chú", state.reportNote.trim().ifEmpty { "—" }),
        )
    } else {
        ...
    }
```

(Keep the existing `if (kind == INSPECTION) {...} else {...}` body text unchanged; only add the new `else if` branch between them.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:compileLocalDebugAndroidTestKotlin ktlintCheck`; with the device `SM-A075F` attached: `./gradlew :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.ui.detail.WorkOrderDetailScreenTest`
Expected: compile OK, no ktlint errors, all `WorkOrderDetailScreenTest` cases (existing + new) PASS on the device.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt
git commit -m "feat(pole-gps): add the F09 detail branch for pole GPS update orders"
```

---

### Task 11: Navigation wiring

**Files:**
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt` (2 new routes)
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt` (2 new `composable` blocks, 3 new callbacks on the existing `WorkOrderDetailRoute` call)

**Interfaces:**
- Consumes: `PoleGpsMeasureRoute` (first plan Task 6), `PoleGpsPdfViewerRoute` (Task 9), `WorkOrderDetailRoute`'s 3 new callbacks (Task 10).
- Produces: `Routes.PoleGpsMeasure.createRoute(workOrderId, poleId)`, `Routes.PoleGpsPdfViewer.createRoute(workOrderId, attachmentId)`.

This task has no unit-testable logic of its own (it is pure wiring); it is verified by the manual real-device check in Step 2, same as every other `NavGraph.kt` change in this codebase's history.

- [ ] **Step 1: Implement**

In `Routes.kt`, add after `WorkOrderEvidenceCapture`:

```kotlin
    data object PoleGpsMeasure : Routes {
        override val route = "work-order/{workOrderId}/pole-gps/{poleId}"

        fun createRoute(
            workOrderId: String,
            poleId: String,
        ) = "work-order/$workOrderId/pole-gps/$poleId"
    }

    data object PoleGpsPdfViewer : Routes {
        override val route = "work-order/{workOrderId}/pole-gps/pdf/{attachmentId}"

        fun createRoute(
            workOrderId: String,
            attachmentId: String,
        ) = "work-order/$workOrderId/pole-gps/pdf/$attachmentId"
    }
```

In `NavGraph.kt`, add the imports `com.luxmap.feature.workorder.polegps.ui.measure.PoleGpsMeasureRoute` and `com.luxmap.feature.workorder.polegps.ui.pdf.PoleGpsPdfViewerRoute`. Change the existing `WorkOrderDetailRoute(...)` call (inside the `Routes.WorkOrderDetail.route` composable) to add the 2 new callbacks (`onRetryAttachment` is not one of them — Task 10 resolved it inside `WorkOrderDetailRoute` itself, straight to the ViewModel, so `NavGraph.kt` never sees it):

```kotlin
                WorkOrderDetailRoute(
                    onBack = { navController.popBackStack() },
                    onCaptureEvidence = {
                        navController.navigate(Routes.WorkOrderEvidenceCapture.createRoute(workOrderId))
                    },
                    onStartSurvey = {
                        navController.navigate(Routes.Survey.createRoute(workOrderId))
                    },
                    onOpenPdf = { attachmentId ->
                        navController.navigate(Routes.PoleGpsPdfViewer.createRoute(workOrderId, attachmentId))
                    },
                    onMeasurePole = { poleId ->
                        navController.navigate(Routes.PoleGpsMeasure.createRoute(workOrderId, poleId))
                    },
                )
```

Add 2 new `composable` blocks after the existing `Routes.WorkOrderEvidenceCapture` block:

```kotlin
            composable(
                route = Routes.PoleGpsMeasure.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("poleId") { type = NavType.StringType },
                    ),
            ) {
                PoleGpsMeasureRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PoleGpsPdfViewer.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("attachmentId") { type = NavType.StringType },
                    ),
            ) {
                PoleGpsPdfViewerRoute(onBack = { navController.popBackStack() })
            }
```

- [ ] **Step 2: Verify**

Run: `./gradlew :app:compileLocalDebugKotlin ktlintCheck`
Expected: compile OK, no ktlint errors.

On the device `SM-A075F`: install the debug build, open a `pole_gps_update` work order's detail screen (seed one through the Fake/test backend path already used for manual checks in this project, or any order the backend has assigned), tap a PDF row and a pole row, confirm both routes open and the back button returns to the detail screen.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/navigation/Routes.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt
git commit -m "feat(pole-gps): wire navigation to the measure screen and PDF viewer"
```
