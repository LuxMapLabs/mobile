# Chụp ảnh cột đèn — Redesign vNext Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the pole list, the photo/video preview and the AI result screens to Design System vNext, with row tap opening the AI result (P1), one progress badge per row (P2) and an inline review card with only the submit button pinned (P3) — no API, Room, sync or review-rule change.

**Architecture:** Pure row logic (`poleProgressBadge`, `poleRowAction`) and `isNetworkError` first, then the pole list (frame/rows, then bottom bar/sheets/states), a shared `PoleMediaPreviewFrame` for both preview screens, then the AI result (frame/decision/photo/conclusion, review card + pinned submit + details, states).

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01, M3), Hilt, Coroutines/Flow, JUnit + MockK + Turbine, Compose UI Test.

**Spec:** `docs/design-system/screens/POLE_PHOTO.md` (approved 2026-11-17, P1–P3).

## Global Constraints

- Branch `style/redesign-pole-photo-vnext` (from `dev`, spec commit `dd47c0d`). Never commit to `main`.
- Commits ≤ 400 changed lines, `type(pole-photo): description`, English, lowercase, imperative. **No Claude attribution.** No new code comments.
- No API, Room, `sync_queue`, review-rule (note required when differing from AI; locked once `decision` exists), Intent-camera or navigation change beyond P1.
- Only theme tokens in `feature/`: no `Navy*`, `Blue*`, `Gray*`, `Color(0x…)`, `Color.White`/`Color.Black` (except white text/icons on `overlayOnMedia` chips), no `*TextStyle`, no `Radius.*`, no `BrandHeroGradient`/`Brush`, no ALL-CAPS labels, no `MessageState`/`InlineBanner`.
- Touch targets ≥ 48dp; margin 16dp. Preview screens stay `LuxMapTheme(forceDark = true)`.
- Stale banners use `LuxBanner(tone = LuxBannerTone.INFO)` (same as Home; there is no OFFLINE tone).
- Copy (exact, unchanged unless listed): pole list title `Chụp ảnh cột đèn`, subtitle = workOrderId; section `Cột trên tuyến · N`; bottom `Còn lại` / `N cột chưa chụp` / `Hoàn tất` / `Đã hoàn tất`; row click labels `Xem kết quả AI`, `Chụp ảnh cột`; progress labels `Đồng bộ lỗi`, `Xung đột`, existing sync labels for queued/syncing, `Đã xác nhận`, `Chờ duyệt`, `Đã tải lên`; loading descriptions `Đang tải danh sách cột`, `Đang tải kết quả AI`, `Đang mở camera`; error titles `Không tải được danh sách cột`, `Không tải được kết quả AI` with `Kiểm tra kết nối rồi thử lại.` / `Vui lòng thử lại sau ít phút.`; AI result title `Kết quả AI`, subtitle `Cột N · <poleId>` (just `<poleId>` when position is 0); sections `Kết luận`, `Kỹ sư xác nhận nhãn`.
- Unit: `./gradlew testLocalDebugUnitTest --tests "<fqcn>"`. Device (SM-A075F): `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"`. `./gradlew ktlintCheck` each task. Stop after each task for the user.

## Review Focus

1. **A pole with a sync error AND a saved review draft**: only `Đồng bộ lỗi` shows — the engineer must see the problem first. Unit test (Task 1) + UI test (Task 2).
2. **Completed survey**: rows without an AI result are not clickable; rows with one still open the AI result. UI test (Task 2).
3. **Typing a review note with the keyboard open on 360dp**: the pinned submit button stays above the keyboard and the note field is reachable. `imePadding` on the pinned bar (Task 6); large-font test covers the button (Task 8).
4. **AI model failed (`cv_state` null)**: review card still shows, both choices selectable, note required for either choice. Existing tests kept and adapted (Task 6).
5. **Saving in progress on preview**: close, retake and confirm all disabled; close button ≥ 48dp. UI test (Task 4).

---

