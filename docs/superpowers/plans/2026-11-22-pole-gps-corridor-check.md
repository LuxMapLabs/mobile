# Pole GPS Corridor Check & Resubmit Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `pole_gps_update` flow on mobile match BE/Web: resend previously reported coordinates after a return, and warn the Field Engineer (without blocking) when a measured position is more than 25 m from the pole's road segment.

**Architecture:** The order's segment geometry (`GET /assets/segments/{segment_id}` → `geom_wkt`) is fetched when the order is opened and cached in a new Room table so the check works offline. A pure-Kotlin function computes point-to-polyline distance; the repository attaches a `CorridorCheck` to every `PoleMeasurement`, so the measure screen, the pole list and the submit sheet all read the same result. The resubmit fix is a change inside `PoleGpsRepository.snapshotCompletion`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Retrofit + kotlinx.serialization, Hilt, JUnit + MockK + Turbine, Room in-memory instrumented tests.

**Spec:** `docs/superpowers/specs/2026-11-22-pole-gps-update-business-flow.md` (section 9 is the agreed scope; sections 2 and 7 give the reasons).

## Global Constraints

- Corridor threshold: `25.0` m, distance from the **new position to the road segment line**, never to the old position (BE PL-3). Mobile **warns and asks**, never blocks.
- Distance `<= 25.0` is inside; `> 25.0` is outside. Segment not drawn, not cached, or unparseable WKT → `Unknown`, save normally.
- Do not change any API JSON. Do not call `PUT /assets/poles/{id}/location`. No new library.
- `pole_locations` on resubmit carries **every** measured pole of the order: device-measured poles with a new photo, server-reported poles with their old coordinates and no new photo. At least one device-measured pole is still required to submit.
- GPS accuracy is shown only on the measure screen; remove it from the pole list. Keep the existing `> 20 m` low-accuracy confirmation unchanged.
- No code comments in new or changed code (owner preference). UI strings in Vietnamese, code/commits in English.
- Each commit ≤ 400 changed lines. Commit format `type(pole-gps): description`.
- Work in a separate worktree from `origin/dev` (the main checkout has unrelated uncommitted survey changes). Branch `feat/pole-gps-corridor-check`, PR into `dev`.
- After each task: stop, summarise to the owner, wait for "ok/tiếp tục" before the next task (CLAUDE.md "Làm theo từng bước nhỏ").
- Run only the tests named in each task. Never run the full Gradle suite without asking. Instrumented tests run on the real device (SM-A075F); the emulator fails every instrumented test for an unrelated reason.
- Run `./gradlew ktlintCheck` before every commit.
- Room version: `dev` is at 25 when this plan was written. If `AppDatabase.version` on `origin/dev` is different when you start, use `N → N+1` everywhere this plan says `25 → 26`.

## Review Focus

1. **Engineer reads the "outside corridor" dialog for more than 10 s** → the GPS sample is then stale; expected: nothing is saved with the stale sample and the "Chưa lấy được vị trí" dialog appears (same as the existing low-accuracy dialog). Test added in Task 5.
2. **Pole beyond the end of the segment** → distance must be to the nearest endpoint, not to the infinite line. Test added in Task 2.
3. **Odd WKT** (`null`, `POINT`, `LINESTRING EMPTY`, single-vertex line, garbage) → `Unknown`, never a crash. Test added in Task 2.
4. **Server-reported pole re-measured on the device** → exactly one entry for that pole in `pole_locations`, with the new coordinates. Test added in Task 1.
5. **Old position missing or `(0.0, 0.0)`** → no "Lệch vị trí cũ" line. Test added in Task 5.

---

### Task 0: Workspace

- [ ] **Step 1: Create the worktree from `origin/dev`**

```bash
cd D:/ki9/SEP490/App/mobile
git fetch origin
git worktree add ../mobile-pole-gps -b feat/pole-gps-corridor-check origin/dev
cd ../mobile-pole-gps
```

- [ ] **Step 2: Confirm the starting point**

Run: `grep -n "version =" app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
Expected: `version = 25,` (otherwise apply the Room-version rule in Global Constraints).

Copy `local.properties` from the main checkout if the build needs it: `cp ../mobile/local.properties .`

---

### Task 1: Resend previously reported coordinates after a return

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt` (`snapshotCompletion`, `legacyCompletionPayload`)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/sections/PoleGpsSection.kt` (server-measurement notice)
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/data/PoleGpsSubmissionTest.kt`

**Interfaces:**
- Consumes: existing `PoleGpsDao.measurements`, `insertServerMeasurements`, `MeasurementSourceColumn`.
- Produces: `snapshotCompletion(workOrderId: String, resetRetryableUploads: Boolean = false)` — the `includeServerMeasurements` parameter is removed.

- [ ] **Step 1: Replace the old "server poles are dropped" test and add two new ones**

In `PoleGpsSubmissionTest.kt`, delete the test `partial_rework_does_not_require_local_photos_for_previous_server_measurements` and add:

```kotlin
    @Test
    fun rework_resends_previous_server_coordinates_without_new_photos() =
        runTest {
            db.poleGpsDao().insertServerMeasurements(
                listOf(
                    LocalPoleGpsMeasurementEntity("WO-1", "P-1", 10.0, 106.0, 3.0, at, MeasurementSourceColumn.SERVER),
                ),
            )
            measure(poleId = "P-2", lat = 11.0)
            assertTrue(gps.stageCompletion(report))
            val submitted = payload()
            assertEquals(setOf("P-1", "P-2"), submitted.poleLocations!!.map { it.poleId }.toSet())
            assertEquals(10.0, submitted.poleLocations!!.single { it.poleId == "P-1" }.lat, 0.0)
            assertEquals(1, submitted.evidenceClientOpIds!!.size)
        }

    @Test
    fun remeasured_server_pole_is_sent_once_with_its_new_coordinates() =
        runTest {
            db.poleGpsDao().insertServerMeasurements(
                listOf(
                    LocalPoleGpsMeasurementEntity("WO-1", "P-1", 10.0, 106.0, 3.0, at, MeasurementSourceColumn.SERVER),
                ),
            )
            measure(poleId = "P-1", lat = 11.0)
            assertTrue(gps.stageCompletion(report))
            val location = payload().poleLocations!!.single()
            assertEquals("P-1", location.poleId)
            assertEquals(11.0, location.lat, 0.0)
        }

    @Test
    fun report_with_only_server_coordinates_is_refused_and_nothing_is_queued() =
        runTest {
            db.poleGpsDao().insertServerMeasurements(
                listOf(
                    LocalPoleGpsMeasurementEntity("WO-1", "P-1", 10.0, 106.0, 3.0, at, MeasurementSourceColumn.SERVER),
                ),
            )
            val error = runCatching { gps.stageCompletion(report) }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertTrue(db.syncQueueDao().queuedRows().isEmpty())
            assertNull(db.workOrderCompletionDao().completionByWorkOrderId("WO-1"))
        }
```

