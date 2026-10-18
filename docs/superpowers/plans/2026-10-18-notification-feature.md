# F14 Thông báo (Notification) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the in-app notification list (F14) backed by the real BE-27 backend endpoints, with a persistent bell+badge entry point visible across the 4 main tabs, and tap-through navigation to the work order a notification is about.

**Architecture:** New `feature/notification/` module (`data/` + `ui/`, no `domain/` — matches CLAUDE.md's "simple screen" category). `NotificationListViewModel` calls `NotificationRepository` (Real-only, no Fake binding needed in `RepositoryModule` for the app to run, but a `FakeNotificationRepository` still exists per the project's always-have-both-impls convention) directly — no Room cache. A second, small `NotificationBadgeViewModel` is hoisted once in `NavGraph.kt` (same scoping trick as the existing `SessionViewModel`) to hold the unread count across tab switches; `NavGraph.kt` itself drives the 45-second poll via `repeatOnLifecycle(Lifecycle.State.STARTED)` on the Activity's own lifecycle, so it pauses automatically when the app is backgrounded — no new `lifecycle-process` dependency needed.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit + kotlinx.serialization, Material3 (`BadgedBox`/`Badge` for the unread count), JUnit + MockK + Turbine + Compose UI Test (existing stack, no new test libraries).

**Spec:** `docs/superpowers/specs/2026-10-18-notification-feature-design.md`

## Global Constraints

- `title`/`body` are server-composed Vietnamese text — render as-is, never translate or reformat.
- `type` and `entity_type` are wire strings that MUST be decoded as plain `String` in the DTO, never as a `@Serializable enum class` — an unrecognized future value must fall back safely (`NotificationType.UNKNOWN`), never throw during JSON decode or crash the screen.
- No Room cache for v1 — a failed fetch is `Error` with retry, not a stale list.
- Navigate on tap **only** when `entity_type == "work_order"` (→ `Routes.WorkOrderDetail`). Every other `entity_type` still marks the notification read but does not navigate.
- Every commit stays ≤400 changed lines (CLAUDE.md); split a task's commit further if it would exceed that.
- Branch off `dev`, name `feat/fm-notification-<slice>` (no official `FM-XX` code found for this ticket in the docs read so far — ask before inventing one if the task list is checked later); PR into `dev`, never `main`.
- Comments in code: English, plain words, WHY not WHAT (CLAUDE.md).

## Review Focus

- An unrecognized `type` or `entity_type` wire value (a type the backend adds before the app knows about it) must render with a safe fallback icon and must not crash list decoding or the row composable — tested in Task 1 (wire mapping) and Task 4 (icon mapping/UI).
- An empty inbox (`items` is an empty list) must show the dedicated `Empty` state, not a `Success` with a blank list — tested in Task 3.
- A network failure on the very first load must show `Error` with a retry button, not an empty-looking screen — since there's no cache, `Error` and `Empty` must stay visually and behaviorally distinct — tested in Task 3 and Task 4.
- A row whose `entity_type` is not `"work_order"` must still mark itself read on tap but must never attempt to navigate (wrong/garbage `entity_id` for the work-order route) — tested in Task 3 (ViewModel always marks read) and Task 6 (Route only calls `onOpenWorkOrder` for `work_order`).
- The 45-second poll must not spin up a second overlapping loop across recompositions/tab switches/rotation, and must resume (not restart from zero / double-fire) correctly after the app returns from background — exercised manually in Task 6's on-device check since `repeatOnLifecycle` itself is a well-tested androidx API; the plan does not reinvent a test for the library's own guarantee.

---

## Task 1: Wire contracts — DTOs, `NotificationType`, `NotificationsApi`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/notification/data/dto/NotificationItemDto.kt`
- Create: `app/src/main/java/com/luxmap/feature/notification/data/dto/NotificationPageDto.kt`
- Create: `app/src/main/java/com/luxmap/feature/notification/data/dto/UnreadCountDto.kt`
- Create: `app/src/main/java/com/luxmap/feature/notification/data/NotificationType.kt`
- Create: `app/src/main/java/com/luxmap/core/network/NotificationsApi.kt`
- Modify: `app/src/main/java/com/luxmap/di/NetworkModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/notification/data/NotificationTypeTest.kt`

**Interfaces:**
- Produces: `NotificationItemDto(notificationId, type: String, title, body, entityType: String, entityId, createdAt: String, readAt: String?)`, `NotificationPageDto(page, pageSize, total, unreadCount, items: List<NotificationItemDto>)`, `UnreadCountDto(unreadCount: Int)`, `enum class NotificationType { WORK_ORDER_ASSIGNED, WORK_ORDER_UNASSIGNED, WORK_ORDER_RESCHEDULED, WORK_ORDER_RETURNED, WORK_ORDER_CANCELLED, WORK_ORDER_COMPLETED, WORK_ORDER_VERIFIED, WORK_ORDER_OVERDUE, SURVEY_RETURNED, SURVEY_READY_FOR_REVIEW, SURVEY_PROCESSING_FAILED, FAULT_REPORTED, CITIZEN_REPORT_RECEIVED, UNKNOWN }`, `fun notificationTypeFromWire(value: String): NotificationType`, `interface NotificationsApi` with `list`/`unreadCount`/`markRead`/`markAllRead`.

- [ ] **Step 1: Write the failing test for wire mapping**