### Task 1: Row logic and network errors

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleRowLogic.kt`
- Create: `app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleRowLogicTest.kt`
- Modify: `PoleListUiState.kt` (both `Error`s), `PoleListViewModel.kt` (3 error sites), `PoleListViewModelTest.kt`

**Interfaces — Produces:**
- `enum class PoleProgress { SYNC_FAILED, SYNC_CONFLICT, SYNCING, QUEUED_OFFLINE, QUEUED_ONLINE, REVIEW_SAVED, AWAITING_DECISION, UPLOADED }`
- `fun poleProgressBadge(row: PoleListRowState): PoleProgress?`
- `sealed interface PoleRowAction { data class OpenAiResult(val serverPhotoId: String); data object OpenReportSheet; data object None }`
- `fun poleRowAction(row: PoleListRowState, isCompleted: Boolean): PoleRowAction`
- `PoleListUiState.Error(message, isNetworkError = false)`, `PolePhotoResultUiState.Error(message, isNetworkError = false)`

- [ ] **Step 1: Failing tests** — `PoleRowLogicTest.kt`:

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import com.luxmap.core.theme.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoleRowLogicTest {
    private fun row(
        sync: SyncStatus? = null,
        photoId: String? = null,
        review: Boolean = false,
        decision: Boolean = false,
    ) = PoleListRowState("P-1", 1, "normal", photoId, sync, review, decision)

    @Test
    fun `not captured has no progress badge`() {
        assertNull(poleProgressBadge(row()))
    }

    @Test
    fun `sync problems win over review and decision`() {
        assertEquals(PoleProgress.SYNC_FAILED, poleProgressBadge(row(SyncStatus.FAILED, review = true, decision = true)))
        assertEquals(PoleProgress.SYNC_CONFLICT, poleProgressBadge(row(SyncStatus.CONFLICT, review = true)))
    }

    @Test
    fun `in-flight sync wins over review`() {
        assertEquals(PoleProgress.SYNCING, poleProgressBadge(row(SyncStatus.SYNCING, review = true)))
        assertEquals(PoleProgress.QUEUED_OFFLINE, poleProgressBadge(row(SyncStatus.QUEUED_OFFLINE)))
        assertEquals(PoleProgress.QUEUED_ONLINE, poleProgressBadge(row(SyncStatus.QUEUED_ONLINE, decision = true)))
    }

    @Test
    fun `uploaded rows show review, then decision, then uploaded`() {
        assertEquals(PoleProgress.REVIEW_SAVED, poleProgressBadge(row(SyncStatus.DONE, "PH-1", review = true, decision = true)))
        assertEquals(PoleProgress.AWAITING_DECISION, poleProgressBadge(row(SyncStatus.DONE, "PH-1", decision = true)))
        assertEquals(PoleProgress.UPLOADED, poleProgressBadge(row(SyncStatus.DONE, "PH-1")))
    }

    @Test
    fun `a row with an ai result opens it even after completion`() {
        val done = row(SyncStatus.DONE, "PH-1")
        assertEquals(PoleRowAction.OpenAiResult("PH-1"), poleRowAction(done, isCompleted = false))
        assertEquals(PoleRowAction.OpenAiResult("PH-1"), poleRowAction(done, isCompleted = true))
    }

    @Test
    fun `rows without an ai result open the report sheet until the survey is completed`() {
        assertEquals(PoleRowAction.OpenReportSheet, poleRowAction(row(), isCompleted = false))
        assertEquals(PoleRowAction.OpenReportSheet, poleRowAction(row(SyncStatus.QUEUED_ONLINE), isCompleted = false))
        assertEquals(PoleRowAction.OpenReportSheet, poleRowAction(row(SyncStatus.DONE, null), isCompleted = false))
        assertEquals(PoleRowAction.None, poleRowAction(row(), isCompleted = true))
    }
}
```

