# F09 Work Order Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a Field Engineer open an Inspection or Repair work order from "Việc hôm nay" and see its faults (with a per-fault "Điều hướng" to Google Maps) and a "Bắt đầu" button that calls the real start API.

**Architecture:** Mirrors the existing `feature/map` pole-detail slice exactly: a `WorkOrderDetailRepository` interface with one Real implementation (`observeWorkOrderDetail` returns `Flow<WorkOrderDetail?>`, `null` = backend 404 → UI `Empty`), a `SavedStateHandle`-driven `WorkOrderDetailViewModel` with the mandatory 4-state `UiState`, and a stateless `WorkOrderDetailScreen` Composable. New pieces beyond that pattern: a `start()` action on the repository/ViewModel, and a first-of-its-kind `openInMaps` Intent helper in `core/common`.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Hilt, Retrofit + kotlinx.serialization, Coroutines/StateFlow, JUnit + MockK + Turbine (unit tests only — this codebase has no Compose UI tests beyond ViewModels; screens are verified on a real device instead).

**Spec:** `docs/superpowers/specs/2026-10-05-work-order-detail-design.md`

## Global Constraints

- No code comments in any file this plan touches (standing project rule, re-confirmed 2026-10-05) — not even the usual English "why" comments CLAUDE.md otherwise allows.
- Never commit to `main` or `dev` directly — branch off `dev` as `feat/fm-09-work-order-detail`, commit in chunks ≤400 changed lines, open a PR into `dev` only when asked.
- Reuse existing design tokens only: severity reuses `WorkOrderPriority` (no new badge), `fault_type`/`fault_status`/`inspection_outcome` are plain text labels (no new badge colors), `wo_status`/`task_kind` badges reuse the F02 `WorkOrderStatus`/`WorkOrderTaskKind` badges as-is.
- Every screen with async data gets all 4 `UiState` cases (Loading/Success/Empty/Error) — no skipping Empty/Error as "unlikely".
- "Hoàn thành" (complete) is explicitly out of scope — no code for it in this plan, not even a disabled button.
- `task_kind = "survey"` shows a one-line redirect message only — no fault list, no route rendering from `segment_ids`.
- Run `./gradlew.bat ktlintCheck` (and `ktlintFormat` if it fails) after every task before moving on.

## Review Focus