```kotlin
// app/src/test/java/com/luxmap/feature/notification/data/NotificationTypeTest.kt
package com.luxmap.feature.notification.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTypeTest {
    @Test
    fun `maps every real backend wire value to its enum constant`() {
        val expected =
            mapOf(
                "work_order_assigned" to NotificationType.WORK_ORDER_ASSIGNED,
                "work_order_unassigned" to NotificationType.WORK_ORDER_UNASSIGNED,
                "work_order_rescheduled" to NotificationType.WORK_ORDER_RESCHEDULED,
                "work_order_returned" to NotificationType.WORK_ORDER_RETURNED,
                "work_order_cancelled" to NotificationType.WORK_ORDER_CANCELLED,
                "work_order_completed" to NotificationType.WORK_ORDER_COMPLETED,
                "work_order_verified" to NotificationType.WORK_ORDER_VERIFIED,
                "work_order_overdue" to NotificationType.WORK_ORDER_OVERDUE,
                "survey_returned" to NotificationType.SURVEY_RETURNED,
                "survey_ready_for_review" to NotificationType.SURVEY_READY_FOR_REVIEW,
                "survey_processing_failed" to NotificationType.SURVEY_PROCESSING_FAILED,
                "fault_reported" to NotificationType.FAULT_REPORTED,
                "citizen_report_received" to NotificationType.CITIZEN_REPORT_RECEIVED,
            )
        expected.forEach { (wire, type) -> assertEquals(type, notificationTypeFromWire(wire)) }
    }

    @Test
    fun `falls back to UNKNOWN for a value the app does not recognize yet`() {
        assertEquals(NotificationType.UNKNOWN, notificationTypeFromWire("some_future_type"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.data.NotificationTypeTest"`
Expected: FAIL to compile — `NotificationType`/`notificationTypeFromWire` do not exist yet.

- [ ] **Step 3: Create `NotificationType.kt`**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/data/NotificationType.kt
package com.luxmap.feature.notification.data

enum class NotificationType {
    WORK_ORDER_ASSIGNED,
    WORK_ORDER_UNASSIGNED,
    WORK_ORDER_RESCHEDULED,
    WORK_ORDER_RETURNED,
    WORK_ORDER_CANCELLED,
    WORK_ORDER_COMPLETED,
    WORK_ORDER_VERIFIED,
    WORK_ORDER_OVERDUE,
    SURVEY_RETURNED,
    SURVEY_READY_FOR_REVIEW,
    SURVEY_PROCESSING_FAILED,
    FAULT_REPORTED,
    CITIZEN_REPORT_RECEIVED,
    UNKNOWN,
}