(Check `SyncStatus` enum names in `core/theme` first; if `PoleListRowState`'s positional order differs, use named args.)

In `PoleListViewModelTest` copy `emits Error when the pole list request fails` into `an IOException from the pole list is a network error` (stub failure `IOException("x")`, assert `isNetworkError == true`) and add `isNetworkError == false` to the original; add one `onViewAiResult` case with no cache and `IOException` → `(aiResultState as Error).isNetworkError == true` (follow the existing `onViewAiResult` test setup).

- [ ] **Step 2: Run, expect FAIL** (unresolved references).
- [ ] **Step 3: Implement** `PoleRowLogic.kt`:

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import com.luxmap.core.theme.SyncStatus

enum class PoleProgress { SYNC_FAILED, SYNC_CONFLICT, SYNCING, QUEUED_OFFLINE, QUEUED_ONLINE, REVIEW_SAVED, AWAITING_DECISION, UPLOADED }

sealed interface PoleRowAction {
    data class OpenAiResult(val serverPhotoId: String) : PoleRowAction

    data object OpenReportSheet : PoleRowAction

    data object None : PoleRowAction
}

fun poleProgressBadge(row: PoleListRowState): PoleProgress? =
    when (row.syncStatus) {
        null -> null
        SyncStatus.FAILED -> PoleProgress.SYNC_FAILED
        SyncStatus.CONFLICT -> PoleProgress.SYNC_CONFLICT
        SyncStatus.SYNCING -> PoleProgress.SYNCING
        SyncStatus.QUEUED_OFFLINE -> PoleProgress.QUEUED_OFFLINE
        SyncStatus.QUEUED_ONLINE -> PoleProgress.QUEUED_ONLINE
        SyncStatus.DONE ->
            when {
                row.hasPendingReview -> PoleProgress.REVIEW_SAVED
                row.hasPendingDecision -> PoleProgress.AWAITING_DECISION
                else -> PoleProgress.UPLOADED
            }
    }

fun poleRowAction(
    row: PoleListRowState,
    isCompleted: Boolean,
): PoleRowAction {
    val photoId = row.serverPhotoId
    return when {
        row.syncStatus == SyncStatus.DONE && photoId != null -> PoleRowAction.OpenAiResult(photoId)
        isCompleted -> PoleRowAction.None
        else -> PoleRowAction.OpenReportSheet
    }
}
```

(If `SyncStatus` has more entries, the `when` must stay exhaustive — map extra entries by meaning and ledger a ruling.) Add `isNetworkError: Boolean = false` to both `Error` classes; in `PoleListViewModel` set `isNetworkError = e is IOException` at the 3 `Error(...)` sites.
- [ ] **Step 4: Run `PoleRowLogicTest`, `PoleListViewModelTest`, `compileLocalDebugAndroidTestKotlin`, ktlint; PASS.**
- [ ] **Step 5: Commit** `feat(pole-photo): add row progress and tap logic and network errors`

---

### Task 2: Pole list frame, card and rows

**Files:** `PoleListScreen.kt` (`PoleListScreen` frame, delete `PoleListHeader`, `PoleListSectionHeader`, `cardOutline`, rewrite `PoleRow`, `PoleNumberAvatar`, delete `polePhotoSyncLabel`), `PoleListScreenTest.kt`.

**Interfaces — Consumes:** `poleProgressBadge`, `poleRowAction`, `LuxChildTopBar(title, onBack, subtitle)`, `LuxSectionHeader`, `LuxCard(contentPadding)`, `LuxBanner`, `StatusBadge(text, colors, icon)`, `LuxTheme.status.sync`, `LuxTheme.status.asset`.

- [ ] **Step 1: Failing UI tests** (read the file's `pole(...)` helper first and extend it with `syncStatus`, `serverPhotoId`, `hasPendingReview`):
  - `tapping_a_pole_with_an_ai_result_opens_it` — row `DONE` + `serverPhotoId = "PH-9"`: node `hasText("POLE-1", substring = true) and hasClickAction()` has `Role.Button`; click → `onViewAiResult` got `"PH-9"`; tag `pole_chevron_POLE-1` (unmerged) exists; text `Báo lỗi cột` does not appear.
  - `a_sync_error_hides_the_review_badge` — row `FAILED` + `hasPendingReview = true`: `Đồng bộ lỗi` exists, `Đã xác nhận` does not.
  - `a_completed_survey_keeps_ai_rows_clickable_but_not_the_others` — `isCompleted = true`, one `DONE`+photo row, one uncaptured row: the first has click action, the second (`hasText("POLE-2")`) has no click action.
  - Replace `loading_state_shows_the_hero_header` with `the_top_bar_shows_the_title_and_the_work_order` (`Chụp ảnh cột đèn`, `WO-1` in Success state).
  - Delete `success_state_tapping_the_ai_result_icon_calls_onViewAiResult_with_the_server_photo_id` and `success_state_hides_the_ai_result_icon_when_the_photo_has_not_finished_syncing` (no icon anymore — covered by the new tests); adapt `success_state_shows_the_pending_review_tag…`/`…pending_decision_tag…` rows to `syncStatus = DONE` + photo id.
- [ ] **Step 2: Implement**
  - Frame: `Scaffold(topBar = { LuxChildTopBar(title = "Chụp ảnh cột đèn", subtitle = workOrderId, onBack = onBack) }, bottomBar = { if (Success) PoleListBottomBar(...) }, containerColor = background) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { when (state) … } }`. Remove the `offset`/`clip` sheet.
  - Success list: `LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md), spacedBy(Spacing.md))`: stale `LuxBanner(message = <same copy>, tone = INFO)`; `LuxSectionHeader("Cột trên tuyến · ${poles.size}")`; one `LuxCard(contentPadding = PaddingValues(0.dp)) { Column { rows with HorizontalDivider(outlineVariant) } }`.
  - `PoleRow(pole, isNext, action, onAction)`: `Row(Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).then(click).padding(horizontal = Spacing.lg, vertical = Spacing.md))` where click = `clickable(onClickLabel = "Xem kết quả AI", role = Role.Button)` for `OpenAiResult`, `clickable(onClickLabel = "Chụp ảnh cột")` for `OpenReportSheet`, nothing for `None`. Content: `PoleNumberAvatar` (40dp, `shapes.small`; isNext → `primary`/`onPrimary`, captured → `primaryContainer`/`onPrimaryContainer`, else `surfaceContainerHigh`/`onSurfaceVariant`); `Column(weight 1)` with pole id `titleMedium` 1 line ellipsis and `FlowRow` of asset badge (`LuxTheme.status.asset` via existing `condition.badgeColors()`/`label()`/`icon()`) + progress badge; trailing: `Icons.Filled.ChevronRight` (tag `pole_chevron_<id>`, tint `onSurfaceVariant`) for `OpenAiResult`, `Icons.Filled.CameraAlt` (tint `primary`) when `syncStatus == null` and action is `OpenReportSheet`, else nothing.
  - `PoleProgress` → badge: `SYNC_FAILED` → `SyncStatus.FAILED` label/colors/icon but text `Đồng bộ lỗi`; `SYNC_CONFLICT` → `Xung đột`; `SYNCING`/`QUEUED_*` → that `SyncStatus`'s own label/colors/icon; `REVIEW_SAVED` → `Đã xác nhận` with `QUEUED_ONLINE` colors/icon; `AWAITING_DECISION` → `Chờ duyệt` same; `UPLOADED` → `Đã tải lên` with `DONE` colors/icon. Put this mapping as a private `@Composable fun PoleProgress.badge()` in `PoleListScreen.kt`.
  - Click wiring in `PoleListScreen`: `when (action) { is OpenAiResult -> onViewAiResult(action.serverPhotoId); OpenReportSheet -> reportMethodSheetPoleId = pole.poleId; None -> Unit }`.
- [ ] **Step 3: Run `PoleListScreenTest` on device + ktlint; PASS.** Split commit if > 400 lines.
- [ ] **Step 4: Commit** `style(pole-photo): rebuild the pole list frame and rows with vnext components`

---

### Task 3: Pole list bottom bar, sheets and states

**Files:** `PoleListScreen.kt` (`PoleListBottomBar`, `ReportMethodSheet`, `ReportMethodOption`, `CompleteSurveyConfirmSheet`, Loading/Empty/Error, delete `PoleListLoadingSkeleton`/`PoleRowSkeleton`/`SkeletonBar`), `PoleListScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — replace `error_state_shows_the_message_and_retry_calls_onRetry` with `a_network_error_uses_screen_copy_and_retries` (`Error("java.net.UnknownHostException", isNetworkError = true)` → `Không tải được danh sách cột`, `Kiểm tra kết nối rồi thử lại.`, no `UnknownHostException`, `Thử lại` calls `onRetry` once); add `loading_is_one_announced_region` (`onNodeWithContentDescription("Đang tải danh sách cột")`). Keep empty/sheet/confirm tests unchanged (copy unchanged).
- [ ] **Step 2: Implement**
  - Bottom bar: `Surface(color = surface) { Column { HorizontalDivider(outlineVariant); Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = CenterVertically) { Column(weight 1) { Text("Còn lại", bodyMedium, onSurfaceVariant); Text("$left cột chưa chụp", titleMedium) }; PrimaryButton(text = if (isCompleted) "Đã hoàn tất" else "Hoàn tất", onClick = onTapComplete, enabled = !isCompleted, trailingIcon = null) } } }`. Keep the existing button test tag if a test uses one.
  - Report method sheet: keep `ModalBottomSheet` and the 4 options + copy; title `Báo lỗi cột` in `titleMedium`, pole id `bodyLarge`/`onSurfaceVariant`; `ReportMethodOption` = `Row(heightIn(min = 56.dp).clickable(role = Role.Button).padding(horizontal = Spacing.lg))` with icon `primary` + label `bodyLarge` + chevron `onSurfaceVariant`. Remove `Radius.*`, `LabelTextStyle`.
  - Confirm sheet: same copy and tag `confirm_survey_complete`; buttons `LuxSecondaryButton("Kiểm tra lại")` + `PrimaryButton("Hoàn tất")` in a `Row(spacedBy(Spacing.sm))`, each `weight(1f)`; icon tile uses `LuxTheme.colors.successContainer`/`success` if it shows one.
  - States: Loading `LuxLoadingRegion("Đang tải danh sách cột", Modifier.padding(Spacing.lg)) { LuxSkeleton(fillMaxWidth().height(24.dp), shapes.small); repeat(5) { LuxSkeleton(fillMaxWidth().height(64.dp), shapes.medium) } }`; Empty `LuxEmptyState(title = "Tuyến này chưa có cột đèn nào", message = "Liên hệ Kỹ sư bảo trì nếu bạn cho rằng đây là nhầm lẫn.")`; Error `LuxErrorState(onRetry, kind, title, message)` as in Global Constraints.
  - Delete old skeleton helpers and unused imports.