- An unknown `severity` wire value (a 5th value added server-side before the app knows about it) must fall back to `WorkOrderPriority.NORMAL`, not crash — covered in Task 1's test.
- A due Inspection/Repair work order with zero faults (edge case the spec doesn't call out — e.g. a `repair` order whose fault was already resolved before assignment) must show an explicit "no faults" line, not a silently blank section — covered in Task 8.
- `due_date` is `null` (not yet scheduled) must render "Chưa có hạn" via the already-tested `dueDateLabel()`, not a blank string or a crash — covered in Task 8 by reusing that existing function.
- An unknown `inspection_outcome` wire value (enum grows before the app does) must fall back to the raw value, not crash — covered in Task 2's test.
- Tapping "Bắt đầu" a second time before the first call returns must not fire a second `start()` call — covered in Task 7 by disabling the button on `isStarting` and testing that the state flips to `isStarting = true` synchronously once `start()` is called.

---

### Task 1: `severityFromWire` — map fault severity onto the existing priority badge

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Color.kt` (add after the `WorkOrderPriority` block, around line 177)
- Test: `app/src/test/java/com/luxmap/core/theme/SeverityFromWireTest.kt`

**Interfaces:**
- Produces: `fun severityFromWire(value: String): WorkOrderPriority`, used by Task 10's `FaultCard`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityFromWireTest {
    @Test
    fun `low maps to LOW`() {
        assertEquals(WorkOrderPriority.LOW, severityFromWire("low"))
    }

    @Test
    fun `medium maps to NORMAL`() {
        assertEquals(WorkOrderPriority.NORMAL, severityFromWire("medium"))
    }

    @Test
    fun `high maps to HIGH`() {
        assertEquals(WorkOrderPriority.HIGH, severityFromWire("high"))
    }

    @Test
    fun `critical maps to URGENT`() {
        assertEquals(WorkOrderPriority.URGENT, severityFromWire("critical"))
    }

    @Test
    fun `an unknown value falls back to NORMAL, not a crash`() {
        assertEquals(WorkOrderPriority.NORMAL, severityFromWire("something_new"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.core.theme.SeverityFromWireTest"`
Expected: FAIL — `severityFromWire` is unresolved.

- [ ] **Step 3: Write minimal implementation**

In `Color.kt`, directly below the existing `WorkOrderPriority.color()` function:

```kotlin
fun severityFromWire(value: String): WorkOrderPriority =
    when (value) {
        "low" -> WorkOrderPriority.LOW
        "high" -> WorkOrderPriority.HIGH
        "critical" -> WorkOrderPriority.URGENT
        else -> WorkOrderPriority.NORMAL
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.core.theme.SeverityFromWireTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Color.kt app/src/test/java/com/luxmap/core/theme/SeverityFromWireTest.kt
git commit -m "feat(fm-09): map fault severity onto the existing priority badge"
```

---

### Task 2: fault/status/inspection-outcome label functions

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderFaultLabels.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderFaultLabelsTest.kt`

**Interfaces:**
- Produces: `fun faultTypeLabel(value: String): String`, `fun faultStatusLabel(value: String): String`, `fun inspectionOutcomeLabel(value: String): String`, used by Task 10's `FaultCard`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.workorder.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkOrderFaultLabelsTest {
    @Test
    fun `fault type labels`() {
        assertEquals("Đèn tắt", faultTypeLabel("lamp_out"))
        assertEquals("Đèn mờ", faultTypeLabel("lamp_dim"))
        assertEquals("Mất điện cả tuyến", faultTypeLabel("segment_outage"))
        assertEquals("Mất kết nối cảm biến", faultTypeLabel("node_offline"))
        assertEquals("Suy giảm thời gian chiếu sáng", faultTypeLabel("runtime_decline"))
        assertEquals("future_type", faultTypeLabel("future_type"))
    }

    @Test
    fun `fault status labels`() {
        assertEquals("Đã phát hiện", faultStatusLabel("detected"))
        assertEquals("Đã xác nhận", faultStatusLabel("confirmed"))
        assertEquals("Đã từ chối", faultStatusLabel("rejected"))
        assertEquals("Đang xử lý", faultStatusLabel("in_progress"))
        assertEquals("Đã xử lý", faultStatusLabel("resolved"))
        assertEquals("Đã nghiệm thu", faultStatusLabel("verified"))
        assertEquals("future_status", faultStatusLabel("future_status"))
    }

    @Test
    fun `inspection outcome labels, with a fallback for an unknown value`() {
        assertEquals("Xác nhận có sự cố", inspectionOutcomeLabel("fault_present"))
        assertEquals("Không phát hiện sự cố", inspectionOutcomeLabel("fault_absent"))
        assertEquals("Chưa xác định", inspectionOutcomeLabel("inconclusive"))
        assertEquals("future_outcome", inspectionOutcomeLabel("future_outcome"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderFaultLabelsTest"`
Expected: FAIL — file/functions don't exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.luxmap.feature.workorder.ui.detail

fun faultTypeLabel(value: String): String =
    when (value) {
        "lamp_out" -> "Đèn tắt"
        "lamp_dim" -> "Đèn mờ"
        "segment_outage" -> "Mất điện cả tuyến"
        "node_offline" -> "Mất kết nối cảm biến"
        "runtime_decline" -> "Suy giảm thời gian chiếu sáng"
        else -> value
    }

fun faultStatusLabel(value: String): String =
    when (value) {
        "detected" -> "Đã phát hiện"
        "confirmed" -> "Đã xác nhận"
        "rejected" -> "Đã từ chối"
        "in_progress" -> "Đang xử lý"
        "resolved" -> "Đã xử lý"
        "verified" -> "Đã nghiệm thu"
        else -> value
    }

fun inspectionOutcomeLabel(value: String): String =
    when (value) {
        "fault_present" -> "Xác nhận có sự cố"
        "fault_absent" -> "Không phát hiện sự cố"
        "inconclusive" -> "Chưa xác định"
        else -> value
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderFaultLabelsTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderFaultLabels.kt app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderFaultLabelsTest.kt
git commit -m "feat(fm-09): add fault type, status, and inspection outcome labels"
```

---

### Task 3: `openInMaps` — the "Điều hướng" Intent helper

**Files:**
- Create: `app/src/main/java/com/luxmap/core/common/MapIntents.kt`
- Test: `app/src/test/java/com/luxmap/core/common/MapIntentsTest.kt`

**Interfaces:**
- Produces: `fun geoUri(lat: Double, lng: Double): String` (pure, tested), `fun openInMaps(context: Context, lat: Double, lng: Double)` (thin Android glue around it, used by Task 10).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MapIntentsTest {
    @Test
    fun `builds a geo URI with 6 decimal places and a query for the pin label`() {
        assertEquals("geo:10.964558,106.495788?q=10.964558,106.495788", geoUri(10.964558, 106.495788))
    }

    @Test
    fun `rounds to 6 decimal places`() {
        assertEquals("geo:10.123457,106.000000?q=10.123457,106.000000", geoUri(10.1234567, 106.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.core.common.MapIntentsTest"`
Expected: FAIL — `geoUri` is unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.luxmap.core.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

fun geoUri(lat: Double, lng: Double): String {
    val coords = String.format(Locale.US, "%.6f,%.6f", lat, lng)
    return "geo:$coords?q=$coords"
}

fun openInMaps(context: Context, lat: Double, lng: Double) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(lat, lng)))
    context.startActivity(Intent.createChooser(intent, null))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.core.common.MapIntentsTest"`