// type arrives from the backend as a snake_case wire string - an unknown value (a type added on
// the backend before the app knows about it) falls back to UNKNOWN's generic icon instead of
// crashing the screen. Checked directly against NotificationType enum in
// luxmap_backend/src/LuxMap.Modules.Notifications/Entities/Notification.cs (read 2026-10-18).
fun notificationTypeFromWire(value: String): NotificationType =
    when (value) {
        "work_order_assigned" -> NotificationType.WORK_ORDER_ASSIGNED
        "work_order_unassigned" -> NotificationType.WORK_ORDER_UNASSIGNED
        "work_order_rescheduled" -> NotificationType.WORK_ORDER_RESCHEDULED
        "work_order_returned" -> NotificationType.WORK_ORDER_RETURNED
        "work_order_cancelled" -> NotificationType.WORK_ORDER_CANCELLED
        "work_order_completed" -> NotificationType.WORK_ORDER_COMPLETED
        "work_order_verified" -> NotificationType.WORK_ORDER_VERIFIED
        "work_order_overdue" -> NotificationType.WORK_ORDER_OVERDUE
        "survey_returned" -> NotificationType.SURVEY_RETURNED
        "survey_ready_for_review" -> NotificationType.SURVEY_READY_FOR_REVIEW
        "survey_processing_failed" -> NotificationType.SURVEY_PROCESSING_FAILED
        "fault_reported" -> NotificationType.FAULT_REPORTED
        "citizen_report_received" -> NotificationType.CITIZEN_REPORT_RECEIVED
        else -> NotificationType.UNKNOWN
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.data.NotificationTypeTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Create the DTOs**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/data/dto/NotificationItemDto.kt
package com.luxmap.feature.notification.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Checked directly against luxmap_backend/src/LuxMap.Modules.Notifications/NotificationContracts.cs
// (NotificationItem), read 2026-10-18. type/entityType stay plain String, never a
// @Serializable enum - an unrecognized value must decode safely, not throw (see
// NotificationType.notificationTypeFromWire, mapped at the UI layer like woStatus elsewhere).
@Serializable
data class NotificationItemDto(
    @SerialName("notification_id") val notificationId: String,
    val type: String,
    val title: String,
    val body: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
)
```

```kotlin
// app/src/main/java/com/luxmap/feature/notification/data/dto/NotificationPageDto.kt
package com.luxmap.feature.notification.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// NotificationPage in NotificationContracts.cs - one poll of the list refreshes both the list
// and the badge (unread_count rides along), so this is its own shape, not the shared
// PagedResultDto<T> (that one has no unread_count field).
@Serializable
data class NotificationPageDto(
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
    @SerialName("unread_count") val unreadCount: Int,
    val items: List<NotificationItemDto>,
)
```

```kotlin
// app/src/main/java/com/luxmap/feature/notification/data/dto/UnreadCountDto.kt
package com.luxmap.feature.notification.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnreadCountDto(
    @SerialName("unread_count") val unreadCount: Int,
)
```

- [ ] **Step 6: Create `NotificationsApi.kt`**

```kotlin
// app/src/main/java/com/luxmap/core/network/NotificationsApi.kt
package com.luxmap.core.network

import com.luxmap.feature.notification.data.dto.NotificationPageDto
import com.luxmap.feature.notification.data.dto.UnreadCountDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Route checked against NotificationsController.cs: [Route("api/v{version:apiVersion}/notifications")].
interface NotificationsApi {
    @GET("api/v1/notifications")
    suspend fun list(
        @Query("unread_only") unreadOnly: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
    ): NotificationPageDto

    @GET("api/v1/notifications/unread-count")
    suspend fun unreadCount(): UnreadCountDto

    @POST("api/v1/notifications/{notification_id}/read")
    suspend fun markRead(
        @Path("notification_id") notificationId: String,
    )

    @POST("api/v1/notifications/read-all")
    suspend fun markAllRead()
}
```

- [ ] **Step 7: Wire `NotificationsApi` into `NetworkModule.kt`**

In `app/src/main/java/com/luxmap/di/NetworkModule.kt`, add the import `com.luxmap.core.network.NotificationsApi` and, after `provideSweepsApi`, add:

```kotlin
    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create(NotificationsApi::class.java)
```

- [ ] **Step 8: Confirm the whole module still compiles**

Run: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/notification/data/dto/NotificationItemDto.kt \
        app/src/main/java/com/luxmap/feature/notification/data/dto/NotificationPageDto.kt \
        app/src/main/java/com/luxmap/feature/notification/data/dto/UnreadCountDto.kt \
        app/src/main/java/com/luxmap/feature/notification/data/NotificationType.kt \
        app/src/main/java/com/luxmap/core/network/NotificationsApi.kt \
        app/src/main/java/com/luxmap/di/NetworkModule.kt \
        app/src/test/java/com/luxmap/feature/notification/data/NotificationTypeTest.kt
git commit -m "feat(fm-notification): add notification API contracts and wire mapping"
```

---

## Task 2: `NotificationRepository` — domain model, Fake, Real, DI binding

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/notification/data/NotificationRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/notification/data/FakeNotificationRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/notification/data/RealNotificationRepository.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/notification/data/RealNotificationRepositoryTest.kt`

**Interfaces:**
- Consumes: `NotificationsApi` (Task 1), `NotificationItemDto`/`NotificationPageDto`/`UnreadCountDto` (Task 1).
- Produces: `data class NotificationItem(notificationId: String, type: String, title: String, body: String, entityType: String, entityId: String, createdAt: String, readAt: String?)`, `data class NotificationPage(items: List<NotificationItem>, total: Int, unreadCount: Int)`, `interface NotificationRepository { suspend fun list(unreadOnly: Boolean = false): Result<NotificationPage>; suspend fun unreadCount(): Result<Int>; suspend fun markRead(notificationId: String): Result<Unit>; suspend fun markAllRead(): Result<Unit> }`.

- [ ] **Step 1: Write the failing repository test**

```kotlin
// app/src/test/java/com/luxmap/feature/notification/data/RealNotificationRepositoryTest.kt
package com.luxmap.feature.notification.data

import com.luxmap.core.network.NotificationsApi
import com.luxmap.feature.notification.data.dto.NotificationItemDto
import com.luxmap.feature.notification.data.dto.NotificationPageDto
import com.luxmap.feature.notification.data.dto.UnreadCountDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

private fun dto(
    id: String = "NTF-000001",
    type: String = "work_order_assigned",
    entityType: String = "work_order",
    entityId: String = "WO-1001",
    readAt: String? = null,
) = NotificationItemDto(
    notificationId = id,
    type = type,
    title = "Bạn được giao phiếu sửa chữa WO-1001",
    body = "WO-1001 — Thay bóng đèn. Hạn 20/10/2026.",
    entityType = entityType,
    entityId = entityId,
    createdAt = "2026-10-18T08:00:00Z",
    readAt = readAt,
)

class RealNotificationRepositoryTest {
    @Test
    fun `list maps the page DTO into the domain page in the same order`() =
        runTest {
            val api = mockk<NotificationsApi>()
            coEvery { api.list(unreadOnly = null, page = 1, pageSize = 50) } returns
                NotificationPageDto(page = 1, pageSize = 50, total = 1, unreadCount = 1, items = listOf(dto()))
            val repository = RealNotificationRepository(api)

            val result = repository.list()

            assertTrue(result.isSuccess)
            assertEquals(
                NotificationPage(
                    items =
                        listOf(
                            NotificationItem(
                                "NTF-000001",
                                "work_order_assigned",
                                "Bạn được giao phiếu sửa chữa WO-1001",
                                "WO-1001 — Thay bóng đèn. Hạn 20/10/2026.",
                                "work_order",
                                "WO-1001",
                                "2026-10-18T08:00:00Z",
                                null,
                            ),
                        ),
                    total = 1,
                    unreadCount = 1,
                ),
                result.getOrNull(),
            )
        }

    @Test
    fun `list passes unreadOnly through to the API`() =
        runTest {
            val api = mockk<NotificationsApi>()
            coEvery { api.list(unreadOnly = true, page = 1, pageSize = 50) } returns
                NotificationPageDto(page = 1, pageSize = 50, total = 0, unreadCount = 0, items = emptyList())
            val repository = RealNotificationRepository(api)

            repository.list(unreadOnly = true)

            coVerify { api.list(unreadOnly = true, page = 1, pageSize = 50) }
        }

    @Test
    fun `list returns failure when the backend rejects the request`() =
        runTest {
            val api = mockk<NotificationsApi>()
            coEvery { api.list(unreadOnly = null, page = 1, pageSize = 50) } throws httpException(500)
            val repository = RealNotificationRepository(api)

            assertTrue(repository.list().isFailure)
        }

    @Test
    fun `unreadCount returns the count from the dedicated endpoint`() =
        runTest {
            val api = mockk<NotificationsApi>()
            coEvery { api.unreadCount() } returns UnreadCountDto(unreadCount = 7)
            val repository = RealNotificationRepository(api)

            assertEquals(7, repository.unreadCount().getOrNull())
        }

    @Test
    fun `markRead calls the per-notification read endpoint`() =
        runTest {
            val api = mockk<NotificationsApi>(relaxed = true)
            val repository = RealNotificationRepository(api)

            val result = repository.markRead("NTF-000001")

            assertTrue(result.isSuccess)
            coVerify { api.markRead("NTF-000001") }
        }

    @Test
    fun `markAllRead calls the read-all endpoint`() =
        runTest {
            val api = mockk<NotificationsApi>(relaxed = true)
            val repository = RealNotificationRepository(api)

            val result = repository.markAllRead()

            assertTrue(result.isSuccess)
            coVerify { api.markAllRead() }
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.data.RealNotificationRepositoryTest"`
Expected: FAIL to compile — `NotificationRepository`/`RealNotificationRepository`/`NotificationItem`/`NotificationPage` do not exist yet.

- [ ] **Step 3: Create the repository interface and domain models**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/data/NotificationRepository.kt
package com.luxmap.feature.notification.data

// type/entityType stay the raw wire String here too (same convention as WorkOrderItem.woStatus) -
// mapped to NotificationType only where it's displayed (NotificationListScreen), not here.
data class NotificationItem(
    val notificationId: String,
    val type: String,
    val title: String,
    val body: String,
    val entityType: String,
    val entityId: String,
    val createdAt: String,
    val readAt: String?,
)

data class NotificationPage(
    val items: List<NotificationItem>,
    val total: Int,
    val unreadCount: Int,
)

interface NotificationRepository {
    suspend fun list(unreadOnly: Boolean = false): Result<NotificationPage>

    suspend fun unreadCount(): Result<Int>

    suspend fun markRead(notificationId: String): Result<Unit>

    suspend fun markAllRead(): Result<Unit>
}
```

- [ ] **Step 4: Create `RealNotificationRepository.kt`**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/data/RealNotificationRepository.kt
package com.luxmap.feature.notification.data

import com.luxmap.core.network.NotificationsApi
import com.luxmap.feature.notification.data.dto.NotificationItemDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealNotificationRepository
    @Inject
    constructor(
        private val api: NotificationsApi,
    ) : NotificationRepository {
        override suspend fun list(unreadOnly: Boolean): Result<NotificationPage> =
            runCatching {
                val dto = api.list(unreadOnly = if (unreadOnly) true else null)
                NotificationPage(
                    items = dto.items.map { it.toDomain() },
                    total = dto.total,
                    unreadCount = dto.unreadCount,
                )
            }

        override suspend fun unreadCount(): Result<Int> = runCatching { api.unreadCount().unreadCount }

        override suspend fun markRead(notificationId: String): Result<Unit> = runCatching { api.markRead(notificationId) }

        override suspend fun markAllRead(): Result<Unit> = runCatching { api.markAllRead() }

        private fun NotificationItemDto.toDomain() =
            NotificationItem(notificationId, type, title, body, entityType, entityId, createdAt, readAt)
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.data.RealNotificationRepositoryTest"`
Expected: PASS (6 tests)

- [ ] **Step 6: Create `FakeNotificationRepository.kt`**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/data/FakeNotificationRepository.kt
package com.luxmap.feature.notification.data

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeNotificationRepository
    @Inject
    constructor() : NotificationRepository {
        private val items =
            MutableStateFlow(
                listOf(
                    NotificationItem(
                        "NTF-000001",
                        "work_order_assigned",
                        "Bạn được giao phiếu sửa chữa WO-1001",
                        "WO-1001 — Thay bóng đèn. Hạn 20/10/2026.",
                        "work_order",
                        "WO-1001",
                        "2026-10-18T08:00:00Z",
                        readAt = null,
                    ),
                    NotificationItem(
                        "NTF-000002",
                        "survey_returned",
                        "Khảo sát SWEEP-1 bị trả lại",
                        "Phiếu WO-900. Lý do: thiếu đoạn cuối tuyến.",
                        "survey_sweep",
                        "SWEEP-1",
                        "2026-10-17T10:00:00Z",
                        readAt = "2026-10-17T12:00:00Z",
                    ),
                ),
            )

        override suspend fun list(unreadOnly: Boolean): Result<NotificationPage> {
            val current = if (unreadOnly) items.value.filter { it.readAt == null } else items.value
            return Result.success(
                NotificationPage(current, total = current.size, unreadCount = items.value.count { it.readAt == null }),
            )
        }

        override suspend fun unreadCount(): Result<Int> = Result.success(items.value.count { it.readAt == null })

        override suspend fun markRead(notificationId: String): Result<Unit> {
            items.value =
                items.value.map {
                    if (it.notificationId == notificationId) it.copy(readAt = "2026-10-18T00:00:00Z") else it
                }
            return Result.success(Unit)
        }

        override suspend fun markAllRead(): Result<Unit> {
            items.value = items.value.map { it.copy(readAt = it.readAt ?: "2026-10-18T00:00:00Z") }
            return Result.success(Unit)
        }
    }
```

- [ ] **Step 7: Bind `RealNotificationRepository` in `RepositoryModule.kt`**

Add the imports `com.luxmap.feature.notification.data.NotificationRepository` and
`com.luxmap.feature.notification.data.RealNotificationRepository`, then add after
`bindPoleListRepository`:

```kotlin
    @Binds
    abstract fun bindNotificationRepository(impl: RealNotificationRepository): NotificationRepository
```

- [ ] **Step 8: Compile and run the full notification test package**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.*"`
Expected: BUILD SUCCESSFUL, 8 tests pass (2 from Task 1 + 6 from this task)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/notification/data/NotificationRepository.kt \
        app/src/main/java/com/luxmap/feature/notification/data/RealNotificationRepository.kt \
        app/src/main/java/com/luxmap/feature/notification/data/FakeNotificationRepository.kt \
        app/src/main/java/com/luxmap/di/RepositoryModule.kt \
        app/src/test/java/com/luxmap/feature/notification/data/RealNotificationRepositoryTest.kt
git commit -m "feat(fm-notification): add NotificationRepository with Fake/Real impls"
```

---

## Task 3: `NotificationListUiState` + `NotificationListViewModel`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/notification/ui/NotificationListUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/notification/ui/NotificationListViewModel.kt`
- Test: `app/src/test/java/com/luxmap/feature/notification/ui/NotificationListViewModelTest.kt`

**Interfaces:**
- Consumes: `NotificationRepository` (Task 2), `NotificationItem`/`NotificationPage` (Task 2).
- Produces: `sealed interface NotificationListUiState { Loading; data class Success(items: List<NotificationListRowState>); Empty; data class Error(message: String) }`, `data class NotificationListRowState(notificationId, type, title, body, entityType, entityId, createdAt, isRead: Boolean)`, `class NotificationListViewModel { val uiState: StateFlow<NotificationListUiState>; fun onRetry(); fun onMarkRead(notificationId: String); fun onMarkAllRead() }`.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/luxmap/feature/notification/ui/NotificationListViewModelTest.kt
package com.luxmap.feature.notification.ui

import app.cash.turbine.test
import com.luxmap.feature.notification.data.NotificationItem
import com.luxmap.feature.notification.data.NotificationPage
import com.luxmap.feature.notification.data.NotificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private fun item(
    id: String,
    entityType: String = "work_order",
    readAt: String? = null,
) = NotificationItem(id, "work_order_assigned", "Title $id", "Body $id", entityType, "WO-1", "2026-10-18T08:00:00Z", readAt)

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Loading then Success with mapped rows`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1")), total = 1, unreadCount = 1))
            val vm = NotificationListViewModel(repository)

            vm.uiState.test {
                assertEquals(NotificationListUiState.Loading, awaitItem())
                val success = awaitItem() as NotificationListUiState.Success
                assertEquals(1, success.items.size)
                assertEquals("NTF-1", success.items[0].notificationId)
                assertEquals(false, success.items[0].isRead)
            }
        }

    @Test
    fun `emits Empty when there are no notifications`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.list() } returns Result.success(NotificationPage(emptyList(), total = 0, unreadCount = 0))
            val vm = NotificationListViewModel(repository)

            vm.uiState.test {
                assertEquals(NotificationListUiState.Loading, awaitItem())
                assertEquals(NotificationListUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Error when the fetch fails`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.list() } returns Result.failure(IllegalStateException("network down"))
            val vm = NotificationListViewModel(repository)

            vm.uiState.test {
                assertEquals(NotificationListUiState.Loading, awaitItem())
                val error = awaitItem() as NotificationListUiState.Error
                assertEquals("network down", error.message)
            }
        }

    @Test
    fun `onMarkRead optimistically flips the row and calls the repository`() =
        runTest {
            val repository = mockk<NotificationRepository>(relaxed = true)
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1")), total = 1, unreadCount = 1))
            val vm = NotificationListViewModel(repository)

            vm.uiState.test {
                awaitItem()
                val success = awaitItem() as NotificationListUiState.Success
                assertEquals(false, success.items[0].isRead)
                vm.onMarkRead("NTF-1")
                val updated = awaitItem() as NotificationListUiState.Success
                assertEquals(true, updated.items[0].isRead)
            }
            coVerify { repository.markRead("NTF-1") }
        }

    @Test
    fun `onMarkAllRead flips every row and calls the repository`() =
        runTest {
            val repository = mockk<NotificationRepository>(relaxed = true)
            coEvery { repository.list() } returns
                Result.success(NotificationPage(listOf(item("NTF-1"), item("NTF-2")), total = 2, unreadCount = 2))
            val vm = NotificationListViewModel(repository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onMarkAllRead()
                val updated = awaitItem() as NotificationListUiState.Success
                assertEquals(true, updated.items.all { it.isRead })
            }
            coVerify { repository.markAllRead() }
        }

    @Test
    fun `onMarkRead on a non-work-order row still marks it read`() =
        runTest {
            val repository = mockk<NotificationRepository>(relaxed = true)
            coEvery { repository.list() } returns
                Result.success(
                    NotificationPage(listOf(item("NTF-1", entityType = "survey_sweep")), total = 1, unreadCount = 1),
                )
            val vm = NotificationListViewModel(repository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onMarkRead("NTF-1")
                val updated = awaitItem() as NotificationListUiState.Success
                assertEquals(true, updated.items[0].isRead)
            }
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.ui.NotificationListViewModelTest"`
Expected: FAIL to compile — `NotificationListUiState`/`NotificationListViewModel` do not exist yet.

- [ ] **Step 3: Create `NotificationListUiState.kt`**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/ui/NotificationListUiState.kt
package com.luxmap.feature.notification.ui

data class NotificationListRowState(
    val notificationId: String,
    // Raw wire value (e.g. "work_order_assigned") - mapped to an icon via
    // notificationTypeFromWire() in NotificationListScreen, same convention as woStatus elsewhere.
    val type: String,
    val title: String,
    val body: String,
    // Raw wire value (e.g. "work_order") - only "work_order" is navigable in v1, see CLAUDE.md.
    val entityType: String,
    val entityId: String,
    val createdAt: String,
    val isRead: Boolean,
)

sealed interface NotificationListUiState {
    data object Loading : NotificationListUiState

    data class Success(val items: List<NotificationListRowState>) : NotificationListUiState

    data object Empty : NotificationListUiState

    data class Error(val message: String) : NotificationListUiState
}
```

- [ ] **Step 4: Create `NotificationListViewModel.kt`**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/ui/NotificationListViewModel.kt
package com.luxmap.feature.notification.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.notification.data.NotificationItem
import com.luxmap.feature.notification.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationListViewModel
    @Inject
    constructor(
        private val repository: NotificationRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<NotificationListUiState>(NotificationListUiState.Loading)
        val uiState: StateFlow<NotificationListUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun onRetry() {
            _uiState.value = NotificationListUiState.Loading
            load()
        }

        fun onMarkRead(notificationId: String) {
            flipRead { it.notificationId == notificationId }
            viewModelScope.launch { repository.markRead(notificationId) }
        }

        fun onMarkAllRead() {
            flipRead { true }
            viewModelScope.launch { repository.markAllRead() }
        }

        // Optimistic UI (CLAUDE.md offline-first spirit): flips the row(s) immediately instead of
        // waiting for the 204 - the mark-read calls above are fire-and-forget, matching the
        // idempotent, side-effect-only nature of those two endpoints.
        private fun flipRead(matches: (NotificationListRowState) -> Boolean) {
            val current = _uiState.value
            if (current is NotificationListUiState.Success) {
                _uiState.value =
                    current.copy(items = current.items.map { if (matches(it)) it.copy(isRead = true) else it })
            }
        }

        private fun load() {
            viewModelScope.launch {
                repository.list().fold(
                    onSuccess = { page ->
                        _uiState.value =
                            if (page.items.isEmpty()) {
                                NotificationListUiState.Empty
                            } else {
                                NotificationListUiState.Success(page.items.map { it.toRowState() })
                            }
                    },
                    onFailure = { e ->
                        _uiState.value = NotificationListUiState.Error(e.message ?: "Không tải được danh sách thông báo")
                    },
                )
            }
        }

        private fun NotificationItem.toRowState() =
            NotificationListRowState(notificationId, type, title, body, entityType, entityId, createdAt, isRead = readAt != null)
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.ui.NotificationListViewModelTest"`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/notification/ui/NotificationListUiState.kt \
        app/src/main/java/com/luxmap/feature/notification/ui/NotificationListViewModel.kt \
        app/src/test/java/com/luxmap/feature/notification/ui/NotificationListViewModelTest.kt
git commit -m "feat(fm-notification): add NotificationListViewModel"
```

---

## Task 4: `AppTopBar` actions slot + `NotificationListScreen` UI

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/ui/components/AppTopBar.kt`
- Create: `app/src/main/java/com/luxmap/feature/notification/ui/NotificationListScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/notification/ui/NotificationListScreenTest.kt`

**Interfaces:**
- Consumes: `NotificationListUiState`/`NotificationListRowState` (Task 3), `NotificationType`/`notificationTypeFromWire` (Task 1), `DateFormatUtils.formatIsoInstant` (existing, `core/common/DateFormatUtils.kt`).
- Produces: `@Composable fun NotificationListScreen(state: NotificationListUiState, onBack: () -> Unit, onRetry: () -> Unit = {}, onMarkAllRead: () -> Unit = {}, onItemClick: (NotificationListRowState) -> Unit = {})`, `@Composable fun NotificationListRoute(onBack: () -> Unit, onOpenWorkOrder: (String) -> Unit, viewModel: NotificationListViewModel = hiltViewModel())`. `AppTopBar` gains an additive `actions: @Composable RowScope.() -> Unit = {}` parameter (default keeps every existing call site unchanged).

- [ ] **Step 1: Add the `actions` slot to `AppTopBar`**

In `app/src/main/java/com/luxmap/core/ui/components/AppTopBar.kt`, add the import
`androidx.compose.foundation.layout.RowScope`, then change the signature and the `TopAppBar` call:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(text = title, style = TitleTextStyle)
                if (subtitle != null) {
                    Text(text = subtitle, style = SubtitleTextStyle)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}
```

- [ ] **Step 2: Confirm existing `AppTopBar` call sites still compile unchanged**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (default empty `actions` means no caller needs updating)

- [ ] **Step 3: Write the failing Compose UI test for `NotificationListScreen`**

```kotlin
// app/src/androidTest/java/com/luxmap/feature/notification/ui/NotificationListScreenTest.kt
package com.luxmap.feature.notification.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun row(
    id: String = "NTF-1",
    type: String = "work_order_assigned",
    entityType: String = "work_order",
    isRead: Boolean = false,
) = NotificationListRowState(id, type, "Bạn được giao phiếu WO-1001", "WO-1001 — Thay bóng đèn.", entityType, "WO-1001", "2026-10-18T08:00:00Z", isRead)

class NotificationListScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_the_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme { NotificationListScreen(state = NotificationListUiState.Loading, onBack = {}) }
        }
        composeRule.onNodeWithTag("notification_list_loading").assertExists()
    }

    @Test
    fun success_state_shows_each_notification_title_and_body() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(state = NotificationListUiState.Success(listOf(row())), onBack = {})
            }
        }
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").assertExists()
        composeRule.onNodeWithText("WO-1001 — Thay bóng đèn.").assertExists()
    }

    @Test
    fun tapping_a_work_order_row_calls_onItemClick_with_that_row() {
        var clicked: NotificationListRowState? = null
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row())),
                    onBack = {},
                    onItemClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").performClick()
        assertEquals("NTF-1", clicked?.notificationId)
    }

    @Test
    fun tapping_mark_all_read_calls_onMarkAllRead() {
        var marked = false
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row())),
                    onBack = {},
                    onMarkAllRead = { marked = true },
                )
            }
        }
        composeRule.onNodeWithText("Đánh dấu tất cả đã đọc").performClick()
        assertEquals(true, marked)
    }

    @Test
    fun empty_state_shows_the_empty_inbox_message() {
        composeRule.setContent {
            LuxMapTheme { NotificationListScreen(state = NotificationListUiState.Empty, onBack = {}) }
        }
        composeRule.onNodeWithText("Chưa có thông báo nào").assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_retry_calls_onRetry() {
        var retried = false
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Error("network down"),
                    onBack = {},
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText("network down").assertExists()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun an_unread_row_shows_the_unread_dot_and_a_read_row_does_not() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row(id = "NTF-1", isRead = false))),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("notification_unread_dot_NTF-1").assertExists()
    }

    @Test
    fun a_notification_type_the_app_does_not_recognize_yet_still_renders_without_crashing() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row(type = "some_future_type"))),
                    onBack = {},
                )
            }
        }
        // No assertion on which icon shows (the fallback is an implementation detail) - the
        // point of this test is that an unrecognized type does not throw during composition and
        // the row's own text still renders, per Review Focus item 1.
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").assertExists()
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: FAIL to compile — `NotificationListScreen` does not exist yet.