- [ ] **Step 3: Run `PoleListScreenTest` + ktlint; PASS.**
- [ ] **Step 4: Commit** `style(pole-photo): move the pole list bar, sheets and states to vnext components`

---

### Task 4: Shared preview frame for photo and video

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleMediaPreviewFrame.kt`
- Create: `app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PoleMediaPreviewFrameTest.kt`
- Modify: `PolePhotoCaptureScreen.kt`, `PoleVideoCaptureScreen.kt` (replace `PolePhotoPreview`/`PoleVideoPreview` chrome, delete `RetakeButton`/`ConfirmButton` and preview color constants)

**Interfaces — Produces:** `@Composable fun PoleMediaPreviewFrame(poleId: String, isSaving: Boolean, retakeLabel: String, confirmLabel: String, onClose: () -> Unit, onRetake: () -> Unit, onConfirm: () -> Unit, media: @Composable BoxScope.() -> Unit)`

- [ ] **Step 1: Failing UI test** `PoleMediaPreviewFrameTest`:

```kotlin
@Test
fun saving_disables_every_action_and_close_keeps_a_48dp_target() {
    composeRule.setContent {
        LuxMapTheme(forceDark = true) {
            PoleMediaPreviewFrame(
                poleId = "POLE-0047",
                isSaving = true,
                retakeLabel = "Chụp lại",
                confirmLabel = "Dùng ảnh này",
                onClose = {},
                onRetake = {},
                onConfirm = {},
            ) {}
        }
    }
    composeRule.onNodeWithContentDescription("Đóng").assertIsNotEnabled().assertTouchTargetIsAtLeast(48.dp)
    composeRule.onNodeWithText("Chụp lại").assertIsNotEnabled()
    composeRule.onNodeWithText("Đang lưu…").assertExists()
    composeRule.onNodeWithText("POLE-0047").assertExists()
}