- [ ] **Step 2: Run them on the device and confirm the first one fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.polegps.data.PoleGpsSubmissionTest`
Expected: `rework_resends_previous_server_coordinates_without_new_photos` FAILS (`P-1` missing from `poleLocations`). The other two may already pass.

- [ ] **Step 3: Change `snapshotCompletion`**

In `PoleGpsRepository.kt`, replace the signature and the start of `snapshotCompletion` up to (and including) the line `measurements.map { measurement ->` and the coordinate `check(...)` inside it, so the function reads:

```kotlin
        private suspend fun snapshotCompletion(
            workOrderId: String,
            resetRetryableUploads: Boolean = false,
        ): CompletionSyncPayload {
            val order = checkNotNull(detailSync.cached(workOrderId)) { "Chưa có chi tiết lệnh GPS trên máy." }
            check(order.taskKind == "pole_gps_update")
            val measurements = dao.measurements(workOrderId).filter { it.poleId in order.poleIds }
            check(measurements.any { it.source == MeasurementSourceColumn.DEVICE }) {
                "Cần đo ít nhất một cột trước khi nộp báo cáo."
            }
            measurements.forEach { measurement ->
                check(
                    measurement.lat.isFinite() && measurement.lat in -90.0..90.0 &&
                        measurement.lng.isFinite() && measurement.lng in -180.0..180.0 &&
                        measurement.accuracyM.isFinite() && measurement.accuracyM >= 0,
                ) { "Tọa độ cột không hợp lệ. Hãy đo lại." }
            }
            val evidence = completionDao.evidenceForCompletion(workOrderId, "observation")
            val operations = mutableListOf<SyncQueueEntity>()
            val ids =
                measurements.filter { it.source == MeasurementSourceColumn.DEVICE }.map { measurement ->
                    val matching =
```

Everything after `val matching =` (photo matching, queue rows, `linkEvidence`, the returned `CompletionSyncPayload` built from `measurements`) stays exactly as it is. The returned `poleLocations` already maps over `measurements`, which now includes server rows.

- [ ] **Step 4: Update the legacy caller**

In `legacyCompletionPayload`, change

```kotlin
                val payload = snapshotCompletion(completion.workOrderId, includeServerMeasurements = true)
```

to

```kotlin
                val payload = snapshotCompletion(completion.workOrderId)
```

- [ ] **Step 5: Replace the outdated notice in the pole list**

In `PoleGpsSection.kt`, replace the text

```kotlin
                    "Báo cáo mới chỉ gửi các cột đã chụp ảnh trên thiết bị này. " +
                        "Cột đã báo trước cần chụp lại để gửi lại.",
```

with

```kotlin
                    "Cột đã báo trước sẽ được gửi lại với vị trí cũ. " +
                        "Chỉ cần đo lại cột Quản lý yêu cầu.",
```

- [ ] **Step 6: Re-run the instrumented tests**

Run: same command as Step 2.
Expected: all `PoleGpsSubmissionTest` tests PASS (the legacy test `legacy_submission_is_upgraded_durably_and_waits_for_photos` included).

- [ ] **Step 7: Run the repository unit tests**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.polegps.data.PoleGpsRepositoryTest"`
Expected: PASS.

- [ ] **Step 8: ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt \
  app/src/main/java/com/luxmap/feature/workorder/ui/detail/sections/PoleGpsSection.kt \
  app/src/androidTest/java/com/luxmap/feature/workorder/polegps/data/PoleGpsSubmissionTest.kt
git commit -m "fix(pole-gps): resend previously reported coordinates on resubmit"
```

---

### Task 2: Corridor geometry (pure Kotlin)

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/RouteCorridor.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/data/RouteCorridorTest.kt`

**Interfaces:**
- Produces:
  - `const val ROUTE_CORRIDOR_METRES: Double = 25.0`
  - `data class RoutePoint(val lat: Double, val lng: Double)`
  - `sealed interface CorridorCheck { data class Inside(val distanceM: Double); data class Outside(val distanceM: Double); data object Unknown }`
  - `fun parseRouteWkt(wkt: String?): List<List<RoutePoint>>?`
  - `fun distanceToRouteMetres(lat: Double, lng: Double, lines: List<List<RoutePoint>>): Double`
  - `fun corridorCheck(geomWkt: String?, lat: Double, lng: Double): CorridorCheck`
  - `fun distanceMetres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.luxmap.feature.workorder.polegps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCorridorTest {
    private val line = "LINESTRING (106 10, 106.001 10)"
    private val metresPerDegreeLat = 111_195.08

    private fun north(metres: Double) = 10.0 + metres / metresPerDegreeLat

    @Test
    fun `a point on the line is zero metres away`() {
        assertEquals(0.0, distanceToRouteMetres(10.0, 106.0005, parseRouteWkt(line)!!), 0.01)
    }

    @Test
    fun `distance to a straight segment is the perpendicular distance`() {
        assertEquals(20.0, distanceToRouteMetres(north(20.0), 106.0005, parseRouteWkt(line)!!), 0.05)
    }

    @Test
    fun `a point past the end of the segment is measured to the endpoint`() {
        assertEquals(109.5, distanceToRouteMetres(10.0, 105.999, parseRouteWkt(line)!!), 0.5)
    }

    @Test
    fun `just inside and just outside the corridor`() {
        assertTrue(corridorCheck(line, north(24.99), 106.0005) is CorridorCheck.Inside)
        assertTrue(corridorCheck(line, north(25.01), 106.0005) is CorridorCheck.Outside)
    }

    @Test
    fun `multilinestring uses the nearest part`() {
        val wkt = "MULTILINESTRING ((106 10, 106.001 10), (106 10.01, 106.001 10.01))"
        assertEquals(10.0, distanceToRouteMetres(north(10.0), 106.0005, parseRouteWkt(wkt)!!), 0.05)
    }

    @Test
    fun `keyword without a space is accepted`() {
        assertEquals(1, parseRouteWkt("LINESTRING(106 10,106.001 10)")!!.size)
    }

    @Test
    fun `unusable geometry is Unknown, never a crash`() {
        listOf(null, "", "POINT (106 10)", "LINESTRING EMPTY", "LINESTRING (106 10)", "LINESTRING (a b, c d)")
            .forEach { wkt ->
                assertNull(parseRouteWkt(wkt))
                assertEquals(CorridorCheck.Unknown, corridorCheck(wkt, 10.0, 106.0))
            }
    }

    @Test
    fun `distance between two points`() {
        assertEquals(20.0, distanceMetres(10.0, 106.0, north(20.0), 106.0), 0.05)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.polegps.data.RouteCorridorTest"`
Expected: FAIL, unresolved references (`parseRouteWkt`, `corridorCheck`…).

- [ ] **Step 3: Implement**

```kotlin
package com.luxmap.feature.workorder.polegps.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

const val ROUTE_CORRIDOR_METRES = 25.0
private const val EARTH_RADIUS_METRES = 6_371_008.8
private const val METRES_PER_DEGREE = EARTH_RADIUS_METRES * PI / 180.0

data class RoutePoint(val lat: Double, val lng: Double)

sealed interface CorridorCheck {
    data class Inside(val distanceM: Double) : CorridorCheck

    data class Outside(val distanceM: Double) : CorridorCheck

    data object Unknown : CorridorCheck
}

fun parseRouteWkt(wkt: String?): List<List<RoutePoint>>? {
    val text = wkt?.trim().orEmpty()
    if (!text.contains("(")) return null
    val upper = text.uppercase()
    val inner = text.substringAfter("(").substringBeforeLast(")")
    val body =
        when {
            upper.startsWith("MULTILINESTRING") -> inner
            upper.startsWith("LINESTRING") -> "($inner)"
            else -> return null
        }
    val lines =
        Regex("\\(([^()]*)\\)").findAll(body).toList().map { match ->
            match.groupValues[1].split(",").map { pair ->
                val parts = pair.trim().split(Regex("\\s+"))
                val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
                val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
                RoutePoint(lat, lng)
            }
        }
    if (lines.isEmpty() || lines.any { it.size < 2 }) return null
    return lines
}

fun distanceToRouteMetres(
    lat: Double,
    lng: Double,
    lines: List<List<RoutePoint>>,
): Double {
    val metresPerDegreeLng = METRES_PER_DEGREE * cos(lat * PI / 180.0)
    fun x(point: RoutePoint) = (point.lng - lng) * metresPerDegreeLng

    fun y(point: RoutePoint) = (point.lat - lat) * METRES_PER_DEGREE
    return lines.minOf { line ->
        line.zipWithNext().minOf { (a, b) -> distanceFromOrigin(x(a), y(a), x(b), y(b)) }
    }
}

private fun distanceFromOrigin(
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy)
}

fun corridorCheck(
    geomWkt: String?,
    lat: Double,
    lng: Double,
): CorridorCheck {
    val lines = parseRouteWkt(geomWkt) ?: return CorridorCheck.Unknown
    val distance = distanceToRouteMetres(lat, lng, lines)
    return if (distance <= ROUTE_CORRIDOR_METRES) CorridorCheck.Inside(distance) else CorridorCheck.Outside(distance)
}

fun distanceMetres(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double,
): Double {
    val metresPerDegreeLng = METRES_PER_DEGREE * cos(lat1 * PI / 180.0)
    return hypot((lng2 - lng1) * metresPerDegreeLng, (lat2 - lat1) * METRES_PER_DEGREE)
}
```

- [ ] **Step 4: Run to verify pass**

Run: same as Step 2. Expected: PASS (8 tests).

- [ ] **Step 5: ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/polegps/data/RouteCorridor.kt \
  app/src/test/java/com/luxmap/feature/workorder/polegps/data/RouteCorridorTest.kt
git commit -m "feat(pole-gps): add route corridor distance check"
```

---

### Task 3: Fetch and cache the segment geometry

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt` (add `segmentId`)
- Create: `app/src/main/java/com/luxmap/core/network/AssetsApi.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/SegmentDetailDto.kt`
- Modify: `app/src/main/java/com/luxmap/di/NetworkModule.kt` (provide `AssetsApi`)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsEntities.kt` (new entity)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsDao.kt` (route queries)
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt` (entity, version 26)
- Modify: `app/src/main/java/com/luxmap/core/database/AppMigrations.kt` (`MIGRATION_25_26`)
- Modify: `app/src/main/java/com/luxmap/di/DatabaseModule.kt` (register migration)
- Test: `app/src/androidTest/java/com/luxmap/core/database/Migration25To26Test.kt`

**Interfaces:**
- Produces:
  - `WorkOrderDetailDto.segmentId: String?` (`@SerialName("segment_id")`, default `null`)
  - `interface AssetsApi { suspend fun segment(segmentId: String): SegmentDetailDto }`
  - `data class SegmentDetailDto(val geomWkt: String? = null)` (`@SerialName("geom_wkt")`)
  - `LocalPoleGpsRouteEntity(workOrderId: String, segmentId: String, geomWkt: String?, fetchedAt: Instant)`, table `local_pole_gps_route`
  - `PoleGpsDao.upsertRoute(entity)`, `PoleGpsDao.route(workOrderId): LocalPoleGpsRouteEntity?`, `PoleGpsDao.observeRoute(workOrderId): Flow<LocalPoleGpsRouteEntity?>`
  - `MIGRATION_25_26`

- [ ] **Step 1: Write the failing migration test**

```kotlin
package com.luxmap.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.feature.workorder.polegps.data.LocalPoleGpsRouteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class Migration25To26Test {
    @Test
    fun upgrade_keeps_measurements_and_adds_the_route_cache() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-25-26-test.db"
            context.deleteDatabase(name)
            val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            before.openHelper.writableDatabase.execSQL(
                "INSERT INTO local_pole_gps_measurement VALUES " +
                    "('W', 'P', 10.0, 106.0, 5.0, 1000, 'device', '/photo.jpg', 'E', 'queued')",
            )
            before.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE local_pole_gps_route")
                it.version = 25
            }
            val after =
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(MIGRATION_25_26)
                    .build()
            val dao = after.poleGpsDao()
            assertEquals(1, dao.measurements("W").size)
            dao.upsertRoute(LocalPoleGpsRouteEntity("W", "SEG-1", "LINESTRING (106 10, 106.001 10)", Instant.EPOCH))
            assertEquals("SEG-1", dao.route("W")!!.segmentId)
            after.close()
            context.deleteDatabase(name)
        }
}
```

- [ ] **Step 2: Add the entity**

Append to `PoleGpsEntities.kt`:

```kotlin
@Entity(tableName = "local_pole_gps_route")
data class LocalPoleGpsRouteEntity(
    @PrimaryKey val workOrderId: String,
    val segmentId: String,
    val geomWkt: String?,
    val fetchedAt: Instant,
)
```

- [ ] **Step 3: Add the DAO methods**

In `PoleGpsDao.kt`, add:

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRoute(entity: LocalPoleGpsRouteEntity)

    @Query("SELECT * FROM local_pole_gps_route WHERE workOrderId = :workOrderId")
    abstract suspend fun route(workOrderId: String): LocalPoleGpsRouteEntity?

    @Query("SELECT * FROM local_pole_gps_route WHERE workOrderId = :workOrderId")
    abstract fun observeRoute(workOrderId: String): Flow<LocalPoleGpsRouteEntity?>
```

- [ ] **Step 4: Register the entity, bump the version, add the migration**

In `AppDatabase.kt`: add `LocalPoleGpsRouteEntity::class,` to `entities` (next to `LocalPoleGpsMeasurementEntity::class`), add its import, change `version = 25,` to `version = 26,`.

Append to `AppMigrations.kt`:

```kotlin
val MIGRATION_25_26: Migration =
    object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_pole_gps_route (workOrderId TEXT NOT NULL, " +
                    "segmentId TEXT NOT NULL, geomWkt TEXT, fetchedAt INTEGER NOT NULL, PRIMARY KEY(workOrderId))",
            )
        }
    }
```

In `DatabaseModule.kt`, add `import com.luxmap.core.database.MIGRATION_25_26` and append `MIGRATION_25_26` to the existing `.addMigrations(...)` list.

- [ ] **Step 5: Run the migration test on the device**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.core.database.Migration25To26Test`
Expected: PASS. Then also run `-Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.polegps.data.PoleGpsDaoTest`, expected PASS.

- [ ] **Step 6: Add `segment_id` to the detail DTO**

In `WorkOrderDetailDto`, after `@SerialName("pole_ids") val poleIds: List<String> = emptyList(),` add:

```kotlin
    @SerialName("segment_id") val segmentId: String? = null,
```

- [ ] **Step 7: Add the API and DTO**

`app/src/main/java/com/luxmap/feature/workorder/polegps/data/SegmentDetailDto.kt`:

```kotlin
package com.luxmap.feature.workorder.polegps.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SegmentDetailDto(
    @SerialName("geom_wkt") val geomWkt: String? = null,
)
```

`app/src/main/java/com/luxmap/core/network/AssetsApi.kt`:

```kotlin
package com.luxmap.core.network

import com.luxmap.feature.workorder.polegps.data.SegmentDetailDto
import retrofit2.http.GET
import retrofit2.http.Path

interface AssetsApi {
    @GET("api/v1/assets/segments/{segment_id}")
    suspend fun segment(
        @Path("segment_id") segmentId: String,
    ): SegmentDetailDto
}
```

In `NetworkModule.kt`, next to `provideWorkOrdersApi`, add (same annotations as the neighbouring providers):

```kotlin
    @Provides
    @Singleton
    fun provideAssetsApi(retrofit: Retrofit): AssetsApi = retrofit.create(AssetsApi::class.java)
```

and `import com.luxmap.core.network.AssetsApi`.

- [ ] **Step 8: Compile and run the detail-sync unit test**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.polegps.data.PoleGpsDetailSyncTest"`
Expected: PASS (the new DTO field has a default).

- [ ] **Step 9: ktlint and commit**

```bash
./gradlew ktlintCheck
git add -A app/src/main/java/com/luxmap/core app/src/main/java/com/luxmap/di \
  app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt \
  app/src/main/java/com/luxmap/feature/workorder/polegps/data \
  app/src/androidTest/java/com/luxmap/core/database/Migration25To26Test.kt
git commit -m "feat(pole-gps): cache the order segment geometry"
```

Check `git diff --cached --stat` first: only the files listed in this task should be staged.

---

### Task 4: Repository — refresh the route and attach the corridor check

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt` (call `refreshRoute`)
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepositoryTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModelTest.kt`
- Test (constructor only): `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/data/PoleGpsSubmissionTest.kt`

**Interfaces:**
- Consumes: Task 2 `corridorCheck`, `CorridorCheck`; Task 3 `AssetsApi`, `SegmentDetailDto`, `LocalPoleGpsRouteEntity`, `PoleGpsDao.upsertRoute/route/observeRoute`, `WorkOrderDetailDto.segmentId`.
- Produces:
  - `PoleMeasurement.corridor: CorridorCheck` (last constructor parameter, default `CorridorCheck.Unknown`)
  - `PoleGpsRepository(…, syncQueueManager: SyncQueueManager, assetsApi: AssetsApi)` — new last constructor parameter
  - `suspend fun PoleGpsRepository.refreshRoute(workOrderId: String)`
  - `suspend fun PoleGpsRepository.routeGeometry(workOrderId: String): String?`

- [ ] **Step 1: Write the failing repository tests**

In `PoleGpsRepositoryTest.kt`:
- add `private val assetsApi = mockk<AssetsApi>()` and pass it as the new last argument of `PoleGpsRepository(...)`;
- in `setUp()` add `every { dao.observeRoute(any()) } returns flowOf(null)` and `coEvery { dao.route(any()) } returns null`;
- add imports `com.luxmap.core.network.AssetsApi`, `com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto`, `io.mockk.every`, `java.io.IOException`;
- add the tests:

```kotlin
    private val route = "LINESTRING (106 10, 106.001 10)"

    @Test
    fun `refreshRoute stores the order segment geometry`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                WorkOrderDetailDto("WO-1", "GPS", "pole_gps_update", "in_progress", segmentId = "SEG-1")
            coEvery { assetsApi.segment("SEG-1") } returns SegmentDetailDto(route)
            val saved = slot<LocalPoleGpsRouteEntity>()
            coEvery { dao.upsertRoute(capture(saved)) } returns Unit
            repository.refreshRoute("WO-1")
            assertEquals("SEG-1", saved.captured.segmentId)
            assertEquals(route, saved.captured.geomWkt)
        }

    @Test
    fun `refreshRoute keeps the cached route when offline`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                WorkOrderDetailDto("WO-1", "GPS", "pole_gps_update", "in_progress", segmentId = "SEG-1")
            coEvery { assetsApi.segment("SEG-1") } throws IOException("offline")
            repository.refreshRoute("WO-1")
            coVerify(exactly = 0) { dao.upsertRoute(any()) }
        }

    @Test
    fun `refreshRoute does nothing for an order without a segment`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                WorkOrderDetailDto("WO-1", "GPS", "pole_gps_update", "in_progress")
            repository.refreshRoute("WO-1")
            coVerify(exactly = 0) { assetsApi.segment(any()) }
        }

    @Test
    fun `observed measurements carry their corridor check`() =
        runTest {
            every { dao.observeMeasurements("WO-1") } returns
                flowOf(
                    listOf(
                        LocalPoleGpsMeasurementEntity(
                            "WO-1", "P-1", 10.0005, 106.0005, 3.0, Instant.EPOCH, MeasurementSourceColumn.DEVICE,
                        ),
                        LocalPoleGpsMeasurementEntity(
                            "WO-1", "P-2", 10.0001, 106.0005, 3.0, Instant.EPOCH, MeasurementSourceColumn.SERVER,
                        ),
                    ),
                )
            every { dao.observeRoute("WO-1") } returns
                flowOf(LocalPoleGpsRouteEntity("WO-1", "SEG-1", route, Instant.EPOCH))
            val measured = repository.observeMeasurements("WO-1").first()
            assertTrue(measured[0].corridor is CorridorCheck.Outside)
            assertTrue(measured[1].corridor is CorridorCheck.Inside)
        }

    @Test
    fun `measurements without a cached route are Unknown`() =
        runTest {
            every { dao.observeMeasurements("WO-1") } returns flowOf(listOf(row("P-1", MeasurementSourceColumn.DEVICE)))
            assertEquals(CorridorCheck.Unknown, repository.observeMeasurements("WO-1").first().single().corridor)
        }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.polegps.data.PoleGpsRepositoryTest"`
Expected: FAIL to compile (`refreshRoute`, `corridor`, constructor arity).

- [ ] **Step 3: Implement in `PoleGpsRepository.kt`**

Add the field to `PoleMeasurement`:

```kotlin
data class PoleMeasurement(
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
    val source: MeasurementSource,
    val photoFilePath: String? = null,
    val corridor: CorridorCheck = CorridorCheck.Unknown,
)
```

Change the mapper to take the route:

```kotlin
private fun LocalPoleGpsMeasurementEntity.toDomain(geomWkt: String?) =
    PoleMeasurement(
        poleId = poleId,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        capturedAt = capturedAt,
        source = if (source == MeasurementSourceColumn.DEVICE) MeasurementSource.DEVICE else MeasurementSource.SERVER,
        photoFilePath = photoFilePath,
        corridor = corridorCheck(geomWkt, lat, lng),
    )
```

Add the constructor parameter (last): `private val assetsApi: AssetsApi,`.

Replace `observeMeasurements` and `allMeasurements`:

```kotlin
        fun observeMeasurements(workOrderId: String): Flow<List<PoleMeasurement>> =
            combine(dao.observeMeasurements(workOrderId), dao.observeRoute(workOrderId)) { rows, route ->
                rows.map { it.toDomain(route?.geomWkt) }
            }

        suspend fun allMeasurements(workOrderId: String): List<PoleMeasurement> {
            val geomWkt = dao.route(workOrderId)?.geomWkt
            return dao.measurements(workOrderId).map { it.toDomain(geomWkt) }
        }
```

Add:

```kotlin
        suspend fun refreshRoute(workOrderId: String) {
            val segmentId = detailSync.cached(workOrderId)?.segmentId ?: return
            val segment =
                try {
                    assetsApi.segment(segmentId)
                } catch (_: IOException) {
                    return
                } catch (_: HttpException) {
                    return
                } catch (_: SerializationException) {
                    return
                }
            dao.upsertRoute(LocalPoleGpsRouteEntity(workOrderId, segmentId, segment.geomWkt, Instant.now()))
        }

        suspend fun routeGeometry(workOrderId: String): String? = dao.route(workOrderId)?.geomWkt
```

Imports to add: `com.luxmap.core.network.AssetsApi`, `kotlinx.coroutines.flow.combine`, `kotlinx.serialization.SerializationException`, `retrofit2.HttpException`, `java.io.IOException`.

- [ ] **Step 4: Fix the other constructor call**

Run: `git grep -n "PoleGpsRepository(" -- app/src`
For every direct construction (expected: `PoleGpsRepositoryTest.kt`, `PoleGpsSubmissionTest.kt`), add the new last argument. In `PoleGpsSubmissionTest.setup()` that is `mockk(relaxed = true)` after the existing last `mockk(relaxed = true)`.

- [ ] **Step 5: Call `refreshRoute` when the order is opened**

In `WorkOrderDetailViewModel.kt`, in the block

```kotlin
                        if (detail.taskKind == "pole_gps_update") {
                            poleListRepository.poles(workOrderId)
                            poleReferencesState.value = poleGpsRepository.poleReferences(workOrderId)
                        }
```

add `poleGpsRepository.refreshRoute(workOrderId)` as the first line inside the `if`.

- [ ] **Step 6: Detail ViewModel test**

In `WorkOrderDetailViewModelTest.kt` add:

```kotlin
    @Test
    fun `a pole_gps_update order refreshes its route geometry when opened`() =
        runTest {
            val poleGpsRepository = mockk<PoleGpsRepository>(relaxed = true)
            every { poleGpsRepository.observeMeasurements(WORK_ORDER_ID) } returns flowOf(emptyList())
            every { poleGpsRepository.observeAttachments(WORK_ORDER_ID) } returns flowOf(emptyList())
            val vm = viewModel(detail = poleGpsDetail(), poleGpsRepository = poleGpsRepository)
            vm.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { poleGpsRepository.refreshRoute(WORK_ORDER_ID) }
        }
```

Add `import io.mockk.coVerify` if the file does not import it yet. Every existing test in this class that builds a strict `mockk<PoleGpsRepository>()` for a `pole_gps_update` detail now needs `coEvery { poleGpsRepository.refreshRoute(WORK_ORDER_ID) } returns Unit` next to its other stubs (at least the two tests at "loads its pole references once…" and "refreshes poles from the server…").

- [ ] **Step 7: Run the tests**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.polegps.data.PoleGpsRepositoryTest" --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderDetailViewModelTest"`
Expected: PASS. Then on the device: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.polegps.data.PoleGpsSubmissionTest` → PASS.

- [ ] **Step 8: ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt \
  app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt \
  app/src/test/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepositoryTest.kt \
  app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModelTest.kt \
  app/src/androidTest/java/com/luxmap/feature/workorder/polegps/data/PoleGpsSubmissionTest.kt
git commit -m "feat(pole-gps): attach corridor check to pole measurements"
```

---

### Task 5: Measure screen — show distances and confirm outside the corridor

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/CorridorText.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/ui/CorridorTextTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 `corridorCheck`, `distanceMetres`, `CorridorCheck`, `ROUTE_CORRIDOR_METRES`; Task 4 `PoleGpsRepository.routeGeometry`.
- Produces:
  - `fun corridorListLabel(check: CorridorCheck): String?`, `fun corridorPreviewLabel(check: CorridorCheck): String`, `fun previousDistanceLabel(distanceM: Double?): String?` (package `com.luxmap.feature.workorder.polegps.ui`)
  - `PoleGpsMeasureUiState.Success.routeGeometry: String?`
  - `PoleGpsMeasureViewModel.pendingCorridor: StateFlow<CorridorCheck.Outside?>`, `confirmOutsideCorridor()`, `corridorFor(fix: GpsFix): CorridorCheck`, `distanceFromPrevious(fix: GpsFix): Double?`

- [ ] **Step 1: Write the failing label tests**

```kotlin
package com.luxmap.feature.workorder.polegps.ui

import com.luxmap.feature.workorder.polegps.data.CorridorCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorridorTextTest {
    @Test
    fun `inside shows the distance against the limit`() {
        assertEquals("Cách tuyến 12.3 m / 25.0 m", corridorListLabel(CorridorCheck.Inside(12.34)))
    }

    @Test
    fun `outside is labelled as outside the corridor`() {
        assertEquals(
            "Ngoài hành lang tuyến · cách tuyến 31.0 m / 25.0 m",
            corridorListLabel(CorridorCheck.Outside(31.0)),
        )
    }

    @Test
    fun `unknown shows nothing in the list but explains itself on the measure screen`() {
        assertNull(corridorListLabel(CorridorCheck.Unknown))
        assertEquals("Chưa kiểm tra được hành lang tuyến", corridorPreviewLabel(CorridorCheck.Unknown))
    }

    @Test
    fun `previous distance is optional`() {
        assertNull(previousDistanceLabel(null))
        assertEquals("Lệch vị trí cũ 40.0 m", previousDistanceLabel(40.0))
    }
}
```

- [ ] **Step 2: Implement the labels**

```kotlin
package com.luxmap.feature.workorder.polegps.ui

import com.luxmap.feature.workorder.polegps.data.CorridorCheck
import com.luxmap.feature.workorder.polegps.data.ROUTE_CORRIDOR_METRES
import java.util.Locale

fun corridorListLabel(check: CorridorCheck): String? =
    when (check) {
        is CorridorCheck.Inside -> "Cách tuyến ${metres(check.distanceM)} / ${metres(ROUTE_CORRIDOR_METRES)}"
        is CorridorCheck.Outside ->
            "Ngoài hành lang tuyến · cách tuyến ${metres(check.distanceM)} / ${metres(ROUTE_CORRIDOR_METRES)}"
        CorridorCheck.Unknown -> null
    }

fun corridorPreviewLabel(check: CorridorCheck): String = corridorListLabel(check) ?: "Chưa kiểm tra được hành lang tuyến"

fun previousDistanceLabel(distanceM: Double?): String? = distanceM?.let { "Lệch vị trí cũ ${metres(it)}" }

private fun metres(value: Double): String = "%.1f m".format(Locale.ROOT, value)
```

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.polegps.ui.CorridorTextTest"` → PASS.

- [ ] **Step 3: Write the failing ViewModel tests**

In `PoleGpsMeasureViewModelTest.kt`: in `setUp()` add `coEvery { repository.routeGeometry("WO-1") } returns null`. Add imports `com.luxmap.feature.workorder.polegps.data.CorridorCheck`, `org.junit.Assert.assertNull`. Add:

```kotlin
    private val route = "LINESTRING (106 10, 106.001 10)"

    @Test
    fun `a fix outside the corridor waits for confirmation before saving`() =
        runTest {
            coEvery { repository.routeGeometry("WO-1") } returns route
            fixesFlow = flowOf(GpsFix(10.0005, 106.0005, 4.0, Instant.ofEpochSecond(1), 1_000_000_000L))
            val vm = viewModel()
            vm.onPermissionGranted()
            vm.onPhotoCaptured(File("p1.jpg"), hasLocationPermission = true) {}
            assertTrue(vm.pendingCorridor.value is CorridorCheck.Outside)
            coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
            vm.confirmOutsideCorridor()
            coVerify { repository.saveCapturedPhoto("WO-1", "P-1", any(), 10.0005, 106.0005, 4.0, any()) }
            assertNull(vm.pendingCorridor.value)
        }

    @Test
    fun `a fix inside the corridor saves without asking`() =
        runTest {
            coEvery { repository.routeGeometry("WO-1") } returns route
            fixesFlow = flowOf(GpsFix(10.0001, 106.0005, 4.0, Instant.ofEpochSecond(1), 1_000_000_000L))
            val vm = viewModel()
            vm.onPermissionGranted()
            vm.onPhotoCaptured(File("p1.jpg"), hasLocationPermission = true) {}
            assertNull(vm.pendingCorridor.value)
            coVerify { repository.saveCapturedPhoto("WO-1", "P-1", any(), 10.0001, 106.0005, 4.0, any()) }
        }

    @Test
    fun `no cached route saves without asking`() =
        runTest {
            fixesFlow = flowOf(GpsFix(10.5, 106.5, 4.0, Instant.ofEpochSecond(1), 1_000_000_000L))
            val vm = viewModel()
            vm.onPermissionGranted()
            vm.onPhotoCaptured(File("p1.jpg"), hasLocationPermission = true) {}
            assertNull(vm.pendingCorridor.value)
            coVerify { repository.saveCapturedPhoto("WO-1", "P-1", any(), 10.5, 106.5, 4.0, any()) }
        }

    @Test
    fun `confirming after the sample went stale saves nothing and asks to retry`() =
        runTest {
            coEvery { repository.routeGeometry("WO-1") } returns route
            fixesFlow = flowOf(GpsFix(10.0005, 106.0005, 4.0, Instant.ofEpochSecond(1), 1_000_000_000L))
            val vm = viewModel()
            vm.onPermissionGranted()
            vm.onPhotoCaptured(File("p1.jpg"), hasLocationPermission = true) {}
            nowNanos += 11_000_000_000L
            vm.confirmOutsideCorridor()
            coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
            assertTrue(vm.locationMissing.value)
        }

    @Test
    fun `distance from the previous position is shown only when one exists`() =
        runTest {
            val fix = GpsFix(10.0001, 106.0, 4.0, Instant.ofEpochSecond(1), 1_000_000_000L)
            val vm = viewModel()
            vm.onPermissionGranted()
            assertEquals(11.1, vm.distanceFromPrevious(fix)!!, 0.1)
            coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 3, 0.0, 0.0)
            val zero = viewModel().also { it.onPermissionGranted() }
            assertNull(zero.distanceFromPrevious(fix))
            coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 3, null, null)
            val missing = viewModel().also { it.onPermissionGranted() }
            assertNull(missing.distanceFromPrevious(fix))
        }
```

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.polegps.ui.measure.PoleGpsMeasureViewModelTest"`
Expected: FAIL to compile (`pendingCorridor`, `confirmOutsideCorridor`, `distanceFromPrevious`).

- [ ] **Step 4: UiState**

```kotlin
    data class Success(
        val poleLabel: String,
        val previous: PoleReference?,
        val routeGeometry: String? = null,
    ) : PoleGpsMeasureUiState
```

- [ ] **Step 5: ViewModel**

In `PoleGpsMeasureViewModel.kt`:

Add fields next to `_pendingConfirmation`:

```kotlin
        private val _pendingCorridor = MutableStateFlow<CorridorCheck.Outside?>(null)
        val pendingCorridor: StateFlow<CorridorCheck.Outside?> = _pendingCorridor.asStateFlow()
        private var pendingCorridorFix: GpsFix? = null
```

In `onPermissionGranted`, build the success state with the cached route:

```kotlin
                        PoleGpsMeasureUiState.Success(
                            poleLabel(reference),
                            reference,
                            repository.routeGeometry(workOrderId),
                        )
```

In `stopWatchingLocation()` and in `retryMeasurement()` (before `onPhotoCaptured(...)`), and at the start of `onPhotoCaptured` next to `_pendingConfirmation.value = null`, add:

```kotlin
            _pendingCorridor.value = null
            pendingCorridorFix = null
```

In `onPhotoCaptured`, replace `persist(file, fix, onDone)` (the `else` branch) with `proceed(file, fix, onDone)`. In `confirmLowAccuracy`, replace `persist(file, fix, onDone)` with `proceed(file, fix, onDone)`.

Add:

```kotlin
        fun corridorFor(fix: GpsFix): CorridorCheck =
            corridorCheck((_state.value as? PoleGpsMeasureUiState.Success)?.routeGeometry, fix.lat, fix.lng)

        fun distanceFromPrevious(fix: GpsFix): Double? {
            val previous = (_state.value as? PoleGpsMeasureUiState.Success)?.previous ?: return null
            val lat = previous.lat ?: return null
            val lng = previous.lng ?: return null
            if (lat == 0.0 && lng == 0.0) return null
            return distanceMetres(lat, lng, fix.lat, fix.lng)
        }

        fun confirmOutsideCorridor() {
            if (_isSaving.value) return
            val fix = pendingCorridorFix ?: return
            val file = pendingPhoto ?: return
            val onDone = pendingDone ?: return
            _pendingCorridor.value = null
            pendingCorridorFix = null
            if (!fix.isUsable()) {
                _locationMissing.value = true
                return
            }
            _isSaving.value = true
            captureJob =
                viewModelScope.launch {
                    try {
                        persist(file, fix, onDone)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        _locationMissing.value = true
                    } finally {
                        _isSaving.value = false
                    }
                }
        }

        private suspend fun proceed(
            file: File,
            fix: GpsFix,
            onDone: () -> Unit,
        ) {
            val corridor = corridorFor(fix)
            if (corridor is CorridorCheck.Outside) {
                pendingCorridorFix = fix
                _pendingCorridor.value = corridor
            } else {
                persist(file, fix, onDone)
            }
        }
```

Imports: `com.luxmap.feature.workorder.polegps.data.CorridorCheck`, `com.luxmap.feature.workorder.polegps.data.corridorCheck`, `com.luxmap.feature.workorder.polegps.data.distanceMetres`.

Run the ViewModel tests from Step 3 → PASS (all existing tests in the class too).

- [ ] **Step 6: Screen**

In `PoleGpsMeasureScreen.kt`:

Collect the new state next to `confirmation`:

```kotlin
    val corridorWarning by viewModel.pendingCorridor.collectAsState()
```

Change `PhotoPreview` to take two extra lines and show them under the accuracy line:

```kotlin
@Composable
internal fun PhotoPreview(
    file: File,
    isSaving: Boolean,
    fix: GpsFix?,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    corridor: CorridorCheck? = null,
    previousDistanceM: Double? = null,
) {
```

and inside, replace the `fix?.let { ... }` block with:

```kotlin
        fix?.let {
            Text("Sai số GPS: ±%.1f m".format(Locale.ROOT, it.accuracyM), style = MaterialTheme.typography.bodyLarge)
            Text("Thời gian mẫu: ${it.capturedAt}", style = MaterialTheme.typography.bodyMedium)
        }
        corridor?.let {
            Text(
                corridorPreviewLabel(it),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (it is CorridorCheck.Outside) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
        previousDistanceLabel(previousDistanceM)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
```

At the call site, pass:

```kotlin
            corridor = currentFix?.let(viewModel::corridorFor),
            previousDistanceM = currentFix?.let(viewModel::distanceFromPrevious),
```

After the existing `confirmation?.let { ... }` block, add the dialog (same button layout as `LowAccuracyConfirmation`: tapping outside means "Đo lại", never "save"):

```kotlin
        corridorWarning?.let { outside ->
            LuxConfirmDialog(
                title = "Vị trí ngoài hành lang tuyến",
                message =
                    "Vị trí đo cách tuyến %.1f m, vượt %.0f m. ".format(
                        Locale.ROOT,
                        outside.distanceM,
                        ROUTE_CORRIDOR_METRES,
                    ) + "Quản lý có thể trả lại báo cáo. Hãy đứng sát cột rồi đo lại nếu có thể.",
                confirmLabel = "Vẫn lưu vị trí này",
                onConfirm = viewModel::confirmOutsideCorridor,
                onDismiss = viewModel::retryMeasurement,
                dismissLabel = "Đo lại",
            )
        }
```

Imports: `CorridorCheck`, `ROUTE_CORRIDOR_METRES`, `com.luxmap.feature.workorder.polegps.ui.corridorPreviewLabel`, `com.luxmap.feature.workorder.polegps.ui.previousDistanceLabel`.

- [ ] **Step 7: Run the tests and ktlint**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.polegps.ui.*"` → PASS.
Run: `./gradlew ktlintCheck` → PASS.

- [ ] **Step 8: Commit** (check `git diff --stat`; if over 400 lines, commit the labels first as `feat(pole-gps): add corridor labels`)

```bash
git add app/src/main/java/com/luxmap/feature/workorder/polegps/ui \
  app/src/test/java/com/luxmap/feature/workorder/polegps/ui
git commit -m "feat(pole-gps): warn before saving a position outside the corridor"
```

---

### Task 6: Pole list labels and submit confirmation

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/sections/PoleGpsSection.kt` (`PoleGpsItem`)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt` (`submitSummaryRows`)

**Interfaces:**
- Consumes: Task 4 `PoleMeasurement.corridor`; Task 5 `corridorListLabel`.

- [ ] **Step 1: Pole list — corridor line instead of accuracy**

In `PoleGpsItem`, replace the block

```kotlin
        measurement?.let {
            Text(
                "GPS đã ghi nhận: %.6f, %.6f\nSai số GPS: ±%.1f m\nThời gian mẫu: %s"
                    .format(Locale.ROOT, it.lat, it.lng, it.accuracyM, it.capturedAt),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
```

with

```kotlin
        measurement?.let {
            Text(
                "GPS đã ghi nhận: %.6f, %.6f".format(Locale.ROOT, it.lat, it.lng),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            corridorListLabel(it.corridor)?.let { label ->
                val outside = it.corridor is CorridorCheck.Outside
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (outside) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (outside) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
```

Imports: `androidx.compose.material.icons.filled.Warning`, `com.luxmap.feature.workorder.polegps.data.CorridorCheck`, `com.luxmap.feature.workorder.polegps.ui.corridorListLabel`.

- [ ] **Step 2: Submit sheet — count poles outside the corridor**

In `submitSummaryRows`, replace the `POLE_GPS_UPDATE` branch with:

```kotlin
    } else if (kind == WorkOrderTaskKind.POLE_GPS_UPDATE) {
        val measured = state.poleMeasurements.map { it.poleId }.toSet().size
        val outside = state.poleMeasurements.count { it.corridor is CorridorCheck.Outside }
        listOf(
            SubmitSummaryRow(Icons.Filled.Navigation, primary, "Cột đã chụp", "$measured/${state.poleReferences.size}"),
            SubmitSummaryRow(
                Icons.Filled.Warning,
                if (outside > 0) MaterialTheme.colorScheme.error else primary,
                "Ngoài hành lang 25 m",
                "$outside cột",
            ),
            SubmitSummaryRow(Icons.Filled.StickyNote2, primary, "Tệp PDF", state.poleAttachments.size.toString()),
            SubmitSummaryRow(Icons.Filled.EditNote, primary, "Ghi chú", state.reportNote.trim().ifEmpty { "—" }),
        )
```

Imports: `androidx.compose.material.icons.filled.Warning` (if not present), `com.luxmap.feature.workorder.polegps.data.CorridorCheck`.

- [ ] **Step 3: Build and run the detail tests**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.*"` → PASS.
Run: `./gradlew ktlintCheck` → PASS.
Run on device: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.workorder.ui.detail.WorkOrderDetailScreenTest` → PASS (catches any broken text assertions).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/detail/sections/PoleGpsSection.kt \
  app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt
git commit -m "feat(pole-gps): show corridor status in pole list and submit sheet"
```

---

### Task 7: Real-device check and PR

- [ ] **Step 1: Manual check on SM-A075F against the dev backend** (`adb reverse tcp:5141 tcp:5141` if the backend runs locally; re-run it if login suddenly fails)

1. Open a `pole_gps_update` order whose segment has a drawn line while online → close the app → airplane mode → reopen the order: measure screen still shows "Cách tuyến …".
2. Measure a pole standing next to it → "Cách tuyến X m / 25.0 m", saves without a dialog; list shows the line, no "Sai số GPS".
3. Measure from > 25 m away from the road → dialog "Vị trí ngoài hành lang tuyến"; tap outside the dialog → it retries, nothing saved; "Vẫn lưu vị trí này" → saved, list line in red with the warning icon.
4. Pole with an old position → "Lệch vị trí cũ … m" on the measure screen; pole without one → no such line.
5. Order on a segment with no drawn line → "Chưa kiểm tra được hành lang tuyến", saves normally.
6. Submit → sheet shows "Ngoài hành lang 25 m: N cột".
7. On the web, return the order; on the phone re-measure only one pole and resubmit → on the web the report still lists every previously reported pole, with the re-measured one updated.

- [ ] **Step 2: Push and open the PR into `dev`**

```bash
git push -u origin feat/pole-gps-corridor-check
gh pr create --base dev --title "feat(pole-gps): corridor check and full resubmit" --body "Implements docs/superpowers/plans/2026-11-22-pole-gps-corridor-check.md (spec: docs/superpowers/specs/2026-11-22-pole-gps-update-business-flow.md, section 9)."
```

Only after the owner confirms the manual check. The spec file is untracked in the main checkout: copy it into the worktree and commit it as `docs(pole-gps): add business flow spec` before pushing.