Expected: PASS, 2 tests. (`geoUri` is plain Kotlin, no Android framework call, so it runs under the regular JVM unit test task; `openInMaps` itself is untested here — it is one line of `Intent`/`Uri`/`startActivity` glue with no branching logic, verified on-device in Task 11 instead, the same way this codebase leaves other one-line Android glue untested.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/common/MapIntents.kt app/src/test/java/com/luxmap/core/common/MapIntentsTest.kt
git commit -m "feat(fm-09): add openInMaps intent helper for fault navigation"
```

---

### Task 4: DTOs, domain model, and the DTO→domain mapping

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetail.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/WorkOrderDetailMappingTest.kt`

**Interfaces:**
- Produces: `data class WorkOrderDetail(workOrderId, title, woStatus, taskKind, dueDate, scheduledDate, note, allowedActions, faults)`, `data class WorkOrderFaultDetail(faultId, lat, lng, faultType, faultStatus, severity, inspectionOutcome)`, `fun WorkOrderDetailDto.toWorkOrderDetail(): WorkOrderDetail` — all consumed by Task 6 (repository) and Task 10 (screen).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderFaultDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderFaultLocationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkOrderDetailMappingTest {
    @Test
    fun `maps every top-level field and flattens fault location into lat-lng`() {
        val dto =
            WorkOrderDetailDto(
                workOrderId = "WO-1",
                title = "Sửa đèn tuyến A",
                taskKind = "repair",
                woStatus = "assigned",
                dueDate = "2026-10-10",
                scheduledDate = "2026-10-09",
                note = "Ưu tiên xử lý trước mưa",
                allowedActions = listOf("start"),
                faults =
                    listOf(
                        WorkOrderFaultDetailDto(
                            faultId = "FAULT-1",
                            location = WorkOrderFaultLocationDto(lat = 10.97, lng = 106.49),
                            faultType = "lamp_out",
                            faultStatus = "confirmed",
                            severity = "high",
                            inspectionOutcome = "fault_present",
                        ),
                    ),
            )

        val detail = dto.toWorkOrderDetail()

        assertEquals("WO-1", detail.workOrderId)
        assertEquals("Sửa đèn tuyến A", detail.title)
        assertEquals("repair", detail.taskKind)
        assertEquals("assigned", detail.woStatus)
        assertEquals("2026-10-10", detail.dueDate)
        assertEquals("2026-10-09", detail.scheduledDate)
        assertEquals("Ưu tiên xử lý trước mưa", detail.note)
        assertEquals(listOf("start"), detail.allowedActions)
        val fault = detail.faults.single()
        assertEquals("FAULT-1", fault.faultId)
        assertTrue(fault.lat == 10.97)
        assertTrue(fault.lng == 106.49)
        assertEquals("lamp_out", fault.faultType)
        assertEquals("confirmed", fault.faultStatus)
        assertEquals("high", fault.severity)
        assertEquals("fault_present", fault.inspectionOutcome)
    }

    @Test
    fun `a survey order has no faults and that maps to an empty list, not a crash`() {
        val dto =
            WorkOrderDetailDto(
                workOrderId = "WO-2",
                title = "Khảo sát tuyến B",
                taskKind = "survey",
                woStatus = "assigned",
                dueDate = null,
                scheduledDate = null,
                note = null,
                allowedActions = emptyList(),
                faults = emptyList(),
            )

        val detail = dto.toWorkOrderDetail()

        assertEquals("survey", detail.taskKind)
        assertTrue(detail.faults.isEmpty())
        assertEquals(null, detail.dueDate)
        assertEquals(null, detail.note)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.WorkOrderDetailMappingTest"`
Expected: FAIL — `WorkOrderDetailDto`/`toWorkOrderDetail` unresolved.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt`:

```kotlin
package com.luxmap.feature.workorder.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Checked directly against luxmap_backend/src/LuxMap.Modules.WorkOrders/WorkOrderResponses.cs
// (WorkOrderDetail) - only the fields WorkOrderDetailScreen shows are declared here. ApiClient's
// Json is configured with ignoreUnknownKeys = true, so the rest of the real response
// (review_note, report_note, materials_note, materials_used, created_by, assigned_at/started_at/
// completed_at/closed_at, assignee_eligible, segment_ids, and the WorkOrderItem base fields not
// repeated here) is safely skipped instead of guessing a wire shape this task never reads.
@Serializable
data class WorkOrderDetailDto(
    @SerialName("work_order_id") val workOrderId: String,
    val title: String,
    @SerialName("task_kind") val taskKind: String,
    @SerialName("wo_status") val woStatus: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    val note: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val faults: List<WorkOrderFaultDetailDto> = emptyList(),
)

@Serializable
data class WorkOrderFaultDetailDto(
    @SerialName("fault_id") val faultId: String,
    val location: WorkOrderFaultLocationDto,
    @SerialName("fault_type") val faultType: String,
    @SerialName("fault_status") val faultStatus: String,
    val severity: String,
    @SerialName("inspection_outcome") val inspectionOutcome: String? = null,
)

@Serializable
data class WorkOrderFaultLocationDto(
    val lat: Double,
    val lng: Double,
)
```

`app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetail.kt`:

```kotlin
package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderFaultDetailDto

data class WorkOrderDetail(
    val workOrderId: String,
    val title: String,
    val woStatus: String,
    val taskKind: String,
    val dueDate: String?,
    val scheduledDate: String?,
    val note: String?,
    val allowedActions: List<String>,
    val faults: List<WorkOrderFaultDetail>,
)

data class WorkOrderFaultDetail(
    val faultId: String,
    val lat: Double,
    val lng: Double,
    val faultType: String,
    val faultStatus: String,
    val severity: String,
    val inspectionOutcome: String?,
)

fun WorkOrderDetailDto.toWorkOrderDetail(): WorkOrderDetail =
    WorkOrderDetail(
        workOrderId = workOrderId,
        title = title,
        woStatus = woStatus,
        taskKind = taskKind,
        dueDate = dueDate,
        scheduledDate = scheduledDate,
        note = note,
        allowedActions = allowedActions,
        faults = faults.map { it.toWorkOrderFaultDetail() },
    )

private fun WorkOrderFaultDetailDto.toWorkOrderFaultDetail() =
    WorkOrderFaultDetail(
        faultId = faultId,
        lat = location.lat,
        lng = location.lng,
        faultType = faultType,
        faultStatus = faultStatus,
        severity = severity,
        inspectionOutcome = inspectionOutcome,
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.WorkOrderDetailMappingTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetail.kt app/src/test/java/com/luxmap/feature/workorder/data/WorkOrderDetailMappingTest.kt
git commit -m "feat(fm-09): add work order detail dto, domain model, and mapping"
```

---

### Task 5: `WorkOrdersApi.detail()` and `.start()`

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt`

**Interfaces:**
- Consumes: `WorkOrderDetailDto` (Task 4).
- Produces: `suspend fun detail(id: String): WorkOrderDetailDto`, `suspend fun start(id: String)` — consumed by Task 6.

No standalone test — this is a Retrofit interface declaration with no logic; it is exercised by Task 6's repository tests. Not TDD'd in isolation for the same reason `list()` wasn't: there is nothing to assert on until something calls it.

- [ ] **Step 1: Edit the interface**

```kotlin
package com.luxmap.core.network

import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Route checked against WorkOrdersController.cs: [Route("api/v{version:apiVersion}/work-orders")].
interface WorkOrdersApi {
    @GET("api/v1/work-orders")
    suspend fun list(
        @Query("assigned_to") assignedTo: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
    ): PagedResultDto<WorkOrderItemDto>

    @GET("api/v1/work-orders/{id}")
    suspend fun detail(
        @Path("id") id: String,
    ): WorkOrderDetailDto

    @POST("api/v1/work-orders/{id}/start")
    suspend fun start(
        @Path("id") id: String,
    )
}
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt
git commit -m "feat(fm-09): add work order detail and start endpoints"
```

---

### Task 6: `WorkOrderDetailRepository` + `RealWorkOrderDetailRepository`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetailRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderDetailRepository.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/RealWorkOrderDetailRepositoryTest.kt`

**Interfaces:**
- Consumes: `WorkOrdersApi.detail()`/`.start()` (Task 5), `WorkOrderDetailDto.toWorkOrderDetail()` (Task 4).
- Produces: `interface WorkOrderDetailRepository { fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?>; suspend fun start(workOrderId: String): Result<Unit> }` — consumed by Task 8's ViewModel.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.workorder.data

import app.cash.turbine.test
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private const val WORK_ORDER_ID = "WO-1"

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

private fun detailDto() =
    WorkOrderDetailDto(
        workOrderId = WORK_ORDER_ID,
        title = "Sửa đèn tuyến A",
        taskKind = "repair",
        woStatus = "assigned",
        dueDate = null,
        scheduledDate = null,
        note = null,
        allowedActions = listOf("start"),
        faults = emptyList(),
    )

class RealWorkOrderDetailRepositoryTest {
    @Test
    fun `emits the mapped detail when the backend returns 200`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } returns detailDto()
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                val detail = awaitItem()
                assertEquals(WORK_ORDER_ID, detail?.workOrderId)
                awaitComplete()
            }
        }

    @Test
    fun `emits null on a 404, not an Error`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws httpException(404)
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a non-404 HTTP error propagates instead of being treated as not-found`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws httpException(500)
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                val error = awaitError()
                assertTrue(error is HttpException)
            }
        }

    @Test
    fun `start returns success when the backend accepts it`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.start(WORK_ORDER_ID) } returns Unit
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            assertTrue(repository.start(WORK_ORDER_ID).isSuccess)
        }

    @Test
    fun `start returns failure when the backend rejects it`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.start(WORK_ORDER_ID) } throws httpException(409)
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            assertTrue(repository.start(WORK_ORDER_ID).isFailure)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.RealWorkOrderDetailRepositoryTest"`
Expected: FAIL — `WorkOrderDetailRepository`/`RealWorkOrderDetailRepository` unresolved.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetailRepository.kt`:

```kotlin
package com.luxmap.feature.workorder.data

import kotlinx.coroutines.flow.Flow

interface WorkOrderDetailRepository {
    fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?>

    suspend fun start(workOrderId: String): Result<Unit>
}
```

`app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderDetailRepository.kt`:

```kotlin
package com.luxmap.feature.workorder.data

import com.luxmap.core.network.WorkOrdersApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealWorkOrderDetailRepository
    @Inject
    constructor(
        private val workOrdersApi: WorkOrdersApi,
    ) : WorkOrderDetailRepository {
        override fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?> =
            flow {
                try {
                    emit(workOrdersApi.detail(workOrderId).toWorkOrderDetail())
                } catch (e: HttpException) {
                    if (e.code() == 404) emit(null) else throw e
                }
            }

        override suspend fun start(workOrderId: String): Result<Unit> = runCatching { workOrdersApi.start(workOrderId) }
    }
```

In `RepositoryModule.kt`, add the import and binding:

```kotlin
import com.luxmap.feature.workorder.data.RealWorkOrderDetailRepository
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
```

```kotlin
    @Binds
    abstract fun bindWorkOrderDetailRepository(impl: RealWorkOrderDetailRepository): WorkOrderDetailRepository
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.RealWorkOrderDetailRepositoryTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetailRepository.kt app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderDetailRepository.kt app/src/main/java/com/luxmap/di/RepositoryModule.kt app/src/test/java/com/luxmap/feature/workorder/data/RealWorkOrderDetailRepositoryTest.kt
git commit -m "feat(fm-09): add work order detail repository with 404-to-empty handling"
```

---

### Task 7: `WorkOrderDetailUiState` and `WorkOrderDetailViewModel`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `WorkOrderDetailRepository` (Task 6).
- Produces: `sealed interface WorkOrderDetailUiState { Loading, Success(detail, isStarting, startError), Empty, Error(message) }`, `class WorkOrderDetailViewModel(savedStateHandle, repository) { val uiState: StateFlow<WorkOrderDetailUiState>; fun start() }`, `const val WORK_ORDER_ID_ARG = "workOrderId"` — consumed by Task 9 (navigation) and Task 10 (screen).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WORK_ORDER_ID = "WO-1"

private fun detail(allowedActions: List<String> = listOf("start")) =
    WorkOrderDetail(
        workOrderId = WORK_ORDER_ID,
        title = "Sửa đèn tuyến A",
        woStatus = "assigned",
        taskKind = "repair",
        dueDate = null,
        scheduledDate = null,
        note = null,
        allowedActions = allowedActions,
        faults = emptyList(),
    )

private fun savedStateHandle() = SavedStateHandle(mapOf(WorkOrderDetailViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

class WorkOrderDetailViewModelTest {
    @Test
    fun `emits Loading then Success when the repository has the work order`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(detail())
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val success = awaitItem() as WorkOrderDetailUiState.Success
                assertEquals(WORK_ORDER_ID, success.detail.workOrderId)
            }
        }

    @Test
    fun `emits Empty when the repository returns null`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(null)
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                assertEquals(WorkOrderDetailUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns
                flow { throw IllegalStateException("network down") }
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val error = awaitItem() as WorkOrderDetailUiState.Error
                assertEquals("network down", error.message)
            }
        }

    @Test
    fun `start sets isStarting immediately, then reloads so the button can disappear`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returnsMany
                listOf(flowOf(detail(allowedActions = listOf("start"))), flowOf(detail(allowedActions = emptyList())))
            coEvery { repository.start(WORK_ORDER_ID) } coAnswers {
                delay(10)
                Result.success(Unit)
            }
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                val loaded = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(loaded.isStarting)

                viewModel.start()

                val starting = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(starting.isStarting)

                val reloading = awaitItem()
                assertEquals(WorkOrderDetailUiState.Loading, reloading)
                val reloaded = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(reloaded.detail.allowedActions.isEmpty())
                assertFalse(reloaded.isStarting)
            }
        }

    @Test
    fun `start failure keeps the loaded detail and sets a start error instead of discarding the page`() =
        runTest {
            val repository = mockk<WorkOrderDetailRepository>()
            every { repository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(detail())
            coEvery { repository.start(WORK_ORDER_ID) } returns Result.failure(IllegalStateException("conflict"))
            val viewModel = WorkOrderDetailViewModel(savedStateHandle(), repository)

            viewModel.uiState.test {
                assertEquals(WorkOrderDetailUiState.Loading, awaitItem())
                awaitItem() as WorkOrderDetailUiState.Success

                viewModel.start()

                val starting = awaitItem() as WorkOrderDetailUiState.Success
                assertTrue(starting.isStarting)
                val failed = awaitItem() as WorkOrderDetailUiState.Success
                assertFalse(failed.isStarting)
                assertEquals("conflict", failed.startError)
            }
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderDetailViewModelTest"`
Expected: FAIL — `WorkOrderDetailUiState`/`WorkOrderDetailViewModel` unresolved.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailUiState.kt`:

```kotlin
package com.luxmap.feature.workorder.ui.detail

import com.luxmap.feature.workorder.data.WorkOrderDetail

sealed interface WorkOrderDetailUiState {
    data object Loading : WorkOrderDetailUiState

    data class Success(
        val detail: WorkOrderDetail,
        val isStarting: Boolean = false,
        val startError: String? = null,
    ) : WorkOrderDetailUiState

    data object Empty : WorkOrderDetailUiState

    data class Error(val message: String) : WorkOrderDetailUiState
}
```

`app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt`:

```kotlin
package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkOrderDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: WorkOrderDetailRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])

        private val _uiState = MutableStateFlow<WorkOrderDetailUiState>(WorkOrderDetailUiState.Loading)
        val uiState: StateFlow<WorkOrderDetailUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = WorkOrderDetailUiState.Loading
                repository
                    .observeWorkOrderDetail(workOrderId)
                    .catch { e ->
                        _uiState.value = WorkOrderDetailUiState.Error(e.message ?: "Không tải được dữ liệu lệnh")
                    }.collect { detail ->
                        _uiState.value =
                            if (detail == null) {
                                WorkOrderDetailUiState.Empty
                            } else {
                                WorkOrderDetailUiState.Success(detail = detail)
                            }
                    }
            }
        }

        fun start() {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Success) return
            _uiState.value = current.copy(isStarting = true, startError = null)
            viewModelScope.launch {
                repository.start(workOrderId).fold(
                    onSuccess = { load() },
                    onFailure = { e ->
                        val afterFailure = _uiState.value
                        if (afterFailure is WorkOrderDetailUiState.Success) {
                            _uiState.value =
                                afterFailure.copy(
                                    isStarting = false,
                                    startError = e.message ?: "Không bắt đầu được lệnh này",
                                )
                        }
                    },
                )
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
```

Note the first `load()` must set `Loading` right before subscribing (not only once in `init`), otherwise `start()`'s reload keeps the stale `Success` on screen with no visible transition — this is why `load()` itself sets `_uiState.value = WorkOrderDetailUiState.Loading` rather than relying on the constructor's initial value.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderDetailViewModelTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailUiState.kt app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModelTest.kt
git commit -m "feat(fm-09): add work order detail view model with start action"
```

---

### Task 8: `WorkOrderDetailScreen` Composable

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt`

**Interfaces:**
- Consumes: `WorkOrderDetailUiState`/`WorkOrderDetailViewModel` (Task 7), `severityFromWire` (Task 1), `faultTypeLabel`/`faultStatusLabel`/`inspectionOutcomeLabel` (Task 2), `openInMaps` (Task 3), `dueDateLabel` from `com.luxmap.core.ui.components.WorkOrderCard` (existing), `workOrderStatusFromWire`/`workOrderTaskKindFromWire`/`.label()`/`.badgeColors()` (existing, F02).
- Produces: `fun WorkOrderDetailRoute(onBack: () -> Unit, viewModel: WorkOrderDetailViewModel = hiltViewModel())`, used by Task 9's `NavGraph`.

No new unit test — this codebase has no Compose UI test coverage beyond ViewModels (see `PoleDetailScreen`/`HomeScreen`, both untested at the Composable level); this screen is checked on a real device in Task 11 instead, against the three shapes `@Preview` renders below (an Inspection/Repair order with faults, a Repair order with zero faults, and a Survey order).

- [ ] **Step 1: Write the screen**

```kotlin
package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.color
import com.luxmap.core.theme.label
import com.luxmap.core.theme.severityFromWire
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.TextOnlyStatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail

@Composable
fun WorkOrderDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    WorkOrderDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onStart = viewModel::start,
        onNavigateFault = { lat, lng -> openInMaps(context, lat, lng) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderDetailScreen(
    uiState: WorkOrderDetailUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết lệnh") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is WorkOrderDetailUiState.Loading -> LoadingState()
                is WorkOrderDetailUiState.Success ->
                    WorkOrderDetailContent(state = uiState, onStart = onStart, onNavigateFault = onNavigateFault)
                is WorkOrderDetailUiState.Empty -> MessageState("Không tìm thấy lệnh này")
                is WorkOrderDetailUiState.Error -> MessageState(uiState.message)
            }
        }
    }
}