@Test
fun idle_actions_call_their_callbacks() {
    var closed = 0; var retaken = 0; var confirmed = 0
    composeRule.setContent {
        LuxMapTheme(forceDark = true) {
            PoleMediaPreviewFrame("POLE-0047", false, "Chụp lại", "Dùng ảnh này", { closed++ }, { retaken++ }, { confirmed++ }) {}
        }
    }
    composeRule.onNodeWithContentDescription("Đóng").performClick()
    composeRule.onNodeWithText("Chụp lại").performClick()
    composeRule.onNodeWithText("Dùng ảnh này").performClick()
    assertEquals(listOf(1, 1, 1), listOf(closed, retaken, confirmed))
}
```

(Check how `PrimaryButton(isLoading = true)` renders its text; if it hides the label, assert on `confirmLabel` disabled instead and ledger it.)
- [ ] **Step 2: Implement** `PoleMediaPreviewFrame`: `Column(Modifier.fillMaxSize().background(colorScheme.background))` → top `Row(statusBarsPadding().padding(horizontal = Spacing.sm, vertical = Spacing.xs), verticalAlignment = CenterVertically, spacedBy(Spacing.sm)) { IconButton(onClick = onClose, enabled = !isSaving) { Icon(Icons.Filled.Close, "Đóng", tint = colorScheme.onSurface) }; Surface(color = LuxTheme.colors.overlayOnMedia, shape = shapes.small) { Text(poleId, labelMedium, Color.White, padding(horizontal = Spacing.md, vertical = Spacing.xs)) } }` → `Box(Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg), contentAlignment = Center, content = media)` → bottom `Row(navigationBarsPadding().padding(Spacing.lg), spacedBy(Spacing.sm)) { LuxSecondaryButton(retakeLabel, onRetake, Modifier.weight(1f), enabled = !isSaving); PrimaryButton(text = if (isSaving) "Đang lưu…" else confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f), enabled = !isSaving, isLoading = isSaving) }`.
  - Photo screen: `PolePhotoPreview` body becomes `PoleMediaPreviewFrame(poleId, isSaving, retakeLabel, "Dùng ảnh này", onClose, onRetake, onConfirm) { AsyncImage(…, contentScale, Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(shapes.medium).border(1.dp, outlineVariant, shapes.medium)) }`; waiting state `LuxLoadingRegion("Đang mở camera")` on `background`.
  - Video screen: same with `"Dùng video này"` and the existing video player/thumbnail as `media` (keep its sizing logic, swap `Radius.*` for `shapes.medium`).
  - Delete `RetakeButton`, `ConfirmButton`, `PolePhotoPreviewBackground`, `PolePhotoConfirmSavingColor`, `PolePhotoButtonContentPadding`, gradient and primitive imports in both files (check the video file's own constants too).
- [ ] **Step 3: Run `PoleMediaPreviewFrameTest`, `PoleVideoFileProviderTest`, `PolePhotoCaptureViewModelTest`, `PoleVideoCaptureViewModelTest` + ktlint; PASS.**
- [ ] **Step 4: Commit** `style(pole-photo): share one vnext preview frame for photo and video`

---

### Task 5: AI result frame, decision, photo and conclusion

**Files:** `PolePhotoResultScreen.kt` (`PolePhotoResultScreen` frame, delete `PolePhotoResultHeader`, `PolePhotoDecisionCard`/`decisionTone`, restyle `PolePhotoPreview` frame, rewrite `ConclusionCard`), `PolePhotoResultScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — replace `header_shows_the_pole_id_and_the_position_badge_in_every_state` with `top_bar_shows_title_and_pole_in_every_state` (`Kết quả AI` + `Cột 3 · P-0103` for Loading, Success, Error); add `the_manager_decision_is_a_banner_above_the_photo` (decision `confirm` → text `Kỹ sư bảo trì đã xác nhận` exists and its node's top < `photo_preview`'s top); replace any assertion of `#3`. Keep `ai_verdict_label`, `photo_preview`, `photo_viewer` tags.
- [ ] **Step 2: Implement**
  - Frame: `Scaffold(topBar = { LuxChildTopBar(title = "Kết quả AI", subtitle = if (position > 0) "Cột $position · $poleId" else poleId, onBack) }, bottomBar = { pinned submit — Task 6 }, containerColor = background)`; content `Column(verticalScroll, padding(horizontal = Spacing.lg, vertical = Spacing.md), spacedBy(Spacing.lg))`; keep `TopSlideToast` overlay.
  - Stale: `LuxBanner(<same copy>, INFO)`.
  - Decision: `LuxBanner(title = <existing title>, message = decisionNote ?: "", tone = confirm→SUCCESS, reject→ERROR, else INFO)` + `Text("Lúc …", bodySmall, onSurfaceVariant)` when `decidedAt != null`. Keep existing title strings.
  - Photo frame: `shapes.medium` + `border(1.dp, outlineVariant, shapes.medium)`; height logic unchanged.
  - Conclusion: `LuxSectionHeader("Kết luận")` + `LuxCard { Text(label, titleLarge, tag ai_verdict_label); StatusBadge(asset colors for on/off/unknown); Text(verdictSourceText(...), bodyMedium, onSurfaceVariant); confidence text bodyMedium }` — reuse existing helpers (`cvStateLabel`, `verdictSourceText`, confidence text); replace `cvStateColors` with `LuxTheme.status.asset` lookups.