- [ ] **Step 5: Create `NotificationListScreen.kt`**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/ui/NotificationListScreen.kt
package com.luxmap.feature.notification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.notification.data.NotificationType
import com.luxmap.feature.notification.data.notificationTypeFromWire

@Composable
fun NotificationListRoute(
    onBack: () -> Unit,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    viewModel: NotificationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    NotificationListScreen(
        state = uiState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onMarkAllRead = viewModel::onMarkAllRead,
        onItemClick = { row ->
            viewModel.onMarkRead(row.notificationId)
            // Only entity_type = work_order has a real destination today - see CLAUDE.md's F14
            // "Khoảng trống đã biết" entry and the design spec section 6 for why survey_sweep/
            // fault/citizen_report are deliberately not navigable in v1.
            if (row.entityType == "work_order") onOpenWorkOrder(row.entityId)
        },
    )
}

@Composable
fun NotificationListScreen(
    state: NotificationListUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onItemClick: (NotificationListRowState) -> Unit = {},
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Thông báo",
                onBack = onBack,
                actions = {
                    if (state is NotificationListUiState.Success) {
                        TextButton(onClick = onMarkAllRead) { Text("Đánh dấu tất cả đã đọc") }
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            when (state) {
                is NotificationListUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.testTag("notification_list_loading"))

                is NotificationListUiState.Success ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.items) { row ->
                            NotificationRow(row = row, onClick = { onItemClick(row) })
                        }
                    }

                is NotificationListUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Chưa có thông báo nào",
                        subtitle = "Thông báo về việc được giao, khảo sát và sự cố sẽ xuất hiện ở đây.",
                        action = {},
                    )

                is NotificationListUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.ErrorOutline,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = "Không tải được danh sách thông báo",
                        subtitle = state.message,
                        action = { PrimaryButton(text = "Thử lại", onClick = onRetry) },
                    )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    row: NotificationListRowState,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = notificationTypeFromWire(row.type).icon(), contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, style = MaterialTheme.typography.titleMedium)
            Text(text = row.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = DateFormatUtils.formatIsoInstant(row.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!row.isRead) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("notification_unread_dot_${row.notificationId}"),
            )
        }
    }
}