@Composable
private fun WorkOrderDetailContent(
    state: WorkOrderDetailUiState.Success,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
) {
    val detail = state.detail
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        WorkOrderHeaderSection(detail)
        if (detail.taskKind == "survey") {
            Text(text = "Lệnh khảo sát — xem trong tab Khảo sát", style = MaterialTheme.typography.bodyMedium)
        } else {
            FaultListSection(faults = detail.faults, onNavigateFault = onNavigateFault)
        }
        if ("start" in detail.allowedActions) {
            PrimaryButton(
                text = "Bắt đầu",
                onClick = onStart,
                enabled = !state.isStarting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.startError != null) {
            Text(
                text = state.startError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WorkOrderHeaderSection(detail: WorkOrderDetail) {
    val status = workOrderStatusFromWire(detail.woStatus)
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = detail.title, style = MaterialTheme.typography.titleLarge)
        Text(text = detail.workOrderId, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(text = status.label(), colors = status.badgeColors())
            StatusBadge(text = kind.label(), colors = kind.badgeColors())
        }
        Text(text = dueDateLabel(detail.dueDate).text, style = MaterialTheme.typography.bodyMedium)
        if (!detail.scheduledDate.isNullOrBlank()) {
            Text(
                text = "Lịch khảo sát/sửa chữa: ${DateFormatUtils.formatPlainDate(detail.scheduledDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!detail.note.isNullOrBlank()) {
            Text(text = detail.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FaultListSection(
    faults: List<WorkOrderFaultDetail>,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (faults.isEmpty()) {
            Text(
                text = "Không có sự cố nào trong lệnh này",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            faults.forEach { fault ->
                FaultCard(fault = fault, onNavigate = { onNavigateFault(fault.lat, fault.lng) })
            }
        }
    }
}

@Composable
private fun FaultCard(
    fault: WorkOrderFaultDetail,
    onNavigate: () -> Unit,
) {
    val priority = severityFromWire(fault.severity)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TextOnlyStatusBadge(text = priority.label(), color = priority.color())
        Text(text = faultTypeLabel(fault.faultType), style = MaterialTheme.typography.titleMedium)
        Text(text = faultStatusLabel(fault.faultStatus), style = MaterialTheme.typography.bodyMedium)
        if (fault.inspectionOutcome != null) {
            Text(text = inspectionOutcomeLabel(fault.inspectionOutcome), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = Icons.Filled.Navigation, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Điều hướng")
        }
    }
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun BoxScope.MessageState(text: String) {
    Text(text = text, modifier = Modifier.align(Alignment.Center).padding(Spacing.lg))
}

private fun sampleRepairWithFaults() =
    WorkOrderDetail(
        workOrderId = "WO-1042",
        title = "Sửa đèn tuyến A",
        woStatus = "assigned",
        taskKind = "repair",
        dueDate = "2026-10-10",
        scheduledDate = "2026-10-09",
        note = "Ưu tiên xử lý trước khi mưa lớn",
        allowedActions = listOf("start"),
        faults =
            listOf(
                WorkOrderFaultDetail(
                    faultId = "FAULT-1",
                    lat = 10.97,
                    lng = 106.49,
                    faultType = "lamp_out",
                    faultStatus = "confirmed",
                    severity = "high",
                    inspectionOutcome = "fault_present",
                ),
            ),
    )

private fun sampleRepairNoFaults() = sampleRepairWithFaults().copy(workOrderId = "WO-1099", faults = emptyList())

private fun sampleSurvey() =
    sampleRepairWithFaults().copy(workOrderId = "WO-2001", taskKind = "survey", faults = emptyList())

@Preview(name = "Success - Repair with faults", showBackground = true, heightDp = 900)
@Composable
private fun WorkOrderDetailScreenRepairPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleRepairWithFaults()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
    )
}

@Preview(name = "Success - Repair, no faults", showBackground = true)
@Composable
private fun WorkOrderDetailScreenNoFaultsPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleRepairNoFaults()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
    )
}

@Preview(name = "Success - Survey", showBackground = true)
@Composable
private fun WorkOrderDetailScreenSurveyPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleSurvey()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
    )
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenLoadingPreview() {
    WorkOrderDetailScreen(uiState = WorkOrderDetailUiState.Loading, onBack = {}, onStart = {}, onNavigateFault = { _, _ -> })
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenEmptyPreview() {
    WorkOrderDetailScreen(uiState = WorkOrderDetailUiState.Empty, onBack = {}, onStart = {}, onNavigateFault = { _, _ -> })
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenErrorPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Error(message = "Không tải được dữ liệu"),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
    )
}
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run ktlint**

Run: `./gradlew.bat ktlintCheck`
Expected: no violations. If there are, run `./gradlew.bat ktlintFormat` and re-check.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt
git commit -m "feat(fm-09): add work order detail screen"
```

---

### Task 9: Wire up navigation (`Routes`, `NavGraph`, `HomeScreen.onOpenWorkOrder`)

**Files:**
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt`

**Interfaces:**
- Consumes: `WorkOrderDetailRoute` (Task 8).

No test — pure wiring, same as `PoleDetail`'s route (also untested at this level).

- [ ] **Step 1: Add the route**

In `Routes.kt`, after `data object PoleDetail`:

```kotlin
    data object WorkOrderDetail : Routes {
        override val route = "work-order/{workOrderId}"

        fun createRoute(workOrderId: String) = "work-order/$workOrderId"
    }
```

- [ ] **Step 2: Let `HomeRoute` take a real navigation callback**

In `HomeScreen.kt`, change:

```kotlin
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
```

to:

```kotlin
fun HomeRoute(
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
```

and change the `HomeScreen(...)` call inside it from `onOpenWorkOrder = { showNotImplemented() }` to `onOpenWorkOrder = onOpenWorkOrder`.

- [ ] **Step 3: Wire the route in `NavGraph.kt`**

Add the import:

```kotlin
import com.luxmap.feature.workorder.ui.detail.WorkOrderDetailRoute
```

Change the Home composable block from:

```kotlin
            composable(Routes.Home.route) {
                HomeRoute()
            }
```

to:

```kotlin
            composable(Routes.Home.route) {
                HomeRoute(
                    onOpenWorkOrder = { workOrderId ->
                        navController.navigate(Routes.WorkOrderDetail.createRoute(workOrderId))
                    },
                )
            }
```

and add a new composable block after the `PoleDetail` one:

```kotlin
            composable(
                route = Routes.WorkOrderDetail.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) {
                WorkOrderDetailRoute(onBack = { navController.popBackStack() })
            }
```

- [ ] **Step 4: Build to confirm it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/navigation/Routes.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt
git commit -m "feat(fm-09): navigate from home to work order detail"
```

---

### Task 10: Fix the pre-existing `HomeScreenTest.kt` compile break from the new `HomeRoute` signature

**Files:**
- Modify: `app/src/androidTest/java/com/luxmap/feature/home/ui/HomeScreenTest.kt`

**Interfaces:**
- None — this task only keeps the instrumented test suite compiling after Task 9's `HomeRoute` signature change. `HomeScreenTest.kt` calls `HomeScreen(...)` directly (not `HomeRoute`), so it is unaffected by Task 9's edit — check first whether this task is actually needed.

- [ ] **Step 1: Check whether anything broke**

Run: `./gradlew.bat :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL, since `HomeScreenTest.kt` calls the `HomeScreen` composable (unchanged signature) rather than `HomeRoute`. If it fails, read the exact error and fix only that compile break — do not add new test coverage for this screen in this task (out of scope, matches the project owner's earlier explicit scoping for this same file).

- [ ] **Step 2: Commit only if Step 1 required a change**

```bash
git add app/src/androidTest/java/com/luxmap/feature/home/ui/HomeScreenTest.kt
git commit -m "fix(fm-09): keep HomeScreenTest compiling after the HomeRoute signature change"
```

---

### Task 11: Manual verification on a real device

**Files:** none — this task runs the finished feature against the real backend, the same closing step every prior screen in this project went through.

- [ ] **Step 1: Run the full relevant unit test suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.*" --tests "com.luxmap.core.theme.*" --tests "com.luxmap.core.common.*" --tests "com.luxmap.feature.home.*"`
Expected: all PASS.

- [ ] **Step 2: ktlint**

Run: `./gradlew.bat ktlintCheck`
Expected: no violations.

- [ ] **Step 3: Install and launch on the connected device**

```bash
./gradlew.bat :app:installDebug
/c/Users/Thinkpad/AppData/Local/Android/Sdk/platform-tools/adb.exe reverse tcp:5141 tcp:5141
/c/Users/Thinkpad/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am force-stop com.luxmap
/c/Users/Thinkpad/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n com.luxmap/.MainActivity
```

- [ ] **Step 4: Verify against the real backend, with the local backend running**

- Log in, open "Việc hôm nay", tap a `repair` or `inspection` work order assigned to the signed-in Field Engineer — confirm the detail screen loads real data (title, status/task-kind badges, due date, fault list with severity/type/status) instead of the old "Chức năng đang được hoàn thiện" snackbar.
- Tap "Điều hướng" on a fault — confirm it opens Google Maps (or the map-app chooser) centered on that fault's coordinates.
- If a work order with `wo_status = assigned` is available, confirm "Bắt đầu" is visible; tap it and confirm the button disappears after it succeeds (re-fetch shows `wo_status = in_progress`, `allowed_actions` without `start`).
- If a `survey` work order is available, confirm it shows the "Lệnh khảo sát — xem trong tab Khảo sát" message instead of a fault list.
- Back out and confirm the bottom nav nothing broke on the Home screen.

- [ ] **Step 5: Report back**

Summarize what was checked and any discrepancy found against the real backend (new wire values not in `faultTypeLabel`/`faultStatusLabel`/`inspectionOutcomeLabel`, unexpected `allowed_actions` values, etc.) so it can be logged to `docs/contract-drift.md` if needed — do not fix silently without flagging it first.