- [ ] **Step 3: Run `PolePhotoResultScreenTest` + ktlint; PASS (review tests may need Task 6 if they relied on the old footer layout — ledger any deferral).**
- [ ] **Step 4: Commit** `style(pole-photo): rebuild the ai result frame, decision and conclusion`

---

### Task 6: Inline review card, pinned submit, details and retake dialog

**Files:** `PolePhotoResultScreen.kt` (`ReviewFooter` → `ReviewCard` + `ReviewSubmitBar`, delete `ReviewChoiceSegment`/`ReviewChoiceCard`, restyle `TechnicalDetails`/`DetailRow`, retake `AlertDialog` → `LuxConfirmDialog`), `PolePhotoResultScreenTest.kt`.

- [ ] **Step 1: Failing UI tests**
  - `the_review_choice_is_a_radio_group` — `Đèn sáng`/`Đèn tắt` nodes have `Role.RadioButton`; selecting `Đèn tắt` marks it selected.
  - `only_the_submit_button_is_pinned` — with a long body the submit button `assertIsDisplayed()` without scrolling, while `Kỹ sư xác nhận nhãn` needs `performScrollTo()`.
  - Adapt existing review tests: replace `review_choice_on`/`review_choice_off` tag clicks with text clicks on `Đèn sáng`/`Đèn tắt` (or keep the tags on the segment buttons — prefer keeping tags via `Modifier.testTag` on each `LuxSegment` if `LuxSegmented` cannot take per-segment modifiers, click by text and ledger); replace `KỸ SƯ XÁC NHẬN NHÃN` with `Kỹ sư xác nhận nhãn`; keep note-required, draft-saved, hidden-after-decision, ai-failed cases.
