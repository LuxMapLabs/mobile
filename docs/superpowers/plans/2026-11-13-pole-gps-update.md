# Pole GPS Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a Field Engineer open the PDFs of a `pole_gps_update` work order offline, measure the real GPS of each target pole, and submit the positions through the existing queued `complete` call.

**Architecture:** One new feature package `feature/workorder/polegps` (data + ui) plus small, additive changes to existing work-order, survey pole-list, Room, sync and navigation code. Measurements and PDF metadata live in three new Room tables behind an explicit `Migration(16, 17)`; the existing `complete_work_order` sync handler assembles `pole_locations[]` from Room; PDFs download through a Hilt WorkManager worker and render with the platform `PdfRenderer`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt (`@HiltViewModel`, `@HiltWorker`), Room, WorkManager, Retrofit + kotlinx.serialization, Fused Location, Android `PdfRenderer`, JUnit4 + MockK + Turbine + MockWebServer. No new library.

**Spec:** `docs/superpowers/specs/2026-11-13-pole-gps-update-design.md` (BE contract: `luxmap_backend/docs/contract-drift.md` WO-POLE-GPS PG-1..PG-13).

## Global Constraints

- No new library (CLAUDE.md "Ngăn xếp công nghệ"). PDF uses `android.graphics.pdf.PdfRenderer`.
- ZERO comments in any code written for this plan (user rule, 2026-10-05).
- Each commit at most 400 changed lines; Conventional Commits in English: `type(scope): description`, scope `pole-gps`.
- Branch `feat/pole-gps-update`, off `dev`. Never commit to `main`. No push or PR without the user's go-ahead.
- Design System v2 tokens only; no new colour token; no dynamic colour. Touch targets at least 48dp.
- Every async screen has all four UiState branches (Loading / Success / Empty / Error), exhaustive `when` with no `else`.
- Offline-first: write Room first, then enqueue `sync_queue`; never call the API from UI or ViewModel.
- Room goes 16 to 17 with an explicit `MIGRATION_16_17` and `fallbackToDestructiveMigration()` stays; the migration must keep `sync_queue` rows.
- `v1` does not scan QR. `verify`, `return`, attachment upload/remove, order creation are Web GIS only.
- Complete body rules: `pole_locations` carries EVERY local measurement of the order; `captured_at` is ISO-8601 UTC and never later than now; `report_note` stays required (trim length at least 10).
- GPS gate: save enabled only when accuracy is at most `GpsAccuracyGate.PROPOSED_THRESHOLD_METERS` (8 m) held for the gate hold time.
- Run `./gradlew ktlintCheck` after each task. Ask the user before running the full Gradle test suite; targeted `--tests` runs and compile are fine.
- Chat prose to the user in Vietnamese; code and commits in English.

## Review Focus

1. A pole with `position = null` and `location = null` parses and renders "Chưa có GPS" without crashing (Task 2 DTO test, Task 2 label test).
2. Engineer measures offline, then submits: the completion is queued and never blocked by network (Task 6 ViewModel test, Task 9 handler test).
3. A returned order re-sends ALL poles, device-measured and server-prefilled, or the server drops the earlier ones (Task 9 handler test).
4. A phone clock ahead of the server: `captured_at` is clamped to now, and a 400 `VALIDATION_FAILED` becomes `Failed`, not an endless retry (Task 9 handler tests).
5. A removed PDF (404 `ATTACHMENT_NOT_FOUND`) marks only that file failed and keeps the others (Task 4 downloader test).
6. The Room upgrade keeps unsent `sync_queue` rows (Task 3 migration test).

---

### Task 1: Task kind enum, label, lifecycle and Home entry

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Color.kt` (imports, `WorkOrderTaskKind` block near lines 298-329)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderLifecycle.kt` (`workOrderLifecycleSteps`, line 22)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt` (exhaustive `when` sites, `LifecycleTracker` call near 887)
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt` (`TaskFilterTab` line 287, `TASK_KIND_SECTION_ORDER` line 298, `TabEmptyState`)
- Test: `app/src/test/java/com/luxmap/core/theme/WorkOrderTaskKindTest.kt` (create)
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderLifecycleTest.kt` (add case)
- Test: `app/src/test/java/com/luxmap/feature/home/ui/HomeTaskKindSectionsTest.kt` (create)

**Interfaces:**
- Consumes: existing `WorkOrderStatus.VERIFIED.badgeColors()`, `workOrderLifecycleSteps`.
- Produces: `WorkOrderTaskKind.POLE_GPS_UPDATE`; `workOrderTaskKindFromWire("pole_gps_update")`; `internal val TASK_KIND_SECTION_ORDER: List<WorkOrderTaskKind>` containing `POLE_GPS_UPDATE`; `workOrderLifecycleSteps(_, POLE_GPS_UPDATE)` returns `emptyList()`.

- [ ] **Step 1: Write the failing tests**

`WorkOrderTaskKindTest.kt`:

```kotlin
package com.luxmap.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkOrderTaskKindTest {
    @Test
    fun `wire value maps to POLE_GPS_UPDATE`() {
        assertEquals(WorkOrderTaskKind.POLE_GPS_UPDATE, workOrderTaskKindFromWire("pole_gps_update"))
    }

    @Test
    fun `unknown wire value still falls back to INSPECTION`() {
        assertEquals(WorkOrderTaskKind.INSPECTION, workOrderTaskKindFromWire("something_new"))
    }

    @Test
    fun `label is the Vietnamese pole GPS name`() {
        assertEquals("Cập nhật GPS cột", WorkOrderTaskKind.POLE_GPS_UPDATE.label())
    }

    @Test
    fun `badge uses the verified status palette`() {
        assertEquals(WorkOrderStatus.VERIFIED.badgeColors(), WorkOrderTaskKind.POLE_GPS_UPDATE.badgeColors())
    }
}
```

`HomeTaskKindSectionsTest.kt`:

```kotlin
package com.luxmap.feature.home.ui

import com.luxmap.core.theme.WorkOrderTaskKind
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTaskKindSectionsTest {
    @Test
    fun `section order includes pole GPS update so those orders are not dropped`() {
        assertTrue(TASK_KIND_SECTION_ORDER.contains(WorkOrderTaskKind.POLE_GPS_UPDATE))
    }
}
```

Add to `WorkOrderLifecycleTest.kt` (inside the class; import `WorkOrderTaskKind`, `WorkOrderStatus` and `assertTrue` if absent):

```kotlin
    @Test
    fun `pole GPS update has no lifecycle steps`() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.ASSIGNED, WorkOrderTaskKind.POLE_GPS_UPDATE)
        assertTrue(steps.isEmpty())
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.theme.WorkOrderTaskKindTest" --tests "com.luxmap.feature.home.ui.HomeTaskKindSectionsTest" --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderLifecycleTest"`
Expected: compilation FAIL, "Unresolved reference: POLE_GPS_UPDATE" and "TASK_KIND_SECTION_ORDER" (private).

- [ ] **Step 3: Implement**

`Color.kt`: add `import androidx.compose.material.icons.filled.LocationOn` with the other icon imports, then change the block:

```kotlin
enum class WorkOrderTaskKind { INSPECTION, REPAIR, SURVEY, POLE_GPS_UPDATE }
```

and add the branches to the four functions:

```kotlin
fun WorkOrderTaskKind.label(): String =
    when (this) {
        WorkOrderTaskKind.INSPECTION -> "Kiểm tra"
        WorkOrderTaskKind.REPAIR -> "Sửa chữa"
        WorkOrderTaskKind.SURVEY -> "Khảo sát"
        WorkOrderTaskKind.POLE_GPS_UPDATE -> "Cập nhật GPS cột"
    }
```

```kotlin
        WorkOrderTaskKind.POLE_GPS_UPDATE -> Icons.Filled.LocationOn
```
(in `icon()`),

```kotlin
        WorkOrderTaskKind.POLE_GPS_UPDATE -> WorkOrderStatus.VERIFIED.badgeColors()
```
(in `badgeColors()`), and in `workOrderTaskKindFromWire` add `"pole_gps_update" -> WorkOrderTaskKind.POLE_GPS_UPDATE` before `else`. Keep the existing branch bodies as they are; only add the new lines.

`WorkOrderLifecycle.kt`: directly after the `check(woStatus != WorkOrderStatus.CANCELLED) {...}` line add

```kotlin
    if (taskKind == WorkOrderTaskKind.POLE_GPS_UPDATE) return emptyList()
```

and in `val currentIndex = when (taskKind) {...}` add `WorkOrderTaskKind.POLE_GPS_UPDATE -> 0`.

`HomeScreen.kt`: add the tab entry `POLE_GPS_UPDATE("Cập nhật GPS", WorkOrderTaskKind.POLE_GPS_UPDATE)` to `TaskFilterTab`; change line 298 to

```kotlin
internal val TASK_KIND_SECTION_ORDER =
    listOf(WorkOrderTaskKind.REPAIR, WorkOrderTaskKind.INSPECTION, WorkOrderTaskKind.POLE_GPS_UPDATE)