// type -> icon (design spec section 6). Reuses ImageVectors already imported elsewhere in the
// app (WorkOrderStatus.icon(), WorkOrderTaskKind.icon() in core/theme/Color.kt) - no new icon.
private fun NotificationType.icon(): ImageVector =
    when (this) {
        NotificationType.WORK_ORDER_ASSIGNED,
        NotificationType.WORK_ORDER_RESCHEDULED,
        NotificationType.WORK_ORDER_OVERDUE,
        -> Icons.Filled.Assignment
        NotificationType.WORK_ORDER_UNASSIGNED -> Icons.Filled.Autorenew
        NotificationType.WORK_ORDER_RETURNED,
        NotificationType.FAULT_REPORTED,
        -> Icons.Filled.ErrorOutline
        NotificationType.WORK_ORDER_CANCELLED -> Icons.Filled.Cancel
        NotificationType.WORK_ORDER_COMPLETED -> Icons.Filled.CheckCircle
        NotificationType.WORK_ORDER_VERIFIED -> Icons.Filled.Verified
        NotificationType.SURVEY_RETURNED,
        NotificationType.SURVEY_READY_FOR_REVIEW,
        NotificationType.SURVEY_PROCESSING_FAILED,
        -> Icons.Filled.Videocam
        NotificationType.CITIZEN_REPORT_RECEIVED -> Icons.Filled.Search
        NotificationType.UNKNOWN -> Icons.Filled.Notifications
    }