- [ ] **Step 2: Implement**
  - Lift the review draft state (`selected`, `note`, baseline) from `ReviewFooter` into `PolePhotoResultScreen` with the same `rememberSaveable` keys and the same `PolePhotoReviewLogic` calls, so the card (in the scroll) and the pinned bar share it.
  - `ReviewCard`: `LuxSectionHeader("Kỹ sư xác nhận nhãn")` + `LuxCard { LuxSegmented(listOf(LuxSegment("on", "Đèn sáng"), LuxSegment("off", "Đèn tắt")), selected, onSelect); OutlinedTextField(label = { Text(if (noteRequired) "Ghi chú *" else "Ghi chú") } — the `*` in `colorScheme.error` via `buildAnnotatedString`; error line "Cần ghi chú vì khác với nhận định của AI"; draft hint "Đã lưu, sẽ gửi khi Hoàn tất" `bodySmall` }`.
  - `ReviewSubmitBar` (Scaffold `bottomBar`, only when `decision == null` and Success): `Surface(color = surface) { Column { HorizontalDivider(outlineVariant); PrimaryButton(text = reviewActionLabel(selected, cvState), enabled = <existing rule>, onClick = submit, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.lg, vertical = Spacing.md)) } }`.
  - Details: header row `clickable(role = Role.Button).semantics { stateDescription = if (expanded) "Đang mở" else "Đang đóng" }`, keep tag `details_toggle`; `DetailRow` label `bodyMedium`/`onSurfaceVariant`, value `bodyMedium`; retake `LuxSecondaryButton("Chụp lại cột này", leadingIcon = Icons.Filled.Replay)`; dialog `LuxConfirmDialog(title = "Chụp lại cột này?", message = "Ảnh hiện tại sẽ được thay bằng ảnh mới.", confirmLabel = "Chụp lại", dismissLabel = "Hủy", destructive = true)`.