```

and extend `TabEmptyState`'s `when` with the title "Không có nhiệm vụ cập nhật GPS" and body "Lệnh đo lại vị trí cột đèn được giao sẽ hiện ở đây.", copying the shape of the neighbouring branches.

`WorkOrderDetailScreen.kt`: run `./gradlew :app:compileDebugKotlin`; for every "'when' expression must be exhaustive" error add `WorkOrderTaskKind.POLE_GPS_UPDATE -> Unit` to the inner content `when (kind)` and make the `scheduleLabel` at line 862 return `"Lịch đo GPS"` for it. Guard the tracker so an empty step list renders nothing:

```kotlin
if (lifecycleSteps.isNotEmpty()) {
    LifecycleTracker(steps = lifecycleSteps)
}
```
(keep the existing argument names at lines 887-889; only wrap the call).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.theme.WorkOrderTaskKindTest" --tests "com.luxmap.feature.home.ui.HomeTaskKindSectionsTest" --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderLifecycleTest"` then `./gradlew ktlintCheck`
Expected: PASS, no ktlint errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Color.kt app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderLifecycle.kt app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt app/src/test/java/com/luxmap/core/theme/WorkOrderTaskKindTest.kt app/src/test/java/com/luxmap/feature/home/ui/HomeTaskKindSectionsTest.kt app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderLifecycleTest.kt
git commit -m "feat(pole-gps): add pole_gps_update task kind to theme, lifecycle and home"
```

---

### Task 2: DTOs, domain mapping and nullable pole position

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderItemDto.kt` (add `pole_ids`)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt` (add `pole_ids`, `attachments`, `pole_locations` + new DTOs)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderCompletionDto.kt` (add `pole_locations` + `PoleLocationRequestDto`)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetail.kt` (domain fields + mapping)
- Modify: `PoleListItemDto` (position nullable + `location`), `PoleListItem`, `RealPoleListRepository` mapping, `LocalPoleListItemEntity` and `toEntity`/`toDomain` (find with Glob `**/PoleListItemDto.kt`, `**/PoleListItem.kt`, `**/LocalPoleListItemEntity.kt`, `**/RealPoleListRepository.kt`)
- Modify: `PoleListScreen.kt` (usages near lines 109, 135, 149, 185, 314, 648, 727-742), `PoleListUiState.kt:8`, `PoleListViewModel.kt:206`
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/WorkOrderPoleGpsDtoTest.kt` (create)
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleNumberLabelTest.kt` (create)
- Fix existing tests: `RealPoleListRepositoryTest`, `PoleListViewModelTest`, `PoleListScreenTest` helper `pole(...)`, plus any `PoleListItem(` construction site reported by the compiler.

**Interfaces:**
- Consumes: `kotlinx.serialization.json.Json`.
- Produces:
  - `WorkOrderItemDto.poleIds: List<String> = emptyList()` (`@SerialName("pole_ids")`).
  - `WorkOrderDetailDto.poleIds`, `.attachments: List<WorkOrderAttachmentDto>`, `.poleLocations: List<WorkOrderPoleLocationDto>`.
  - `WorkOrderAttachmentDto(attachmentId, fileName, byteCount: Long, uploadedAt: String, downloadUrl: String)`.
  - `WorkOrderPoleLocationDto(poleId, lat, lng, accuracyM: Double, capturedAt: String)`.
  - `PoleCoordinateDto(lat: Double, lng: Double)`.
  - `PoleLocationRequestDto(poleId, lat, lng, accuracyM, capturedAt: String)` and `CompleteWorkOrderRequestDto.poleLocations: List<PoleLocationRequestDto>? = null`.
  - Domain: `WorkOrderDetail.poleIds: List<String>`, `.attachments: List<WorkOrderAttachment>`, `.poleLocations: List<WorkOrderPoleLocation>`; `WorkOrderAttachment(attachmentId, fileName, byteCount: Long, uploadedAt: Instant, downloadUrl)`; `WorkOrderPoleLocation(poleId, lat, lng, accuracyM, capturedAt: Instant)`.
  - `PoleListItem(poleId: String, position: Int?, lat: Double?, lng: Double?, fixtureStatus: String)`.
  - `fun poleNumberLabel(position: Int?): String` in `PoleListUiState.kt`.

- [ ] **Step 1: Write the failing tests**

`WorkOrderPoleGpsDtoTest.kt` (adjust the `PoleListItemDto` import to its real package):

```kotlin
package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto
import com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WorkOrderPoleGpsDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val detailJson =
        """
        {
          "work_order_id": "WO-1",
          "title": "Đo GPS cột",
          "task_kind": "pole_gps_update",
          "wo_status": "assigned",
          "pole_ids": ["P-1", "P-2"],
          "attachments": [
            {
              "attachment_id": "A-1",
              "file_name": "qr.pdf",
              "byte_count": 2048,
              "uploaded_at": "2026-11-13T01:00:00Z",
              "download_url": "/api/v1/work-orders/WO-1/attachments/A-1"
            }
          ],
          "pole_locations": [
            {
              "pole_id": "P-1",
              "lat": 10.5,
              "lng": 106.5,
              "accuracy_m": 4.5,
              "captured_at": "2026-11-13T02:00:00Z"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `detail parses pole ids attachments and pole locations`() {
        val dto = json.decodeFromString<WorkOrderDetailDto>(detailJson)
        assertEquals(listOf("P-1", "P-2"), dto.poleIds)
        assertEquals("/api/v1/work-orders/WO-1/attachments/A-1", dto.attachments.single().downloadUrl)
        assertEquals(2048L, dto.attachments.single().byteCount)
        assertEquals(4.5, dto.poleLocations.single().accuracyM, 0.0)
    }

    @Test
    fun `detail without the new fields defaults to empty lists`() {
        val dto =
            json.decodeFromString<WorkOrderDetailDto>(
                """{"work_order_id":"WO-2","title":"t","task_kind":"repair","wo_status":"open"}""",
            )
        assertTrue(dto.poleIds.isEmpty())
        assertTrue(dto.attachments.isEmpty())
        assertTrue(dto.poleLocations.isEmpty())
    }

    @Test
    fun `detail maps to domain with parsed instants`() {
        val detail = json.decodeFromString<WorkOrderDetailDto>(detailJson).toWorkOrderDetail()
        assertEquals(listOf("P-1", "P-2"), detail.poleIds)
        assertEquals(Instant.parse("2026-11-13T01:00:00Z"), detail.attachments.single().uploadedAt)
        assertEquals(Instant.parse("2026-11-13T02:00:00Z"), detail.poleLocations.single().capturedAt)
    }

    @Test
    fun `complete request omits pole_locations when null and encodes them when present`() {
        val without = Json { encodeDefaults = false }.encodeToString(CompleteWorkOrderRequestDto(reportNote = "x"))
        assertTrue(!without.contains("pole_locations"))
        val with =
            Json.encodeToString(
                CompleteWorkOrderRequestDto(
                    reportNote = "x",
                    poleLocations = listOf(PoleLocationRequestDto("P-1", 10.5, 106.5, 4.5, "2026-11-13T02:00:00Z")),
                ),
            )
        assertTrue(with.contains("\"pole_id\":\"P-1\""))
        assertTrue(with.contains("\"accuracy_m\":4.5"))
        assertTrue(with.contains("\"captured_at\":\"2026-11-13T02:00:00Z\""))
    }

    @Test
    fun `pole list item with null position and null location parses`() {
        val dto =
            json.decodeFromString<com.luxmap.core.network.dto.PoleListItemDto>(
                """{"pole_id":"P-9","position":null,"location":null,"fixture_status":"unknown"}""",
            )
        assertNull(dto.position)
        assertNull(dto.location)
    }

    @Test
    fun `work order item parses pole ids`() {
        val dto =
            json.decodeFromString<WorkOrderItemDto>(
                """
                {"work_order_id":"WO-1","title":"t","commune_id":"C","task_kind":"pole_gps_update",
                 "case_id":"WO-1","fault_ids":[],"wo_status":"assigned",
                 "created_at":"2026-11-13T00:00:00Z","updated_at":"2026-11-13T00:00:00Z",
                 "pole_ids":["P-1"]}
                """.trimIndent(),
            )
        assertEquals(listOf("P-1"), dto.poleIds)
    }
}
```

`PoleNumberLabelTest.kt` (match the package of `PoleListUiState.kt`):

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import org.junit.Assert.assertEquals
import org.junit.Test

class PoleNumberLabelTest {
    @Test
    fun `placed pole shows its number`() {
        assertEquals("Cột 3", poleNumberLabel(3))
    }

    @Test
    fun `unplaced pole shows no GPS yet`() {
        assertEquals("Chưa có GPS", poleNumberLabel(null))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.WorkOrderPoleGpsDtoTest" --tests "*PoleNumberLabelTest"`
Expected: compilation FAIL, unresolved `poleIds`, `PoleLocationRequestDto`, `poleNumberLabel`.

- [ ] **Step 3: Implement**

`WorkOrderItemDto.kt`: append as the last constructor property

```kotlin
    @SerialName("pole_ids") val poleIds: List<String> = emptyList(),
```

`WorkOrderDetailDto.kt`: append after `faults`

```kotlin
    @SerialName("pole_ids") val poleIds: List<String> = emptyList(),
    val attachments: List<WorkOrderAttachmentDto> = emptyList(),
    @SerialName("pole_locations") val poleLocations: List<WorkOrderPoleLocationDto> = emptyList(),
```

and in the same file

```kotlin
@Serializable
data class WorkOrderAttachmentDto(
    @SerialName("attachment_id") val attachmentId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("byte_count") val byteCount: Long,
    @SerialName("uploaded_at") val uploadedAt: String,
    @SerialName("download_url") val downloadUrl: String,
)

@Serializable
data class WorkOrderPoleLocationDto(
    @SerialName("pole_id") val poleId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("accuracy_m") val accuracyM: Double,
    @SerialName("captured_at") val capturedAt: String,
)

@Serializable
data class PoleCoordinateDto(
    val lat: Double,
    val lng: Double,
)
```

`WorkOrderCompletionDto.kt`: add to `CompleteWorkOrderRequestDto` the last property `@SerialName("pole_locations") val poleLocations: List<PoleLocationRequestDto>? = null,` and

```kotlin
@Serializable
data class PoleLocationRequestDto(
    @SerialName("pole_id") val poleId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("accuracy_m") val accuracyM: Double,
    @SerialName("captured_at") val capturedAt: String,
)
```

`WorkOrderDetail.kt`: add the domain properties `val poleIds: List<String> = emptyList()`, `val attachments: List<WorkOrderAttachment> = emptyList()`, `val poleLocations: List<WorkOrderPoleLocation> = emptyList()` after `faults`, then

```kotlin
data class WorkOrderAttachment(
    val attachmentId: String,
    val fileName: String,
    val byteCount: Long,
    val uploadedAt: Instant,
    val downloadUrl: String,
)

data class WorkOrderPoleLocation(
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
)
```

and in `toWorkOrderDetail()` pass

```kotlin
        poleIds = poleIds,
        attachments =
            attachments.map {
                WorkOrderAttachment(
                    it.attachmentId,
                    it.fileName,
                    it.byteCount,
                    java.time.OffsetDateTime.parse(it.uploadedAt).toInstant(),
                    it.downloadUrl,
                )
            },
        poleLocations =
            poleLocations.map {
                WorkOrderPoleLocation(
                    it.poleId,
                    it.lat,
                    it.lng,
                    it.accuracyM,
                    java.time.OffsetDateTime.parse(it.capturedAt).toInstant(),
                )
            },
```

Pole list: in `PoleListItemDto` change `position: Int` to `val position: Int? = null` and add `val location: PoleCoordinateDto? = null` (import from `com.luxmap.feature.workorder.data.dto`). `PoleListItem` becomes `PoleListItem(poleId: String, position: Int?, lat: Double?, lng: Double?, fixtureStatus: String)`; `LocalPoleListItemEntity` gets `val position: Int?`, `val lat: Double?`, `val lng: Double?`; `RealPoleListRepository` maps `PoleListItem(it.poleId, it.position, it.location?.lat, it.location?.lng, it.fixtureStatus)` and `toEntity`/`toDomain` copy the two new fields. In `PoleListUiState.kt` add

```kotlin
fun poleNumberLabel(position: Int?): String = position?.let { "Cột $it" } ?: "Chưa có GPS"
```

Run `./gradlew :app:compileDebugKotlin`, then at every reported usage of `position` in `PoleListScreen.kt`, `PoleListUiState.kt`, `PoleListViewModel.kt` replace an `Int` use by `position?.toString() ?: "-"` for avatar text and by `poleNumberLabel(position)` for row titles; fix existing tests' `PoleListItem(...)` calls by adding `lat = null, lng = null` named arguments.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.WorkOrderPoleGpsDtoTest" --tests "*PoleNumberLabelTest" --tests "*RealPoleListRepositoryTest" --tests "*PoleListViewModelTest"` then `./gradlew ktlintCheck`
Expected: PASS.

- [ ] **Step 5: Commit** (split in two commits if the diff exceeds 400 lines: DTO/domain first, pole-list nullable second)

```bash
git add app/src/main app/src/test
git commit -m "feat(pole-gps): parse pole ids, attachments and nullable pole position"
```

### Task 3: Room v17, migration and DAO

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsEntities.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsDao.kt`
- Create: `app/src/main/java/com/luxmap/core/database/AppMigrations.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt` (version 16 to 17, entities, `abstract fun poleGpsDao(): PoleGpsDao`)
- Modify: `app/src/main/java/com/luxmap/di/DatabaseModule.kt` (`.addMigrations(MIGRATION_16_17)`, `providePoleGpsDao`)
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/dao/PoleListCacheDao.kt` (`byWorkOrder` ordering)
- Test: `app/src/androidTest/java/com/luxmap/core/database/AppMigrationsTest.kt` (create)
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/data/PoleGpsDaoTest.kt` (create)

**Interfaces:**
- Consumes: `InstantConverters` (Instant stored as `INTEGER NOT NULL`), `LocalPoleListItemEntity` from Task 2.
- Produces:
  - `LocalPoleGpsOrderEntity(workOrderId, detailJson, fetchedAt: Instant)`.
  - `LocalWorkOrderAttachmentEntity(workOrderId, attachmentId, fileName, byteCount: Long, downloadUrl, status: String, localPath: String?)`.
  - `LocalPoleGpsMeasurementEntity(workOrderId, poleId, lat, lng, accuracyM, capturedAt: Instant, source: String)`.
  - `object AttachmentStatusColumn { PENDING, DOWNLOADING, READY, FAILED }` and `object MeasurementSourceColumn { DEVICE, SERVER }` (string constants).
  - `PoleGpsDao` with: `upsertOrder`, `order(workOrderId)`, `observeAttachments(workOrderId): Flow<List<...>>`, `attachments(workOrderId)`, `replaceAttachments(workOrderId, items)`, `deleteAttachments(workOrderId)`, `updateAttachmentStatus(workOrderId, attachmentId, status, localPath)`, `upsertDeviceMeasurement(entity)`, `insertServerMeasurements(items)`, `observeMeasurements(workOrderId): Flow<List<...>>`, `measurements(workOrderId): List<...>`.
  - `val MIGRATION_16_17: Migration`.

- [ ] **Step 1: Write the failing instrumented tests**

`AppMigrationsTest.kt` (raw SQLite, because `exportSchema = false` rules out `MigrationTestHelper`):

```kotlin
package com.luxmap.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppMigrationsTest {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion16() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(16) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "clientOpId TEXT NOT NULL, opType TEXT NOT NULL, payloadJson TEXT NOT NULL, " +
                                    "dependsOnClientOpId TEXT, status TEXT NOT NULL, attemptCount INTEGER NOT NULL, " +
                                    "lastError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                            )
                            db.execSQL(
                                "CREATE TABLE local_pole_list_item (workOrderId TEXT NOT NULL, poleId TEXT NOT NULL, " +
                                    "position INTEGER NOT NULL, fixtureStatus TEXT NOT NULL, " +
                                    "PRIMARY KEY(workOrderId, poleId))",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        db.execSQL(
            "INSERT INTO sync_queue (clientOpId, opType, payloadJson, dependsOnClientOpId, status, " +
                "attemptCount, lastError, createdAt, updatedAt) " +
                "VALUES ('OP-1', 'complete_work_order', '{}', NULL, 'queued', 0, NULL, 1, 1)",
        )
        db.execSQL("INSERT INTO local_pole_list_item VALUES ('WO-1', 'P-1', 3, 'normal')")
        MIGRATION_16_17.migrate(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun unsent_sync_queue_rows_survive() {
        db.query("SELECT clientOpId, status FROM sync_queue").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("OP-1", it.getString(0))
            assertEquals("queued", it.getString(1))
        }
    }

    @Test
    fun cached_poles_keep_their_data_and_gain_null_coordinates() {
        db.query("SELECT position, lat, lng, fixtureStatus FROM local_pole_list_item WHERE poleId = 'P-1'").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
            assertEquals("normal", it.getString(3))
        }
    }

    @Test
    fun pole_position_can_now_be_null() {
        db.execSQL("INSERT INTO local_pole_list_item VALUES ('WO-1', 'P-2', NULL, NULL, NULL, 'unknown')")
        db.query("SELECT COUNT(*) FROM local_pole_list_item").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
        }
    }

    @Test
    fun new_tables_exist() {
        val names = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            while (it.moveToNext()) names += it.getString(0)
        }
        assertTrue(names.containsAll(listOf("local_pole_gps_order", "local_work_order_attachment", "local_pole_gps_measurement")))
    }
}
```

`PoleGpsDaoTest.kt` (replace `poleListCacheDao()` with the real accessor name on `AppDatabase`):

```kotlin
package com.luxmap.feature.workorder.polegps.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PoleGpsDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PoleGpsDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.poleGpsDao()
    }

    @After
    fun tearDown() = db.close()

    private fun measurement(
        source: String,
        lat: Double,
    ) = LocalPoleGpsMeasurementEntity("WO-1", "P-1", lat, 106.0, 3.0, Instant.ofEpochSecond(10), source)

    private fun attachment(
        id: String,
        status: String = AttachmentStatusColumn.PENDING,
    ) = LocalWorkOrderAttachmentEntity("WO-1", id, "$id.pdf", 10L, "/api/v1/x/$id", status, null)

    @Test
    fun device_measurement_replaces_an_earlier_server_row() =
        runTest {
            dao.insertServerMeasurements(listOf(measurement(MeasurementSourceColumn.SERVER, 1.0)))
            dao.upsertDeviceMeasurement(measurement(MeasurementSourceColumn.DEVICE, 2.0))
            val row = dao.measurements("WO-1").single()
            assertEquals(2.0, row.lat, 0.0)
            assertEquals(MeasurementSourceColumn.DEVICE, row.source)
        }

    @Test
    fun server_prefill_never_overwrites_a_device_measurement() =
        runTest {
            dao.upsertDeviceMeasurement(measurement(MeasurementSourceColumn.DEVICE, 2.0))
            dao.insertServerMeasurements(listOf(measurement(MeasurementSourceColumn.SERVER, 1.0)))
            assertEquals(2.0, dao.measurements("WO-1").single().lat, 0.0)
        }

    @Test
    fun replace_attachments_keeps_existing_status_and_drops_removed_files() =
        runTest {
            dao.replaceAttachments("WO-1", listOf(attachment("A-1"), attachment("A-2")))
            dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.READY, "/files/a1.pdf")
            dao.replaceAttachments("WO-1", listOf(attachment("A-1")))
            val rows = dao.attachments("WO-1")
            assertEquals(listOf("A-1"), rows.map { it.attachmentId })
            assertEquals(AttachmentStatusColumn.READY, rows.single().status)
            assertEquals("/files/a1.pdf", rows.single().localPath)
        }

    @Test
    fun replace_attachments_with_empty_list_clears_the_order() =
        runTest {
            dao.replaceAttachments("WO-1", listOf(attachment("A-1")))
            dao.replaceAttachments("WO-1", emptyList())
            assertTrue(dao.attachments("WO-1").isEmpty())
        }

    @Test
    fun pole_list_cache_puts_unplaced_poles_last() =
        runTest {
            val cache = db.poleListCacheDao()
            cache.insertAll(
                listOf(
                    LocalPoleListItemEntity("WO-1", "P-null", null, null, null, "unknown"),
                    LocalPoleListItemEntity("WO-1", "P-2", 2, 1.0, 1.0, "normal"),
                    LocalPoleListItemEntity("WO-1", "P-1", 1, 1.0, 1.0, "normal"),
                ),
            )
            assertEquals(listOf("P-1", "P-2", "P-null"), cache.byWorkOrder("WO-1").map { it.poleId })
        }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL, unresolved `MIGRATION_16_17`, `PoleGpsDao`, `LocalPoleGpsMeasurementEntity`, `poleGpsDao`.

- [ ] **Step 3: Implement**

`PoleGpsEntities.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

object AttachmentStatusColumn {
    const val PENDING = "pending"
    const val DOWNLOADING = "downloading"
    const val READY = "ready"
    const val FAILED = "failed"
}

object MeasurementSourceColumn {
    const val DEVICE = "device"
    const val SERVER = "server"
}

@Entity(tableName = "local_pole_gps_order")
data class LocalPoleGpsOrderEntity(
    @PrimaryKey val workOrderId: String,
    val detailJson: String,
    val fetchedAt: Instant,
)

@Entity(tableName = "local_work_order_attachment", primaryKeys = ["workOrderId", "attachmentId"])
data class LocalWorkOrderAttachmentEntity(
    val workOrderId: String,
    val attachmentId: String,
    val fileName: String,
    val byteCount: Long,
    val downloadUrl: String,
    val status: String,
    val localPath: String?,
)

@Entity(tableName = "local_pole_gps_measurement", primaryKeys = ["workOrderId", "poleId"])
data class LocalPoleGpsMeasurementEntity(
    val workOrderId: String,
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
    val source: String,
)
```

`PoleGpsDao.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PoleGpsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOrder(entity: LocalPoleGpsOrderEntity)

    @Query("SELECT * FROM local_pole_gps_order WHERE workOrderId = :workOrderId")
    abstract suspend fun order(workOrderId: String): LocalPoleGpsOrderEntity?

    @Query("SELECT * FROM local_work_order_attachment WHERE workOrderId = :workOrderId ORDER BY fileName")
    abstract fun observeAttachments(workOrderId: String): Flow<List<LocalWorkOrderAttachmentEntity>>

    @Query("SELECT * FROM local_work_order_attachment WHERE workOrderId = :workOrderId ORDER BY fileName")
    abstract suspend fun attachments(workOrderId: String): List<LocalWorkOrderAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAttachmentsIgnore(items: List<LocalWorkOrderAttachmentEntity>)

    @Query("DELETE FROM local_work_order_attachment WHERE workOrderId = :workOrderId AND attachmentId NOT IN (:keepIds)")
    abstract suspend fun deleteAttachmentsNotIn(
        workOrderId: String,
        keepIds: List<String>,
    )

    @Query("DELETE FROM local_work_order_attachment WHERE workOrderId = :workOrderId")
    abstract suspend fun deleteAttachments(workOrderId: String)

    @Query(
        "UPDATE local_work_order_attachment SET status = :status, localPath = :localPath " +
            "WHERE workOrderId = :workOrderId AND attachmentId = :attachmentId",
    )
    abstract suspend fun updateAttachmentStatus(
        workOrderId: String,
        attachmentId: String,
        status: String,
        localPath: String?,
    )

    @Transaction
    open suspend fun replaceAttachments(
        workOrderId: String,
        items: List<LocalWorkOrderAttachmentEntity>,
    ) {
        insertAttachmentsIgnore(items)
        deleteAttachmentsNotIn(workOrderId, items.map { it.attachmentId })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDeviceMeasurement(entity: LocalPoleGpsMeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertServerMeasurements(items: List<LocalPoleGpsMeasurementEntity>)

    @Query("SELECT * FROM local_pole_gps_measurement WHERE workOrderId = :workOrderId")
    abstract fun observeMeasurements(workOrderId: String): Flow<List<LocalPoleGpsMeasurementEntity>>

    @Query("SELECT * FROM local_pole_gps_measurement WHERE workOrderId = :workOrderId")
    abstract suspend fun measurements(workOrderId: String): List<LocalPoleGpsMeasurementEntity>
}
```

`AppMigrations.kt` (open `LocalPoleListItemEntity` first and make the `_new` table definition match its `@Entity` exactly: same primary key and any indices):

```kotlin
package com.luxmap.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17: Migration =
    object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_pole_gps_order (workOrderId TEXT NOT NULL, " +
                    "detailJson TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(workOrderId))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_work_order_attachment (workOrderId TEXT NOT NULL, " +
                    "attachmentId TEXT NOT NULL, fileName TEXT NOT NULL, byteCount INTEGER NOT NULL, " +
                    "downloadUrl TEXT NOT NULL, status TEXT NOT NULL, localPath TEXT, " +
                    "PRIMARY KEY(workOrderId, attachmentId))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_pole_gps_measurement (workOrderId TEXT NOT NULL, " +
                    "poleId TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, accuracyM REAL NOT NULL, " +
                    "capturedAt INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(workOrderId, poleId))",
            )
            db.execSQL(
                "CREATE TABLE local_pole_list_item_new (workOrderId TEXT NOT NULL, poleId TEXT NOT NULL, " +
                    "position INTEGER, lat REAL, lng REAL, fixtureStatus TEXT NOT NULL, " +
                    "PRIMARY KEY(workOrderId, poleId))",
            )
            db.execSQL(
                "INSERT INTO local_pole_list_item_new (workOrderId, poleId, position, fixtureStatus) " +
                    "SELECT workOrderId, poleId, position, fixtureStatus FROM local_pole_list_item",
            )
            db.execSQL("DROP TABLE local_pole_list_item")
            db.execSQL("ALTER TABLE local_pole_list_item_new RENAME TO local_pole_list_item")
        }
    }
```

`AppDatabase.kt`: change `version = 16` to `17`, append `LocalPoleGpsOrderEntity::class, LocalWorkOrderAttachmentEntity::class, LocalPoleGpsMeasurementEntity::class` to `entities`, add `abstract fun poleGpsDao(): PoleGpsDao`. `DatabaseModule.kt`: insert `.addMigrations(MIGRATION_16_17)` before `.fallbackToDestructiveMigration()` and add

```kotlin
    @Provides
    fun providePoleGpsDao(db: AppDatabase): PoleGpsDao = db.poleGpsDao()
```

`PoleListCacheDao.kt`: change the `byWorkOrder` query to `ORDER BY position IS NULL, position`.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck` then, with the real device SM-A075F attached, `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.core.database.AppMigrationsTest,com.luxmap.feature.workorder.polegps.data.PoleGpsDaoTest`
Expected: compile OK; 9 tests PASS on the device (the emulator is broken by the InputManager bug, do not use it).

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/androidTest
git commit -m "feat(pole-gps): add Room v17 tables with explicit 16 to 17 migration"
```

---

### Task 4: Attachment download and order cache

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt` (add `downloadAttachment`)
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsFiles.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/AttachmentDownloader.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/AttachmentDownloadScheduler.kt` (interface + WorkManager impl)
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/AttachmentDownloadWorker.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsDetailSync.kt`
- Create: `app/src/main/java/com/luxmap/di/PoleGpsModule.kt` (`@Binds` scheduler)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderDetailRepository.kt`
- Test: `app/src/test/java/com/luxmap/core/network/WorkOrdersApiAttachmentTest.kt`, `.../polegps/data/PoleGpsFilesTest.kt`, `.../polegps/data/AttachmentDownloaderTest.kt`, `.../polegps/data/PoleGpsDetailSyncTest.kt`, and additions to `RealWorkOrderDetailRepositoryTest`

**Interfaces:**
- Consumes: `PoleGpsDao`, `AttachmentStatusColumn`, `MeasurementSourceColumn`, entities (Task 3); `WorkOrderDetailDto` (Task 2).
- Produces:
  - `WorkOrdersApi.downloadAttachment(url: String): ResponseBody` (`@Streaming @GET`, `@Url`).
  - `PoleGpsFiles(root: File)` with `@Inject constructor(@ApplicationContext context: Context)`; `fileFor(workOrderId, attachmentId): File`; `deleteOrder(workOrderId)`; top-level `fun sanitizeSegment(value: String): String`.
  - `enum class DownloadOutcome { DONE, RETRY }`; `AttachmentDownloader.downloadPending(workOrderId): DownloadOutcome`; `AttachmentDownloader.retry(workOrderId, attachmentId)`.
  - `interface AttachmentDownloadScheduler { fun schedule(workOrderId: String) }`.
  - `PoleGpsDetailSync.store(dto: WorkOrderDetailDto)`, `PoleGpsDetailSync.cached(workOrderId): WorkOrderDetailDto?`.

- [ ] **Step 1: Write the failing tests**

`WorkOrdersApiAttachmentTest.kt`:

```kotlin
package com.luxmap.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class WorkOrdersApiAttachmentTest {
    private lateinit var server: MockWebServer
    private lateinit var api: WorkOrdersApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder().baseUrl(server.url("/api/v1/")).build().create(WorkOrdersApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `download_url replaces the base path and returns the raw bytes`() =
        runTest {
            server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3))))
            val body = api.downloadAttachment("/api/v1/work-orders/WO-1/attachments/A-1")
            assertEquals(listOf<Byte>(1, 2, 3), body.bytes().toList())
            assertEquals("/api/v1/work-orders/WO-1/attachments/A-1", server.takeRequest().path)
        }
}
```

`PoleGpsFilesTest.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PoleGpsFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `server ids cannot escape the root`() {
        val files = PoleGpsFiles(tmp.root)
        val file = files.fileFor("../WO", "../../a")
        assertTrue(file.canonicalPath.startsWith(tmp.root.canonicalPath))
        assertEquals("______a.pdf", file.name)
    }

    @Test
    fun `deleteOrder removes the whole order folder`() {
        val files = PoleGpsFiles(tmp.root)
        val file = files.fileFor("WO-1", "A-1")
        file.parentFile!!.mkdirs()
        file.writeText("x")
        files.deleteOrder("WO-1")
        assertFalse(file.exists())
    }
}
```

`AttachmentDownloaderTest.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import com.luxmap.core.network.WorkOrdersApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AttachmentDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val api = mockk<WorkOrdersApi>()
    private val dao = mockk<PoleGpsDao>(relaxed = true)

    private fun attachment(
        id: String,
        status: String = AttachmentStatusColumn.PENDING,
    ) = LocalWorkOrderAttachmentEntity("WO-1", id, "$id.pdf", 3L, "/api/v1/work-orders/WO-1/attachments/$id", status, null)

    private fun downloader() = AttachmentDownloader(api, dao, PoleGpsFiles(tmp.root))

    private fun pdf(text: String): ResponseBody = text.toResponseBody("application/pdf".toMediaType())

    private fun notFound() = HttpException(Response.error<ResponseBody>(404, "{}".toResponseBody("application/json".toMediaType())))

    @Test
    fun `successful download writes the file and marks it ready`() =
        runTest {
            coEvery { dao.attachments("WO-1") } returns listOf(attachment("A-1"))
            coEvery { api.downloadAttachment(any()) } returns pdf("abc")
            val outcome = downloader().downloadPending("WO-1")
            assertEquals(DownloadOutcome.DONE, outcome)
            val path = PoleGpsFiles(tmp.root).fileFor("WO-1", "A-1")
            assertEquals("abc", path.readText())
            coVerify { dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.READY, path.absolutePath) }
        }

    @Test
    fun `a removed file is marked failed and the others still download`() =
        runTest {
            coEvery { dao.attachments("WO-1") } returns listOf(attachment("A-1"), attachment("A-2"))
            coEvery { api.downloadAttachment("/api/v1/work-orders/WO-1/attachments/A-1") } throws notFound()
            coEvery { api.downloadAttachment("/api/v1/work-orders/WO-1/attachments/A-2") } returns pdf("ok")
            val outcome = downloader().downloadPending("WO-1")
            assertEquals(DownloadOutcome.DONE, outcome)
            coVerify { dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.FAILED, null) }
            assertTrue(PoleGpsFiles(tmp.root).fileFor("WO-1", "A-2").exists())
        }

    @Test
    fun `a network error keeps the file pending and asks for a retry`() =
        runTest {
            coEvery { dao.attachments("WO-1") } returns listOf(attachment("A-1"))
            coEvery { api.downloadAttachment(any()) } throws IOException("offline")
            val outcome = downloader().downloadPending("WO-1")
            assertEquals(DownloadOutcome.RETRY, outcome)
            coVerify { dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.PENDING, null) }
        }

    @Test
    fun `ready files are not downloaded again`() =
        runTest {
            coEvery { dao.attachments("WO-1") } returns listOf(attachment("A-1", AttachmentStatusColumn.READY))
            downloader().downloadPending("WO-1")
            coVerify(exactly = 0) { api.downloadAttachment(any()) }
        }

    @Test
    fun `retry puts a failed file back to pending`() =
        runTest {
            downloader().retry("WO-1", "A-1")
            coVerify { dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.PENDING, null) }
        }
}
```

`PoleGpsDetailSyncTest.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import com.luxmap.feature.workorder.data.dto.WorkOrderAttachmentDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderPoleLocationDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoleGpsDetailSyncTest {
    private val dao = mockk<PoleGpsDao>(relaxed = true)
    private val scheduler = mockk<AttachmentDownloadScheduler>(relaxed = true)
    private val files = mockk<PoleGpsFiles>(relaxed = true)
    private val sync = PoleGpsDetailSync(dao, scheduler, files)

    private fun dto(
        kind: String = "pole_gps_update",
        status: String = "assigned",
    ) = WorkOrderDetailDto(
        workOrderId = "WO-1",
        title = "t",
        taskKind = kind,
        woStatus = status,
        poleIds = listOf("P-1"),
        attachments = listOf(WorkOrderAttachmentDto("A-1", "a.pdf", 10, "2026-11-13T01:00:00Z", "/api/v1/work-orders/WO-1/attachments/A-1")),
        poleLocations = listOf(WorkOrderPoleLocationDto("P-1", 1.0, 2.0, 3.0, "2026-11-13T02:00:00Z")),
    )

    @Test
    fun `active order is cached, attachments stored, prefill stored and download scheduled`() =
        runTest {
            val items = slot<List<LocalWorkOrderAttachmentEntity>>()
            val prefill = slot<List<LocalPoleGpsMeasurementEntity>>()
            coEvery { dao.replaceAttachments("WO-1", capture(items)) } returns Unit
            coEvery { dao.insertServerMeasurements(capture(prefill)) } returns Unit
            sync.store(dto())
            coVerify { dao.upsertOrder(any()) }
            assertEquals(AttachmentStatusColumn.PENDING, items.captured.single().status)
            assertEquals(MeasurementSourceColumn.SERVER, prefill.captured.single().source)
            verify { scheduler.schedule("WO-1") }
        }

    @Test
    fun `finished order drops its files and rows`() =
        runTest {
            sync.store(dto(status = "done"))
            verify { files.deleteOrder("WO-1") }
            coVerify { dao.deleteAttachments("WO-1") }
            verify(exactly = 0) { scheduler.schedule(any()) }
        }

    @Test
    fun `other task kinds are ignored`() =
        runTest {
            sync.store(dto(kind = "repair"))
            coVerify(exactly = 0) { dao.upsertOrder(any()) }
        }

    @Test
    fun `cached returns the stored detail and null when missing`() =
        runTest {
            val stored = slot<LocalPoleGpsOrderEntity>()
            coEvery { dao.upsertOrder(capture(stored)) } returns Unit
            sync.store(dto())
            coEvery { dao.order("WO-1") } returns stored.captured
            coEvery { dao.order("WO-2") } returns null
            assertEquals(listOf("P-1"), sync.cached("WO-1")!!.poleIds)
            assertNull(sync.cached("WO-2"))
        }
}
```

Add to `RealWorkOrderDetailRepositoryTest` (new field `private val sync = mockk<PoleGpsDetailSync>(relaxed = true)`, and pass `sync` as the second constructor argument at every existing construction site):

```kotlin
    @Test
    fun `network failure falls back to the cached pole GPS order`() =
        runTest {
            val cached = WorkOrderDetailDto(workOrderId = "WO-1", title = "t", taskKind = "pole_gps_update", woStatus = "assigned")
            coEvery { api.detail("WO-1") } throws IOException("offline")
            coEvery { sync.cached("WO-1") } returns cached
            val result = repository.observeWorkOrderDetail("WO-1").first()
            assertEquals("WO-1", result!!.workOrderId)
        }

    @Test
    fun `network failure without a cache still surfaces the error`() =
        runTest {
            coEvery { api.detail("WO-1") } throws IOException("offline")
            coEvery { sync.cached("WO-1") } returns null
            assertFailsWith<IOException> { repository.observeWorkOrderDetail("WO-1").first() }
        }
```

(use the field names `api` and `repository` that the existing test class already uses; add `import java.io.IOException`, `kotlinx.coroutines.flow.first`, `kotlin.test.assertFailsWith` if absent.)

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*WorkOrdersApiAttachmentTest" --tests "*PoleGpsFilesTest" --tests "*AttachmentDownloaderTest" --tests "*PoleGpsDetailSyncTest" --tests "*RealWorkOrderDetailRepositoryTest"`
Expected: compilation FAIL (unresolved `downloadAttachment`, `PoleGpsFiles`, `AttachmentDownloader`, `PoleGpsDetailSync`).

- [ ] **Step 3: Implement**

`WorkOrdersApi.kt` (imports `okhttp3.ResponseBody`, `retrofit2.http.Streaming`, `retrofit2.http.Url`):

```kotlin
    @Streaming
    @GET
    suspend fun downloadAttachment(
        @Url url: String,
    ): ResponseBody
```

`PoleGpsFiles.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val UNSAFE_SEGMENT = Regex("[^A-Za-z0-9_-]")

fun sanitizeSegment(value: String): String = value.replace(UNSAFE_SEGMENT, "_")

@Singleton
class PoleGpsFiles(
    private val root: File,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "pole-gps"))

    fun fileFor(
        workOrderId: String,
        attachmentId: String,
    ): File = File(File(root, sanitizeSegment(workOrderId)), "${sanitizeSegment(attachmentId)}.pdf")

    fun deleteOrder(workOrderId: String) {
        File(root, sanitizeSegment(workOrderId)).deleteRecursively()
    }
}
```

`AttachmentDownloader.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import com.luxmap.core.network.WorkOrdersApi
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadOutcome { DONE, RETRY }

@Singleton
class AttachmentDownloader
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PoleGpsDao,
        private val files: PoleGpsFiles,
    ) {
        suspend fun downloadPending(workOrderId: String): DownloadOutcome {
            var outcome = DownloadOutcome.DONE
            dao
                .attachments(workOrderId)
                .filter { it.status == AttachmentStatusColumn.PENDING || it.status == AttachmentStatusColumn.DOWNLOADING }
                .forEach { item ->
                    if (!download(item)) outcome = DownloadOutcome.RETRY
                }
            return outcome
        }

        suspend fun retry(
            workOrderId: String,
            attachmentId: String,
        ) {
            dao.updateAttachmentStatus(workOrderId, attachmentId, AttachmentStatusColumn.PENDING, null)
        }

        private suspend fun download(item: LocalWorkOrderAttachmentEntity): Boolean {
            dao.updateAttachmentStatus(item.workOrderId, item.attachmentId, AttachmentStatusColumn.DOWNLOADING, null)
            val target = files.fileFor(item.workOrderId, item.attachmentId)
            return try {
                write(target) { api.downloadAttachment(item.downloadUrl).use { body -> body.byteStream().copyTo(it) } }
                dao.updateAttachmentStatus(item.workOrderId, item.attachmentId, AttachmentStatusColumn.READY, target.absolutePath)
                true
            } catch (e: HttpException) {
                val permanent = e.code() in 400..499
                val status = if (permanent) AttachmentStatusColumn.FAILED else AttachmentStatusColumn.PENDING
                dao.updateAttachmentStatus(item.workOrderId, item.attachmentId, status, null)
                permanent
            } catch (e: IOException) {
                dao.updateAttachmentStatus(item.workOrderId, item.attachmentId, AttachmentStatusColumn.PENDING, null)
                false
            }
        }

        private inline fun write(
            target: File,
            block: (java.io.OutputStream) -> Unit,
        ) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.part")
            try {
                temp.outputStream().use(block)
                if (!temp.renameTo(target)) throw IOException("rename failed")
            } finally {
                temp.delete()
            }
        }
    }
```

`AttachmentDownloadScheduler.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface AttachmentDownloadScheduler {
    fun schedule(workOrderId: String)
}

@Singleton
class WorkManagerAttachmentDownloadScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AttachmentDownloadScheduler {
        override fun schedule(workOrderId: String) {
            val request =
                OneTimeWorkRequestBuilder<AttachmentDownloadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(AttachmentDownloadWorker.KEY_WORK_ORDER_ID to workOrderId))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("pole-gps-pdf-$workOrderId", ExistingWorkPolicy.KEEP, request)
        }
    }
```

`AttachmentDownloadWorker.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AttachmentDownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val downloader: AttachmentDownloader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val workOrderId = inputData.getString(KEY_WORK_ORDER_ID) ?: return Result.failure()
            return when (downloader.downloadPending(workOrderId)) {
                DownloadOutcome.DONE -> Result.success()
                DownloadOutcome.RETRY -> Result.retry()
            }
        }

        companion object {
            const val KEY_WORK_ORDER_ID = "work_order_id"
        }
    }
```

`PoleGpsDetailSync.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val POLE_GPS_UPDATE_WIRE = "pole_gps_update"
private val ACTIVE_STATUSES = setOf("assigned", "in_progress")

@Singleton
class PoleGpsDetailSync
    @Inject
    constructor(
        private val dao: PoleGpsDao,
        private val scheduler: AttachmentDownloadScheduler,
        private val files: PoleGpsFiles,
    ) {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        suspend fun store(dto: WorkOrderDetailDto) {
            if (dto.taskKind != POLE_GPS_UPDATE_WIRE) return
            dao.upsertOrder(LocalPoleGpsOrderEntity(dto.workOrderId, json.encodeToString(dto), Instant.now()))
            if (dto.woStatus !in ACTIVE_STATUSES) {
                files.deleteOrder(dto.workOrderId)
                dao.deleteAttachments(dto.workOrderId)
                return
            }
            dao.replaceAttachments(
                dto.workOrderId,
                dto.attachments.map {
                    LocalWorkOrderAttachmentEntity(
                        dto.workOrderId,
                        it.attachmentId,
                        it.fileName,
                        it.byteCount,
                        it.downloadUrl,
                        AttachmentStatusColumn.PENDING,
                        null,
                    )
                },
            )
            dao.insertServerMeasurements(
                dto.poleLocations.map {
                    LocalPoleGpsMeasurementEntity(
                        dto.workOrderId,
                        it.poleId,
                        it.lat,
                        it.lng,
                        it.accuracyM,
                        OffsetDateTime.parse(it.capturedAt).toInstant(),
                        MeasurementSourceColumn.SERVER,
                    )
                },
            )
            scheduler.schedule(dto.workOrderId)
        }

        suspend fun cached(workOrderId: String): WorkOrderDetailDto? =
            dao.order(workOrderId)?.let { json.decodeFromString<WorkOrderDetailDto>(it.detailJson) }
    }
```

`PoleGpsModule.kt`:

```kotlin
package com.luxmap.di

import com.luxmap.feature.workorder.polegps.data.AttachmentDownloadScheduler
import com.luxmap.feature.workorder.polegps.data.WorkManagerAttachmentDownloadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PoleGpsModule {
    @Binds
    abstract fun bindAttachmentDownloadScheduler(impl: WorkManagerAttachmentDownloadScheduler): AttachmentDownloadScheduler
}
```

`RealWorkOrderDetailRepository.kt`: add the constructor parameter `private val poleGpsDetailSync: PoleGpsDetailSync` after `workOrdersApi`, and replace `observeWorkOrderDetail` with

```kotlin
    override fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?> =
        flow {
            val detail =
                try {
                    val dto = workOrdersApi.detail(workOrderId)
                    poleGpsDetailSync.store(dto)
                    dto.toWorkOrderDetail()
                } catch (e: HttpException) {
                    if (e.code() == 404) null else throw e
                } catch (e: IOException) {
                    poleGpsDetailSync.cached(workOrderId)?.toWorkOrderDetail() ?: throw e
                }
            emit(detail)
        }
```

(keep the existing parameter name and signature of the method; add `import java.io.IOException`.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*WorkOrdersApiAttachmentTest" --tests "*PoleGpsFilesTest" --tests "*AttachmentDownloaderTest" --tests "*PoleGpsDetailSyncTest" --tests "*RealWorkOrderDetailRepositoryTest"` then `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: PASS. (`AttachmentDownloadWorker` has no unit test: the repo has no `work-testing` dependency and none may be added; it is a 6-line pass-through covered by the downloader tests.)

- [ ] **Step 5: Commit** in two commits to stay under 400 lines

```bash
git add app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsFiles.kt app/src/main/java/com/luxmap/feature/workorder/polegps/data/AttachmentDownloader.kt app/src/test/java/com/luxmap/core/network/WorkOrdersApiAttachmentTest.kt app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsFilesTest.kt app/src/test/java/com/luxmap/feature/workorder/polegps/data/AttachmentDownloaderTest.kt
git commit -m "feat(pole-gps): download work order PDFs to private storage"
git add app/src/main app/src/test
git commit -m "feat(pole-gps): schedule PDF download and cache the order for offline"
```

### Task 5: GPS fix source and accuracy gate

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/GpsFix.kt` (`GpsFix`, `GpsFixSource`)
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/FusedGpsFixSource.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/domain/PoleFixGate.kt`
- Modify: `app/src/main/java/com/luxmap/di/PoleGpsModule.kt` (bind `GpsFixSource`)
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/domain/PoleFixGateTest.kt`

**Interfaces:**
- Consumes: `GpsAccuracyGate.PROPOSED_THRESHOLD_METERS` (`Float`, 8f, package `com.luxmap.feature.survey.domain`).
- Produces:
  - `data class GpsFix(val lat: Double, val lng: Double, val accuracyM: Double, val capturedAt: Instant)`.
  - `interface GpsFixSource { fun fixes(): Flow<GpsFix> }` (cold flow, about one fix per second, throws `SecurityException` when the permission is missing).
  - `enum class FixQuality { WAITING, WEAK, HOLDING, READY }`.
  - `class PoleFixGate(thresholdMeters: Double, holdMillis: Long)` with `fun accept(fix: GpsFix): FixQuality` and `fun reset()`.

Ruling recorded: the gate is a small pure class owned by this feature instead of reusing `PreRecordGpsAccuracyTracker`, because that tracker is wired to the survey capture session and its lifecycle. Only the 8 m constant is shared.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.workorder.polegps.domain

import com.luxmap.feature.workorder.polegps.data.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PoleFixGateTest {
    private fun fix(
        accuracy: Double,
        atMillis: Long,
    ) = GpsFix(10.0, 106.0, accuracy, Instant.ofEpochMilli(atMillis))

    private fun gate() = PoleFixGate(thresholdMeters = 8.0, holdMillis = 3000)

    @Test
    fun `an accurate fix starts the hold but is not ready yet`() {
        assertEquals(FixQuality.HOLDING, gate().accept(fix(5.0, 0)))
    }

    @Test
    fun `an accurate fix held for the full duration is ready`() {
        val gate = gate()
        gate.accept(fix(5.0, 0))
        gate.accept(fix(6.0, 1500))
        assertEquals(FixQuality.READY, gate.accept(fix(4.0, 3000)))
    }

    @Test
    fun `a weak fix resets the hold`() {
        val gate = gate()
        gate.accept(fix(5.0, 0))
        assertEquals(FixQuality.WEAK, gate.accept(fix(20.0, 1000)))
        assertEquals(FixQuality.HOLDING, gate.accept(fix(5.0, 2000)))
        assertEquals(FixQuality.HOLDING, gate.accept(fix(5.0, 4000)))
        assertEquals(FixQuality.READY, gate.accept(fix(5.0, 5000)))
    }

    @Test
    fun `accuracy exactly at the threshold counts as accurate`() {
        assertEquals(FixQuality.HOLDING, gate().accept(fix(8.0, 0)))
    }

    @Test
    fun `non finite or negative accuracy is weak`() {
        val gate = gate()
        assertEquals(FixQuality.WEAK, gate.accept(fix(Double.NaN, 0)))
        assertEquals(FixQuality.WEAK, gate.accept(fix(-1.0, 100)))
        assertEquals(FixQuality.WEAK, gate.accept(fix(Double.POSITIVE_INFINITY, 200)))
    }

    @Test
    fun `reset forgets the hold`() {
        val gate = gate()
        gate.accept(fix(5.0, 0))
        gate.reset()
        assertEquals(FixQuality.HOLDING, gate.accept(fix(5.0, 5000)))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*PoleFixGateTest"`
Expected: compilation FAIL, unresolved `PoleFixGate`, `FixQuality`, `GpsFix`.

- [ ] **Step 3: Implement**

`GpsFix.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class GpsFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
)

interface GpsFixSource {
    fun fixes(): Flow<GpsFix>
}
```

`PoleFixGate.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.domain

import com.luxmap.feature.survey.domain.GpsAccuracyGate
import com.luxmap.feature.workorder.polegps.data.GpsFix

enum class FixQuality { WAITING, WEAK, HOLDING, READY }

private const val DEFAULT_HOLD_MILLIS = 3000L

class PoleFixGate(
    private val thresholdMeters: Double = GpsAccuracyGate.PROPOSED_THRESHOLD_METERS.toDouble(),
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
) {
    private var accurateSince: Long? = null

    fun accept(fix: GpsFix): FixQuality {
        val accurate = fix.accuracyM.isFinite() && fix.accuracyM >= 0.0 && fix.accuracyM <= thresholdMeters
        if (!accurate) {
            accurateSince = null
            return FixQuality.WEAK
        }
        val now = fix.capturedAt.toEpochMilli()
        val since = accurateSince ?: now.also { accurateSince = it }
        return if (now - since >= holdMillis) FixQuality.READY else FixQuality.HOLDING
    }

    fun reset() {
        accurateSince = null
    }
}
```

`FusedGpsFixSource.kt` (compile-only verified; there is no unit seam for `FusedLocationProviderClient`, it is exercised on the device in Task 10):

```kotlin
package com.luxmap.feature.workorder.polegps.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val FIX_INTERVAL_MILLIS = 1000L

@Singleton
class FusedGpsFixSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : GpsFixSource {
        @SuppressLint("MissingPermission")
        override fun fixes(): Flow<GpsFix> =
            callbackFlow {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val request =
                    LocationRequest
                        .Builder(Priority.PRIORITY_HIGH_ACCURACY, FIX_INTERVAL_MILLIS)
                        .setMinUpdateIntervalMillis(FIX_INTERVAL_MILLIS)
                        .build()
                val callback =
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val location = result.lastLocation ?: return
                            trySend(
                                GpsFix(
                                    lat = location.latitude,
                                    lng = location.longitude,
                                    accuracyM = location.accuracy.toDouble(),
                                    capturedAt = Instant.now(),
                                ),
                            )
                        }
                    }
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                awaitClose { client.removeLocationUpdates(callback) }
            }
    }
```

`PoleGpsModule.kt`: add

```kotlin
    @Binds
    abstract fun bindGpsFixSource(impl: FusedGpsFixSource): GpsFixSource
```

(with imports `com.luxmap.feature.workorder.polegps.data.FusedGpsFixSource` and `GpsFixSource`).

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*PoleFixGateTest"` then `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: 6 tests PASS; compile OK. If `Priority`/`LocationRequest.Builder` does not resolve, check the Play Services location version already used by `LocationTracker` and use the same API style; do not add a dependency.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat(pole-gps): add GPS fix source and accuracy hold gate"
```

---

### Task 6: Pole GPS repository and measure screen

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt` (+ domain models in the same file)
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepositoryTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModelTest.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureScreenTest.kt`

**Interfaces:**
- Consumes: `PoleGpsDao`, entities, `AttachmentStatusColumn`, `MeasurementSourceColumn` (Task 3); `AttachmentDownloader`, `AttachmentDownloadScheduler`, `PoleGpsDetailSync` (Task 4); `GpsFix`, `GpsFixSource`, `PoleFixGate`, `FixQuality` (Task 5); `PoleListCacheDao.byWorkOrder(workOrderId)`; `poleNumberLabel` (Task 2).
- Produces:
  - `enum class MeasurementSource { DEVICE, SERVER }`.
  - `data class PoleMeasurement(poleId: String, lat: Double, lng: Double, accuracyM: Double, capturedAt: Instant, source: MeasurementSource)`.
  - `enum class AttachmentStatus { PENDING, DOWNLOADING, READY, FAILED }`.
  - `data class PoleGpsAttachment(attachmentId: String, fileName: String, byteCount: Long, status: AttachmentStatus, localPath: String?)`.
  - `data class PoleReference(poleId: String, position: Int?, lat: Double?, lng: Double?)`.
  - `PoleGpsRepository` with `observeMeasurements(workOrderId): Flow<List<PoleMeasurement>>`, `allMeasurements(workOrderId): List<PoleMeasurement>`, `saveDeviceMeasurement(workOrderId, poleId, fix)`, `observeAttachments(workOrderId): Flow<List<PoleGpsAttachment>>`, `attachment(workOrderId, attachmentId): PoleGpsAttachment?`, `retryAttachment(workOrderId, attachmentId)`, `poleReference(workOrderId, poleId): PoleReference?`.
  - `PoleGpsMeasureViewModel` with `state: StateFlow<PoleGpsMeasureUiState>`, `onPermissionGranted()`, `onPermissionDenied()`, `onSave()`, `onMeasureAgain()`.

- [ ] **Step 1: Write the failing tests**

`PoleGpsRepositoryTest.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PoleGpsRepositoryTest {
    private val dao = mockk<PoleGpsDao>(relaxed = true)
    private val downloader = mockk<AttachmentDownloader>(relaxed = true)
    private val scheduler = mockk<AttachmentDownloadScheduler>(relaxed = true)
    private val sync = mockk<PoleGpsDetailSync>(relaxed = true)
    private val cache = mockk<PoleListCacheDao>(relaxed = true)
    private val repository = PoleGpsRepository(dao, downloader, scheduler, sync, cache)

    private fun row(
        poleId: String,
        source: String,
    ) = LocalPoleGpsMeasurementEntity("WO-1", poleId, 1.0, 2.0, 3.0, Instant.ofEpochSecond(5), source)

    @Test
    fun `saving a device measurement stores it as DEVICE with the fix values`() =
        runTest {
            val saved = slot<LocalPoleGpsMeasurementEntity>()
            coEvery { dao.upsertDeviceMeasurement(capture(saved)) } returns Unit
            repository.saveDeviceMeasurement("WO-1", "P-1", GpsFix(10.5, 106.5, 4.0, Instant.ofEpochSecond(9)))
            assertEquals("P-1", saved.captured.poleId)
            assertEquals(MeasurementSourceColumn.DEVICE, saved.captured.source)
            assertEquals(10.5, saved.captured.lat, 0.0)
            assertEquals(4.0, saved.captured.accuracyM, 0.0)
        }

    @Test
    fun `measurements are mapped with their source`() =
        runTest {
            coEvery { dao.measurements("WO-1") } returns
                listOf(row("P-1", MeasurementSourceColumn.DEVICE), row("P-2", MeasurementSourceColumn.SERVER))
            val all = repository.allMeasurements("WO-1")
            assertEquals(listOf(MeasurementSource.DEVICE, MeasurementSource.SERVER), all.map { it.source })
        }

    @Test
    fun `observed attachments expose a typed status`() =
        runTest {
            val entity = LocalWorkOrderAttachmentEntity("WO-1", "A-1", "a.pdf", 5L, "/x", AttachmentStatusColumn.READY, "/f/a.pdf")
            coEvery { dao.observeAttachments("WO-1") } returns flowOf(listOf(entity))
            val first = repository.observeAttachments("WO-1").first().single()
            assertEquals(AttachmentStatus.READY, first.status)
            assertEquals("/f/a.pdf", first.localPath)
        }

    @Test
    fun `retry resets the file and schedules the download`() =
        runTest {
            repository.retryAttachment("WO-1", "A-1")
            coVerify { downloader.retry("WO-1", "A-1") }
            verify { scheduler.schedule("WO-1") }
        }

    @Test
    fun `pole reference comes from the cached pole list when the pole belongs to the order`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                    poleIds = listOf("P-1"),
                )
            coEvery { cache.byWorkOrder("WO-1") } returns
                listOf(LocalPoleListItemEntity("WO-1", "P-1", 4, 10.0, 106.0, "unknown"))
            val reference = repository.poleReference("WO-1", "P-1")!!
            assertEquals(4, reference.position)
            assertEquals(10.0, reference.lat!!, 0.0)
        }

    @Test
    fun `pole reference is null for a pole outside the order`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                    poleIds = listOf("P-1"),
                )
            assertNull(repository.poleReference("WO-1", "P-99"))
        }

    @Test
    fun `pole in the order with no cached row has an empty reference`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                    poleIds = listOf("P-1"),
                )
            coEvery { cache.byWorkOrder("WO-1") } returns emptyList()
            val reference = repository.poleReference("WO-1", "P-1")!!
            assertNull(reference.position)
            assertNull(reference.lat)
        }
}
```

`PoleGpsMeasureViewModelTest.kt` (uses `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before`, `resetMain()` in `@After`, same as the other ViewModel tests in the repo; `MutableSharedFlow` stands in for the GPS source):

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.domain.FixQuality
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PoleGpsMeasureViewModelTest {
    private val repository = mockk<PoleGpsRepository>(relaxed = true)
    private val fixes = MutableSharedFlow<GpsFix>(extraBufferCapacity = 16)
    private val source =
        object : GpsFixSource {
            override fun fixes(): Flow<GpsFix> = fixes
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 3, 10.0, 106.0)
        coEvery { repository.allMeasurements("WO-1") } returns emptyList()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        PoleGpsMeasureViewModel(SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")), repository, source)

    private fun fix(
        accuracy: Double,
        atMillis: Long,
    ) = GpsFix(10.1, 106.1, accuracy, Instant.ofEpochMilli(atMillis))

    @Test
    fun `starts in Loading then shows the pole once permission is granted`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals(FixQuality.WAITING, state.quality)
            assertEquals(3, state.previous?.position)
            assertFalse(state.canSave)
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
    fun `save is only enabled after the accuracy gate is ready`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(5.0, 0))
            assertFalse((vm.state.value as PoleGpsMeasureUiState.Success).canSave)
            fixes.emit(fix(5.0, 3000))
            assertTrue((vm.state.value as PoleGpsMeasureUiState.Success).canSave)
        }

    @Test
    fun `a weak fix keeps save disabled and shows the accuracy`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(25.0, 0))
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals(FixQuality.WEAK, state.quality)
            assertEquals(25.0, state.liveFix!!.accuracyM, 0.0)
            assertFalse(state.canSave)
        }

    @Test
    fun `saving stores the latest fix and reports it as saved`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(4.0, 0))
            fixes.emit(fix(4.0, 3000))
            vm.onSave()
            coVerify { repository.saveDeviceMeasurement("WO-1", "P-1", fix(4.0, 3000)) }
            assertTrue((vm.state.value as PoleGpsMeasureUiState.Success).saved)
        }

    @Test
    fun `saving without a ready gate does nothing`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(30.0, 0))
            vm.onSave()
            coVerify(exactly = 0) { repository.saveDeviceMeasurement(any(), any(), any()) }
        }

    @Test
    fun `measuring again clears the saved flag and restarts the gate`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(4.0, 0))
            fixes.emit(fix(4.0, 3000))
            vm.onSave()
            vm.onMeasureAgain()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertFalse(state.saved)
            assertFalse(state.canSave)
        }

    @Test
    fun `a previously measured pole shows its stored measurement`() =
        runTest {
            coEvery { repository.allMeasurements("WO-1") } returns
                listOf(
                    com.luxmap.feature.workorder.polegps.data.PoleMeasurement(
                        "P-1",
                        10.2,
                        106.2,
                        3.0,
                        Instant.ofEpochSecond(1),
                        com.luxmap.feature.workorder.polegps.data.MeasurementSource.DEVICE,
                    ),
                )
            val vm = viewModel()
            vm.onPermissionGranted()
            assertEquals(10.2, (vm.state.value as PoleGpsMeasureUiState.Success).stored!!.lat, 0.0)
        }
}
```

`PoleGpsMeasureScreenTest.kt` (androidTest; renders the stateless `PoleGpsMeasureContent`):

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.domain.FixQuality
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PoleGpsMeasureScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun success(
        quality: FixQuality,
        canSave: Boolean,
        accuracy: Double = 4.0,
    ) = PoleGpsMeasureUiState.Success(
        poleLabel = "Cột 3",
        previous = PoleReference("P-1", 3, 10.0, 106.0),
        liveFix = GpsFix(10.1, 106.1, accuracy, Instant.EPOCH),
        quality = quality,
        canSave = canSave,
        saved = false,
        stored = null,
    )

    private fun show(
        state: PoleGpsMeasureUiState,
        onSave: () -> Unit = {},
    ) = rule.setContent {
        LuxMapTheme {
            PoleGpsMeasureContent(state, onBack = {}, onSave = onSave, onMeasureAgain = {}, onRetryPermission = {})
        }
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
        show(PoleGpsMeasureUiState.Error("Cần cấp quyền vị trí để đo GPS"))
        rule.onNodeWithText("Cần cấp quyền vị trí để đo GPS").assertExists()
        rule.onNodeWithText("Thử lại").assertExists()
    }

    @Test
    fun save_is_disabled_while_the_fix_is_weak() {
        show(success(FixQuality.WEAK, canSave = false, accuracy = 25.0))
        rule.onNodeWithText("Lưu vị trí").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun save_is_enabled_and_fires_when_ready() {
        var saved = false
        show(success(FixQuality.READY, canSave = true), onSave = { saved = true })
        rule.onNodeWithText("Lưu vị trí").performScrollTo().assertIsEnabled().performClick()
        assertTrue(saved)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*PoleGpsRepositoryTest" --tests "*PoleGpsMeasureViewModelTest"`
Expected: compilation FAIL, unresolved `PoleGpsRepository`, `PoleGpsMeasureViewModel`, `PoleGpsMeasureUiState`.

- [ ] **Step 3: Implement**

`PoleGpsRepository.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class MeasurementSource { DEVICE, SERVER }

enum class AttachmentStatus { PENDING, DOWNLOADING, READY, FAILED }

data class PoleMeasurement(
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
    val source: MeasurementSource,
)

data class PoleGpsAttachment(
    val attachmentId: String,
    val fileName: String,
    val byteCount: Long,
    val status: AttachmentStatus,
    val localPath: String?,
)

data class PoleReference(
    val poleId: String,
    val position: Int?,
    val lat: Double?,
    val lng: Double?,
)

private fun LocalPoleGpsMeasurementEntity.toDomain() =
    PoleMeasurement(
        poleId = poleId,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        capturedAt = capturedAt,
        source = if (source == MeasurementSourceColumn.DEVICE) MeasurementSource.DEVICE else MeasurementSource.SERVER,
    )

private fun LocalWorkOrderAttachmentEntity.toDomain() =
    PoleGpsAttachment(
        attachmentId = attachmentId,
        fileName = fileName,
        byteCount = byteCount,
        status =
            when (status) {
                AttachmentStatusColumn.READY -> AttachmentStatus.READY
                AttachmentStatusColumn.DOWNLOADING -> AttachmentStatus.DOWNLOADING
                AttachmentStatusColumn.FAILED -> AttachmentStatus.FAILED
                else -> AttachmentStatus.PENDING
            },
        localPath = localPath,
    )

@Singleton
class PoleGpsRepository
    @Inject
    constructor(
        private val dao: PoleGpsDao,
        private val downloader: AttachmentDownloader,
        private val scheduler: AttachmentDownloadScheduler,
        private val detailSync: PoleGpsDetailSync,
        private val poleCache: PoleListCacheDao,
    ) {
        fun observeMeasurements(workOrderId: String): Flow<List<PoleMeasurement>> =
            dao.observeMeasurements(workOrderId).map { rows -> rows.map { it.toDomain() } }

        suspend fun allMeasurements(workOrderId: String): List<PoleMeasurement> =
            dao.measurements(workOrderId).map { it.toDomain() }

        suspend fun saveDeviceMeasurement(
            workOrderId: String,
            poleId: String,
            fix: GpsFix,
        ) {
            dao.upsertDeviceMeasurement(
                LocalPoleGpsMeasurementEntity(
                    workOrderId = workOrderId,
                    poleId = poleId,
                    lat = fix.lat,
                    lng = fix.lng,
                    accuracyM = fix.accuracyM,
                    capturedAt = fix.capturedAt,
                    source = MeasurementSourceColumn.DEVICE,
                ),
            )
        }

        fun observeAttachments(workOrderId: String): Flow<List<PoleGpsAttachment>> =
            dao.observeAttachments(workOrderId).map { rows -> rows.map { it.toDomain() } }

        suspend fun attachment(
            workOrderId: String,
            attachmentId: String,
        ): PoleGpsAttachment? =
            dao.attachments(workOrderId).firstOrNull { it.attachmentId == attachmentId }?.toDomain()

        suspend fun retryAttachment(
            workOrderId: String,
            attachmentId: String,
        ) {
            downloader.retry(workOrderId, attachmentId)
            scheduler.schedule(workOrderId)
        }

        suspend fun poleReference(
            workOrderId: String,
            poleId: String,
        ): PoleReference? {
            val order = detailSync.cached(workOrderId) ?: return null
            if (poleId !in order.poleIds) return null
            val row = poleCache.byWorkOrder(workOrderId).firstOrNull { it.poleId == poleId }
            return PoleReference(poleId, row?.position, row?.lat, row?.lng)
        }
    }
```

`PoleGpsMeasureUiState.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.domain.FixQuality

sealed interface PoleGpsMeasureUiState {
    data object Loading : PoleGpsMeasureUiState

    data class Success(
        val poleLabel: String,
        val previous: PoleReference?,
        val liveFix: GpsFix?,
        val quality: FixQuality,
        val canSave: Boolean,
        val saved: Boolean,
        val stored: PoleMeasurement?,
    ) : PoleGpsMeasureUiState

    data object Empty : PoleGpsMeasureUiState

    data class Error(
        val message: String,
    ) : PoleGpsMeasureUiState
}
```

`PoleGpsMeasureViewModel.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.ui.polephoto.poleNumberLabel
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.domain.FixQuality
import com.luxmap.feature.workorder.polegps.domain.PoleFixGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
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
        private val gate = PoleFixGate()
        private val _state = MutableStateFlow<PoleGpsMeasureUiState>(PoleGpsMeasureUiState.Loading)
        val state: StateFlow<PoleGpsMeasureUiState> = _state
        private var collecting: Job? = null
        private var latestFix: GpsFix? = null

        fun onPermissionGranted() {
            collecting?.cancel()
            collecting =
                viewModelScope.launch {
                    val reference = repository.poleReference(workOrderId, poleId)
                    if (reference == null) {
                        _state.value = PoleGpsMeasureUiState.Empty
                        return@launch
                    }
                    val stored = repository.allMeasurements(workOrderId).firstOrNull { it.poleId == poleId }
                    _state.value =
                        PoleGpsMeasureUiState.Success(
                            poleLabel = poleNumberLabel(reference.position),
                            previous = reference,
                            liveFix = null,
                            quality = FixQuality.WAITING,
                            canSave = false,
                            saved = false,
                            stored = stored,
                        )
                    fixSource
                        .fixes()
                        .catch { _state.value = PoleGpsMeasureUiState.Error("Không đọc được GPS. Hãy bật định vị và thử lại.") }
                        .collect { onFix(it) }
                }
        }

        fun onPermissionDenied() {
            collecting?.cancel()
            _state.value = PoleGpsMeasureUiState.Error("Cần cấp quyền vị trí để đo GPS cột đèn.")
        }

        fun onSave() {
            val current = _state.value as? PoleGpsMeasureUiState.Success ?: return
            val fix = latestFix
            if (!current.canSave || fix == null) return
            viewModelScope.launch {
                repository.saveDeviceMeasurement(workOrderId, poleId, fix)
                _state.value =
                    current.copy(
                        saved = true,
                        stored = fix.toMeasurement(),
                    )
            }
        }

        fun onMeasureAgain() {
            val current = _state.value as? PoleGpsMeasureUiState.Success ?: return
            gate.reset()
            latestFix = null
            _state.value = current.copy(liveFix = null, quality = FixQuality.WAITING, canSave = false, saved = false)
        }

        private fun onFix(fix: GpsFix) {
            val current = _state.value as? PoleGpsMeasureUiState.Success ?: return
            if (current.saved) return
            latestFix = fix
            val quality = gate.accept(fix)
            _state.value = current.copy(liveFix = fix, quality = quality, canSave = quality == FixQuality.READY)
        }

        private fun GpsFix.toMeasurement() =
            PoleMeasurement(
                poleId = poleId,
                lat = lat,
                lng = lng,
                accuracyM = accuracyM,
                capturedAt = capturedAt,
                source = com.luxmap.feature.workorder.polegps.data.MeasurementSource.DEVICE,
            )
    }
```

`poleNumberLabel` is the top-level function Task 2 adds to `feature/survey/ui/polephoto/PoleListUiState.kt`.

`PoleGpsMeasureScreen.kt`: build the screen from the existing Design System v2 pieces (navy hero header and bordered cards used by `PoleListScreen`; reuse its hero composable if one is exported, otherwise a `Column` with `MaterialTheme.colorScheme` roles; no new colour token). Required structure:

```kotlin
@Composable
fun PoleGpsMeasureRoute(
    onBack: () -> Unit,
    viewModel: PoleGpsMeasureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
        }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onPermissionGranted() else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    PoleGpsMeasureContent(
        state = state,
        onBack = onBack,
        onSave = viewModel::onSave,
        onMeasureAgain = viewModel::onMeasureAgain,
        onRetryPermission = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    )
}

@Composable
fun PoleGpsMeasureContent(
    state: PoleGpsMeasureUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMeasureAgain: () -> Unit,
    onRetryPermission: () -> Unit,
) {
    when (state) {
        PoleGpsMeasureUiState.Loading -> CenteredMessage("Đang chuẩn bị đo GPS...", showProgress = true)
        PoleGpsMeasureUiState.Empty -> CenteredMessage("Cột này không thuộc lệnh này.", actionLabel = "Quay lại", onAction = onBack)
        is PoleGpsMeasureUiState.Error -> CenteredMessage(state.message, actionLabel = "Thử lại", onAction = onRetryPermission)
        is PoleGpsMeasureUiState.Success -> MeasureBody(state, onSave, onMeasureAgain)
    }
}
```

`MeasureBody` is a vertically scrollable `Column` with: pole label; a card "Vị trí GIS hiện tại" (lat/lng with 6 decimals, or "Chưa có"); a card "Vị trí đo được" (live lat/lng with 6 decimals, accuracy "±X,X m", and a status line chosen by `quality`: WAITING "Đang chờ tín hiệu GPS", WEAK "Sai số cao hơn 8 m. Di chuyển ra nơi thoáng hơn", HOLDING "Giữ yên thiết bị...", READY "Đủ độ chính xác"); a full-width 48dp `Button` "Lưu vị trí" with `enabled = state.canSave && !state.saved`; when `state.saved` a confirmation row "Đã lưu vị trí cột" and an `OutlinedButton` "Đo lại". `CenteredMessage` is a private composable in the same file (text, optional progress, optional 48dp button). Use only `MaterialTheme.colorScheme` roles and the typography scale. The 8 m figure in the WEAK text must be built from `GpsAccuracyGate.PROPOSED_THRESHOLD_METERS` (`"%.0f".format(...)`), not typed as a literal.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*PoleGpsRepositoryTest" --tests "*PoleGpsMeasureViewModelTest"` then `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`; with the device SM-A075F: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.polegps.ui.measure.PoleGpsMeasureScreenTest`
Expected: 7 + 9 unit tests PASS; 5 UI tests PASS on the device.

- [ ] **Step 5: Commit** in two commits

```bash
git add app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepositoryTest.kt
git commit -m "feat(pole-gps): add pole GPS repository over Room"
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat(pole-gps): add per-pole GPS measure screen"
```

<!--NEXT-->