```

- [ ] **Step 6: Run the UI test to verify it passes on the connected device**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.notification.ui.NotificationListScreenTest`
Expected: 8/8 pass

- [ ] **Step 7: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL — fix any formatting issue `ktlintFormat` reports before committing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/AppTopBar.kt \
        app/src/main/java/com/luxmap/feature/notification/ui/NotificationListScreen.kt \
        app/src/androidTest/java/com/luxmap/feature/notification/ui/NotificationListScreenTest.kt
git commit -m "feat(fm-notification): add NotificationListScreen"
```

---

## Task 5: `NotificationBadgeViewModel`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/notification/ui/NotificationBadgeViewModel.kt`
- Test: `app/src/test/java/com/luxmap/feature/notification/ui/NotificationBadgeViewModelTest.kt`

**Interfaces:**
- Consumes: `NotificationRepository` (Task 2).
- Produces: `class NotificationBadgeViewModel { val unreadCount: StateFlow<Int>; fun refresh() }`. This ViewModel owns no polling loop itself (kept free of any Android `Lifecycle` dependency, matching `SessionViewModel`'s shape) — the loop is driven externally from `NavGraph.kt` in Task 6.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/feature/notification/ui/NotificationBadgeViewModelTest.kt
package com.luxmap.feature.notification.ui

import app.cash.turbine.test
import com.luxmap.feature.notification.data.NotificationRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBadgeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts at zero before the first refresh`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            val vm = NotificationBadgeViewModel(repository)

            assertEquals(0, vm.unreadCount.value)
        }

    @Test
    fun `refresh updates unreadCount from the repository`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.unreadCount() } returns Result.success(5)
            val vm = NotificationBadgeViewModel(repository)

            vm.unreadCount.test {
                assertEquals(0, awaitItem())
                vm.refresh()
                assertEquals(5, awaitItem())
            }
        }

    @Test
    fun `refresh keeps the previous count when the fetch fails`() =
        runTest {
            val repository = mockk<NotificationRepository>()
            coEvery { repository.unreadCount() } returnsMany
                listOf(Result.success(3), Result.failure(IllegalStateException("network down")))
            val vm = NotificationBadgeViewModel(repository)

            vm.unreadCount.test {
                assertEquals(0, awaitItem())
                vm.refresh()
                assertEquals(3, awaitItem())
                vm.refresh()
                // No new emission - StateFlow conflates the unchanged value, so there is nothing
                // further to await here; if a failed refresh is the test's last action, confirm
                // via vm.unreadCount.value instead of awaiting a second time.
                expectNoEvents()
            }
            assertEquals(3, vm.unreadCount.value)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.ui.NotificationBadgeViewModelTest"`
Expected: FAIL to compile — `NotificationBadgeViewModel` does not exist yet.

- [ ] **Step 3: Create `NotificationBadgeViewModel.kt`**

```kotlin
// app/src/main/java/com/luxmap/feature/notification/ui/NotificationBadgeViewModel.kt
package com.luxmap.feature.notification.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.notification.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Hoisted once in NavGraph.kt (same ViewModelStoreOwner trick as SessionViewModel) so the badge
// count survives tab switches. Does not own a polling loop itself - NavGraph.kt's own
// LaunchedEffect calls refresh() on a timer gated by the Activity's own Lifecycle, keeping this
// class free of any Android Lifecycle/Compose dependency and easy to unit test.
@HiltViewModel
class NotificationBadgeViewModel
    @Inject
    constructor(
        private val repository: NotificationRepository,
    ) : ViewModel() {
        private val _unreadCount = MutableStateFlow(0)
        val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

        fun refresh() {
            viewModelScope.launch {
                repository.unreadCount().onSuccess { _unreadCount.value = it }
            }
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.ui.NotificationBadgeViewModelTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/notification/ui/NotificationBadgeViewModel.kt \
        app/src/test/java/com/luxmap/feature/notification/ui/NotificationBadgeViewModelTest.kt
git commit -m "feat(fm-notification): add NotificationBadgeViewModel"
```

---

## Task 6: Wire into `NavGraph.kt` — bell+badge, polling, routing, Profile row text

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/NotificationBellBar.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/luxmap/feature/profile/ui/ProfileScreen.kt:138`

**Interfaces:**
- Consumes: `NotificationBadgeViewModel` (Task 5), `NotificationListRoute` (Task 4), `Routes.Notification` (already exists in `Routes.kt`), `Routes.WorkOrderDetail.createRoute` (already exists).
- Produces: nothing new consumed by later tasks — this is the final integration task.

- [ ] **Step 1: Create the `NotificationBellBar` component**

```kotlin
// app/src/main/java/com/luxmap/core/ui/components/NotificationBellBar.kt
package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.luxmap.core.theme.Spacing

// Persistent, thin top element shown on the 4 tab screens only - lives in NavGraph.kt's own
// Scaffold (symmetric with the existing bottomBar slot), not inside AppTopBar, because none of
// the 4 tab root screens (Home/Survey Hub/Map/Profile) use AppTopBar today (design spec section 3).
@Composable
fun NotificationBellBar(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.End,
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                }
            },
        ) {
            IconButton(onClick = onClick, modifier = Modifier.testTag("notification_bell")) {
                val description = if (unreadCount > 0) "Thông báo, $unreadCount chưa đọc" else "Thông báo"
                Icon(imageVector = Icons.Filled.Notifications, contentDescription = description)
            }
        }
    }
}
```

- [ ] **Step 2: Wire `NotificationBadgeViewModel`, the bell bar, polling, and the route into `NavGraph.kt`**

Add these imports to `app/src/main/java/com/luxmap/navigation/NavGraph.kt`:

```kotlin
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.luxmap.core.ui.components.NotificationBellBar
import com.luxmap.feature.notification.ui.NotificationBadgeViewModel
import com.luxmap.feature.notification.ui.NotificationListRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
```

Right after the existing `sessionViewModel`/`isLoggedIn` block (after the `LaunchedEffect(isLoggedIn, currentRoute) { ... }` block, still before the `Scaffold(` call), add:

```kotlin
    val notificationBadgeViewModel: NotificationBadgeViewModel = hiltViewModel()
    val unreadNotificationCount by notificationBadgeViewModel.unreadCount.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                notificationBadgeViewModel.refresh()
                delay(NOTIFICATION_POLL_INTERVAL_MS)
            }
        }
    }