- [ ] **Step 3: Run `PolePhotoResultScreenTest`, `PolePhotoReviewLogicTest` + ktlint; PASS.** Split commit if > 400 lines (card + bar first, details + dialog second).
- [ ] **Step 4: Commit** `style(pole-photo): move the label review into the content and pin only the submit button`

---

### Task 7: AI result states

**Files:** `PolePhotoResultScreen.kt` (Loading/Empty/Error, delete `PolePhotoResultSkeleton`), `PolePhotoResultScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — replace `loading_state_shows_the_skeleton` with `loading_is_one_announced_region` (`Đang tải kết quả AI`); replace `error_state_shows_the_message_and_retry_invokes_onRetry` with `a_network_error_uses_screen_copy_and_retries` (as in Task 3, title `Không tải được kết quả AI`). Keep `empty_state_shows_the_ai_unavailable_message`.
- [ ] **Step 2: Implement** Loading `LuxLoadingRegion("Đang tải kết quả AI") { LuxSkeleton(fillMaxWidth().height(photoHeight()), shapes.medium); LuxSkeleton(fillMaxWidth().height(96.dp), shapes.medium); LuxSkeleton(fillMaxWidth().height(48.dp), shapes.medium) }`; Empty `LuxEmptyState(title = "AI chưa phân loại được ảnh này", message = "Model AI gặp lỗi khi xử lý, ảnh vẫn đã lưu trên hệ thống.", icon = Icons.Filled.SmartToy)`; Error `LuxErrorState(onRetry, kind, "Không tải được kết quả AI", network/generic)`. Delete skeleton helper, `MessageState`, `StatePlaceholderHeight`, unused imports.
- [ ] **Step 3: Run `PolePhotoResultScreenTest` + ktlint; PASS.**
- [ ] **Step 4: Commit** `style(pole-photo): move the ai result states to vnext components`

---

### Task 8: Large font, full device run, cleanup, spec status

**Files:** Create `app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoLargeFontTest.kt`; modify spec status line.

- [ ] **Step 1: Large-font tests** (1.5×, 360dp, `HomeLargeFontTest` pattern):
  - Pole list: Success with a long pole id, `FAILED` row and a `DONE`+photo row; the AI row (`hasClickAction() and hasText(id, substring = true)`) and `Hoàn tất` ≥ 48dp; `Đồng bộ lỗi` displayed.
  - AI result: Success, no decision, `cvState = "off"`; `Đèn sáng` and the pinned submit button ≥ 48dp; submit `assertIsDisplayed()`.
- [ ] **Step 2: Run device package `com.luxmap.feature.survey.ui.polephoto` + unit `com.luxmap.feature.survey.ui.polephoto.*` + ktlint; all PASS** (excluding the known-environment emulator issue — device is SM-A075F).
- [ ] **Step 3: Cleanup grep** in the 5 screen files for `Navy`, `Blue[0-9]`, `Gray[0-9]`, `Color(0x`, `TextStyle`, `Radius.`, `Gradient`, `Brush`, `MessageState`, `InlineBanner`, ALL-CAPS literals; remove leftovers (white on `overlayOnMedia` is allowed).
- [ ] **Step 4: Spec status** → `**ĐÃ DUYỆT VÀ ĐÃ CODE XONG** trên nhánh style/redesign-pole-photo-vnext. Còn nợ: kiểm tra tay trên máy đã đăng nhập (Light/Night Field, 200%), chụp/quay thật.`
- [ ] **Step 5: Commit** `test(pole-photo): cover the pole list and ai result at large font`