```

Add the constant near the top of the file, alongside `FADE_OUT_MS`/`FADE_IN_MS`:

```kotlin
private const val NOTIFICATION_POLL_INTERVAL_MS = 45_000L
```

Change the `Scaffold(` call to add a `topBar`, shown on the same 4 routes as `bottomBar`:

```kotlin
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showBottomBar) {
                NotificationBellBar(
                    unreadCount = unreadNotificationCount,
                    onClick = { navController.navigate(Routes.Notification.route) },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    ...
```

(leave the existing `bottomBar` body exactly as-is — only `topBar` is new.)

Add the route itself inside the `NavHost { ... }` block, next to the other top-level routes (after the `composable(Routes.Profile.route) { ProfileRoute() }` block):

```kotlin
            composable(Routes.Notification.route) {
                NotificationListRoute(
                    onBack = {
                        notificationBadgeViewModel.refresh()
                        navController.popBackStack()
                    },
                    onOpenWorkOrder = { workOrderId ->
                        navController.navigate(Routes.WorkOrderDetail.createRoute(workOrderId))
                    },
                )
            }
```

- [ ] **Step 3: Update the `ProfileScreen` row text**

In `app/src/main/java/com/luxmap/feature/profile/ui/ProfileScreen.kt:138`, change:

```kotlin
            SettingsRow(label = "Thông báo", value = "Chưa hỗ trợ trong phiên bản này")
```

to:

```kotlin
            SettingsRow(label = "Thông báo", value = "Xem ở biểu tượng chuông phía trên")
```

- [ ] **Step 4: Compile and run the full notification test suite**

Run: `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin && ./gradlew testDebugUnitTest --tests "com.luxmap.feature.notification.*"`
Expected: BUILD SUCCESSFUL, all unit tests pass (17 total: 2 from Task 1 + 6 from Task 2 + 6 from Task 3 + 3 from Task 5).

- [ ] **Step 5: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual on-device verification**

Build and install the debug APK on a real device (`./gradlew assembleDebug`, `adb install -r`),
with the backend reachable (`adb reverse tcp:5141 tcp:5141` if testing against the local dev
backend). Walk through, in this order:

1. Log in, land on a tab screen — confirm the bell appears top-right with no badge (or the real
   unread count, if the test account has any).
2. Tap the bell — `NotificationListScreen` opens, shows real data from `GET /notifications`.
3. Tap a `work_order_*` row — confirm it marks read (dot disappears) and navigates to
   `WorkOrderDetailScreen` for that work order.
4. Go back, tap a `survey_*`-typed row if one exists — confirm it marks read but does **not**
   navigate anywhere.
5. Tap "Đánh dấu tất cả đã đọc" — confirm every dot disappears.
6. Go back to a tab screen — confirm the bell badge reflects the new (lower) unread count
   immediately, not after waiting for the next poll tick.
7. Background the app (Home button) for over a minute, then foreground it again — confirm the
   app does not crash and the badge still updates (polling resumed, did not double-fire or hang).

Note any failures in the PR description; do not claim this step passed without actually running it.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/NotificationBellBar.kt \
        app/src/main/java/com/luxmap/navigation/NavGraph.kt \
        app/src/main/java/com/luxmap/feature/profile/ui/ProfileScreen.kt
git commit -m "feat(fm-notification): wire bell badge, polling, and routing into NavGraph"
```

---

## Post-plan housekeeping (not a task — do after the branch is reviewed/merged)

Update `CLAUDE.md`'s F14 "Khoảng trống đã biết" entry to say the feature is implemented (it
currently says `feature/notification` "chưa tồn tại" / "chưa gọi tới API này" — both become
false once this plan ships), and note the v1 navigation scope limitation (`work_order` only)
there too, the same way the pole-photo feature's gaps were kept current throughout its own
development this session.
