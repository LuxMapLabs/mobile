# LuxMap Redesign Phase 5: F04 Chế Độ Quay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle `CaptureScreen` (F04, the night survey-capture screen) to the v2.1 redesign: a new `CaptureWarning` component for the GPS-lost/BLE-gap banners, a persistent top HUD chip row (GPS/lux/storage) visible across every pre-recording state instead of Recording-only, a solid `Night` bottom panel with rounded top corners instead of a translucent scrim, a hold-to-stop gesture with a circle→square shape morph on the stop button (spec §6), and copy fixes (the real `GpsAccuracyGate` numbers instead of an illustrative "≤10m"). Every camera/device side effect already in this file — `BackHandler`, the orientation lock, `FLAG_KEEP_SCREEN_ON`, the `TextureView`/`Surface` lifecycle, the haptic `warningPulses` collector, the `onSessionPackaged` effect — is carried over **unchanged**; this plan never touches `CaptureViewModel.kt`.

**Architecture:** Two new shared components (`CaptureWarning`, `HudChip`), a restructure of `CaptureScreen.kt`'s bottom-panel `when` branches into named, stateless sub-composables so they can be driven by tests directly (same pattern as `SurveyPlanContent`), and one new gesture handler (hold-to-stop) built entirely with `pointerInput` + `Animatable` — no new ViewModel methods, no new `CaptureUiState` fields.

**Tech Stack:** Kotlin, Jetpack Compose Material3, `pointerInput`/`detectTapGestures`-equivalent low-level gesture APIs already available in the Compose version this project uses. No new dependencies.

**Spec:** `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (Phase 5 row) and `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` lines 137 (`CaptureWarning` component row), 190-196 (F04 section, codes 1k-1o), and the "Record → Stop" line in §6 (Tương tác & chuyển động). Screen Review items A3/A4 (`docs/LuxMap_Mobile_Screen_Review_2026-10-11.md`). Current-code findings this plan is built from: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt` (375 lines, read in full), `CaptureUiState.kt` (confirms every state the spec asks for — `Connecting`, `Scanning`, `ScanTimedOut`, `Ready`, `StartingRecording`, `Recording`, `Packaging`, `Packaged`, `PackagingFailed` — already exists; Screen Review section B's "missing states" are states missing from the *design prototype*, not from this code), `GpsAccuracyGate.kt` (confirms the real numbers: `PROPOSED_THRESHOLD_METERS = 8f`, `PROPOSED_HOLD_DURATION_MS = 3_000L`), `GpsAccuracyIndicator.kt`.

**A3/A4 status check (done during planning, not re-litigated in a task):** A3 (hardcoded "≤10m" vs. the real gate) — real, `CaptureScreen.kt:276-277` already reads `GpsAccuracyGate.PROPOSED_THRESHOLD_METERS` correctly, but never mentions the 3-second hold condition; Task 2 fixes the copy to state both numbers. A4 ("BLE" → "Bluetooth" labels) — checked, this file's current user-facing strings already say "cảm biến ánh sáng" everywhere and never say "BLE"; no change needed here (A4's actual violation is elsewhere, already resolved per `docs/contract-drift.md`'s "Lux sensor connection technology" row).

## Global Constraints

- Branch off `dev` (`git checkout dev && git pull`), never `main`. Branch: `style/redesign-phase5-capture-mode`.
- Every commit ≤ 400 changed lines (CLAUDE.md). Expect one commit per task (5 total).
- `./gradlew ktlintCheck` after every task, fix before moving on.
- **`CaptureViewModel.kt` is not touched by this plan at all** — no new methods, no new `CaptureUiState` fields, no changed method signatures. Every restyle reads the exact same state shape that exists today.
- The following side effects in `CaptureScreen.kt` are copied forward **byte-for-byte**, never refactored or "cleaned up" as part of a restyle: the `warningPulses` `LaunchedEffect`, the `isCameraSessionLive` computation, `BackHandler(enabled = isCameraSessionLive)`, both `DisposableEffect(isCameraSessionLive)` blocks (orientation lock, `FLAG_KEEP_SCREEN_ON`), the `packagedSessionId` `LaunchedEffect`, and the entire `AndroidView`/`TextureView`/`SurfaceTexture` block including its comments. If a task's diff touches any line inside these blocks for a reason other than pure reordering around them, that is a plan defect — stop and flag it rather than continuing.
- The hold-to-stop gesture must still call `viewModel::onStopRecording` **exactly once**, after the full hold duration, with no change to what that call does — this plan changes *how the user triggers it*, never what it triggers.
- **Known, logged data gap, not invented:** the spec's HUD chip row asks for "AE khoá + shutter/ISO" (live exposure-lock status + the current shutter speed/ISO values). `CaptureUiState.Recording` carries no such fields today — only `CaptureConfigWriter`/`VideoCaptureSession.stop()` read real `CaptureResult` values, and only once, at the very end of the session, for the manifest. This plan does **not** add a live shutter/ISO field to `CaptureUiState` (that is a `CaptureViewModel`/`VideoCaptureSession` change, out of scope) and does **not** fabricate a value — Task 2 ships the HUD row with 3 chips (GPS, lux, storage) and Task 5 logs the missing 4th chip in `docs/contract-drift.md` as a real gap.

## Review Focus

- **Nothing inside the camera-critical blocks changes.** Confirmed by `git diff` showing zero line changes inside the 6 blocks named in the Global Constraints above, checked at the end of every task, not just the last one.
- **The hold-to-stop gesture must not fire on a normal tap.** A tap shorter than the hold duration must call neither `onStopRecording` nor leave the button in a stuck "mid-hold" visual state — releasing early must cleanly reset the progress ring to 0 and the label back to "Nhấn giữ để dừng quay". A test must cover both the completed-hold and the released-early paths.
- **HUD chips must not claim data the state doesn't have.** The GPS/lux/storage chips read `null` gracefully (already true of `GpsAccuracyIndicator`/the existing lux/storage text) — a test must confirm the HUD row renders without crashing when every optional field is `null` (the exact shape of a freshly-entered `Recording` state before the first sensor sample arrives).
- **Every state the spec and the current code both already have must still render after the restructure.** The restructure splits one big `when` into named sub-composables — a test must cover at least one branch per state (9 states) to catch a state silently dropped during the extraction, not just the ones this plan visually changes the most (`Recording`, `Ready`).
- **The restyled bottom panel must not break on a narrow screen.** Spec's responsive section sets a 360×800dp baseline with 320dp as the narrow case — the hold-to-stop button and HUD chips must not use a fixed width that overflows at 320dp. This is a visual judgment call to make while implementing, not a separate test (no screenshot tooling in this environment).

---

### Task 1: Add the shared `CaptureWarning` component

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/CaptureWarning.kt`

**Interfaces:**
- Consumes: `AssetCondition.DIM.badgeColors(isDark = true)` (existing — `#4A3310`/`#F7C66D`, exactly the spec's `CaptureWarning` row colors), `Radius.control` (14dp, matches the spec's "Radius 14").
- Produces: `CaptureWarning(message: String, modifier: Modifier = Modifier)` — consumed by Task 4.

- [ ] **Step 1: Create the file**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors

// §3 CaptureWarning: GPS-lost / lux-sensor-gap banner for F04. Reuses AssetCondition.DIM's dark
// pair (#4A3310/#F7C66D, same hex the spec's own component row gives) instead of a new token.
@Composable
fun CaptureWarning(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = AssetCondition.DIM.badgeColors(isDark = true)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(Radius.control))
                .padding(Spacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Filled.WarningAmber, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text = message, style = LabelTextStyle, color = colors.text)
    }
}
```

- [ ] **Step 2: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL. If ktlint flags the `Icon(...)` line for length, wrap its arguments onto separate lines the same way other components in this codebase do.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/CaptureWarning.kt
git commit -m "feat(redesign-phase5): add CaptureWarning shared component"
```

---

### Task 2: Add `HudChip`, build the persistent top HUD row, fix the GPS copy

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/HudChip.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/capture/CaptureScreenTest.kt` (new file)

**Interfaces:**
- Produces: `HudChip(text: String, modifier: Modifier = Modifier)` — a small pill, consumed by the new `CaptureHudRow` composable in `CaptureScreen.kt` (this task) and by Task 3.
- Produces: `CaptureHudRow(gpsAccuracyMeters: Float?, latestLuxValue: Float?, freeStorageBytes: Long?, modifier: Modifier = Modifier)` — a private composable in `CaptureScreen.kt`, consumed by this task's own restructure.

- [ ] **Step 1: Write the failing test**

Create `CaptureScreenTest.kt`:

```kotlin
package com.luxmap.feature.survey.ui.capture

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Rule
import org.junit.Test

class CaptureScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun hud_row_shows_the_lux_value_when_present() {
        composeRule.setContent {
            LuxMapTheme { CaptureHudRow(gpsAccuracyMeters = 5f, latestLuxValue = 12.3f, freeStorageBytes = 500_000_000L) }
        }
        composeRule.onNodeWithText("12.3 lux").assertExists()
    }

    @Test
    fun hud_row_does_not_crash_with_every_value_null() {
        composeRule.setContent {
            LuxMapTheme { CaptureHudRow(gpsAccuracyMeters = null, latestLuxValue = null, freeStorageBytes = null) }
        }
        composeRule.onNodeWithText("Chưa có dữ liệu lux").assertExists()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `CaptureHudRow` is unresolved (it does not exist yet, and is not yet `internal`/visible to the test).

- [ ] **Step 3: Create `HudChip.kt`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

// §3 HUD chip: a small pill floating directly over the full-bleed camera preview (F04) - each
// chip carries its own background instead of a shared status-bar strip, since the spec's F04
// section describes HUD chips sitting individually over the live feed, not inside one bar.
private val HudChipBackground = Color(0x991A1C22)

@Composable
fun HudChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = LabelTextStyle,
        color = Color.White,
        modifier =
            modifier
                .background(HudChipBackground, RoundedCornerShape(Radius.chip))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
```

- [ ] **Step 4: Restructure `CaptureScreen.kt`'s top status row into `CaptureHudRow`**

Replace the current Recording-only HUD block (currently lines ~202-228, the `if (uiState is CaptureUiState.Recording) { Row(...) { GpsAccuracyIndicator(...); Text(...); Text(...) } }` block) with a call that shows for every pre-packaging state, not only `Recording`:

```kotlin
            if (uiState is CaptureUiState.Ready || uiState is CaptureUiState.StartingRecording || uiState is CaptureUiState.Recording) {
                val (gpsAccuracyMeters, latestLuxValue, freeStorageBytes) =
                    when (val state = uiState) {
                        is CaptureUiState.Ready -> Triple(state.gpsAccuracyMeters, null, null)
                        is CaptureUiState.Recording -> Triple(state.gpsAccuracyMeters, state.latestLuxValue, state.freeStorageBytes)
                        else -> Triple(null, null, null)
                    }
                CaptureHudRow(
                    gpsAccuracyMeters = gpsAccuracyMeters,
                    latestLuxValue = latestLuxValue,
                    freeStorageBytes = freeStorageBytes,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(Spacing.md),
                )
            }
```

Add `CaptureHudRow` (visible to the test, so `internal`, not `private`) right after `CaptureScreen`'s closing brace (before `formatDuration`):

```kotlin
@Composable
internal fun CaptureHudRow(
    gpsAccuracyMeters: Float?,
    latestLuxValue: Float?,
    freeStorageBytes: Long?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        HudChip(text = gpsAccuracyLabel(gpsAccuracyMeters))
        HudChip(text = latestLuxValue?.let { "%.1f lux".format(it) } ?: "Chưa có dữ liệu lux")
        HudChip(text = freeStorageBytes?.let { "${it / 1_000_000} MB trống" } ?: "— MB trống")
    }
}
```

Add imports: `com.luxmap.core.ui.components.HudChip`, `com.luxmap.core.ui.components.gpsAccuracyLabel` (the label function `GpsAccuracyIndicator.kt` already exposes at file scope). Remove the now-unused `GpsAccuracyIndicator` import if ktlint/the compiler flags it (the HUD row now uses `gpsAccuracyLabel(...)` directly inside a `HudChip`, not the `GpsAccuracyIndicator` composable itself, since that composable's own text style doesn't match the chip look).

- [ ] **Step 5: Fix the GPS readiness copy (Screen Review A3)**

Replace, in the `is CaptureUiState.Ready ->` branch:

```kotlin
                            "Độ chính xác hiện tại: %.0fm (cần ≤%.0fm)"
                                .format(it, GpsAccuracyGate.PROPOSED_THRESHOLD_METERS)
```

with:

```kotlin
                            "Độ chính xác hiện tại: %.0fm (cần ≤%.0fm, giữ ổn định %ds)"
                                .format(
                                    it,
                                    GpsAccuracyGate.PROPOSED_THRESHOLD_METERS,
                                    GpsAccuracyGate.PROPOSED_HOLD_DURATION_MS / 1000,
                                )
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/HudChip.kt app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/capture/CaptureScreenTest.kt
git commit -m "feat(redesign-phase5): add persistent HUD chip row, fix GPS gate copy"
```

---

### Task 3: Restyle the bottom panel to a solid `Night` sheet, extract per-state content

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/capture/CaptureScreenTest.kt`

**Interfaces:**
- Produces: `CaptureBottomPanelContent(state: CaptureUiState, surveySweepId: String, workOrderId: String, viewModel: CaptureViewModel, modifier: Modifier = Modifier)` — `internal`, consumed by this task's own `CaptureScreen` call site and by Task 4's tests. (Keeping the real `viewModel` reference here, not a pile of individual lambdas, matches this file's existing style — every branch already calls `viewModel::xxx` directly; splitting each into its own lambda parameter would be a much larger diff for no behavior change.)

- [ ] **Step 1: Write the failing tests — one per state that must survive the extraction**

Add to `CaptureScreenTest.kt`:

```kotlin
    @Test
    fun scanning_with_devices_lists_them() {
        val viewModel = mockk<CaptureViewModel>(relaxed = true)
        composeRule.setContent {
            LuxMapTheme {
                CaptureBottomPanelContent(
                    state = CaptureUiState.Scanning(devices = listOf(LuxDevice(address = "AA:BB", name = "LuxMap_ESP32"))),
                    surveySweepId = "SWEEP-1",
                    workOrderId = "WO-1",
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNodeWithText("LuxMap_ESP32").assertExists()
    }

    @Test
    fun recording_with_a_gps_and_ble_warning_shows_both_capture_warnings() {
        val viewModel = mockk<CaptureViewModel>(relaxed = true)
        composeRule.setContent {
            LuxMapTheme {
                CaptureBottomPanelContent(
                    state = CaptureUiState.Recording(gpsSignalLost = true, bleGapDetected = true),
                    surveySweepId = "SWEEP-1",
                    workOrderId = "WO-1",
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNodeWithText("Mất tín hiệu GPS").assertExists()
        composeRule.onNodeWithText("Mất kết nối cảm biến ánh sáng — vẫn tiếp tục quay").assertExists()
    }

    @Test
    fun packaging_failed_shows_the_reason_and_a_dismiss_action() {
        val viewModel = mockk<CaptureViewModel>(relaxed = true)
        composeRule.setContent {
            LuxMapTheme {
                CaptureBottomPanelContent(
                    state = CaptureUiState.PackagingFailed(reason = "Hết dung lượng"),
                    surveySweepId = "SWEEP-1",
                    workOrderId = "WO-1",
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNodeWithText("Không thể hoàn tất phiên khảo sát: Hết dung lượng").assertExists()
        composeRule.onNodeWithText("Dừng và đóng phiên").assertExists()
    }
```

Add imports to `CaptureScreenTest.kt`: `io.mockk.mockk`, `com.luxmap.core.ble.LuxDevice` (`CaptureUiState`/`CaptureViewModel`/`CaptureBottomPanelContent` need no import — same package as the test). `mockk<CaptureViewModel>(relaxed = true)` works here (not in a plain JVM `test` sourceset) because this is an `androidTest`, which already depends on `mockk-android` for exactly this — mocking a final Kotlin class on a real device/emulator runtime (same pattern as `ExposureLockControllerTest.kt`). A relaxed mock is enough because every branch only calls `viewModel::xxxx` as a click handler and never reads further `CaptureViewModel` state.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `CaptureBottomPanelContent` does not exist yet.

- [ ] **Step 3: Extract `CaptureBottomPanelContent`, restyle the panel background**

Replace the bottom `Column` in `CaptureScreen` (currently `Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)).padding(Spacing.lg)` wrapping the whole `when (val state = uiState) { ... }` block) with:

```kotlin
            CaptureBottomPanelContent(
                state = uiState,
                surveySweepId = surveySweepId,
                workOrderId = workOrderId,
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
```

Add, after `CaptureHudRow` (from Task 2):

```kotlin
@Composable
internal fun CaptureBottomPanelContent(
    state: CaptureUiState,
    surveySweepId: String,
    workOrderId: String,
    viewModel: CaptureViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is CaptureUiState.Connecting -> {
                val label = state.deviceName?.let { "Đang kết nối tới $it..." } ?: "Đang kết nối cảm biến ánh sáng..."
                Text(label, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = viewModel::onChangeDevice) { Text("Đổi thiết bị khác") }
            }

            is CaptureUiState.Scanning ->
                if (state.devices.isEmpty()) {
                    Text("Đang tìm cảm biến ánh sáng gần đây...", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("Chọn cảm biến ánh sáng:", style = MaterialTheme.typography.bodyLarge)
                    LazyColumn {
                        items(state.devices) { device ->
                            DeviceRow(device = device, onClick = { viewModel.onDeviceSelected(device) })
                        }
                    }
                }

            is CaptureUiState.ScanTimedOut -> {
                Text(
                    "Không tìm thấy cảm biến ánh sáng nào gần đây. Kiểm tra pin và khoảng cách rồi thử lại.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrimaryButton(text = "Quét lại", onClick = viewModel::onRetryScan, modifier = Modifier.fillMaxWidth())
            }

            is CaptureUiState.Ready -> {
                if (!state.gpsReadyToRecord) {
                    val accuracyLabel =
                        state.gpsAccuracyMeters?.let {
                            "Độ chính xác hiện tại: %.0fm (cần ≤%.0fm, giữ ổn định %ds)"
                                .format(
                                    it,
                                    GpsAccuracyGate.PROPOSED_THRESHOLD_METERS,
                                    GpsAccuracyGate.PROPOSED_HOLD_DURATION_MS / 1000,
                                )
                        } ?: "Đang chờ tín hiệu GPS..."
                    Text(accuracyLabel, style = MaterialTheme.typography.bodyLarge)
                }
                PrimaryButton(
                    text = "Bắt đầu quay",
                    onClick = { viewModel.onStartRecording(surveySweepId, workOrderId) },
                    enabled = state.gpsReadyToRecord,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = viewModel::onChangeDevice) { Text("Đổi thiết bị khác") }
            }

            is CaptureUiState.StartingRecording ->
                Text("Đang mở camera...", style = MaterialTheme.typography.bodyLarge)

            is CaptureUiState.Recording -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("● REC", color = Danger600, style = MaterialTheme.typography.labelLarge)
                    Text(formatDuration(state.durationSeconds), style = MaterialTheme.typography.labelLarge)
                    Text("%.2f km".format(state.distanceMeters / 1000f), style = MaterialTheme.typography.labelLarge)
                }
                if (state.gpsSignalLost) {
                    CaptureWarning(message = "Mất tín hiệu GPS", modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                }
                if (state.bleGapDetected) {
                    CaptureWarning(
                        message = "Mất kết nối cảm biến ánh sáng — vẫn tiếp tục quay",
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    )
                }
                HoldToStopButton(onHoldComplete = viewModel::onStopRecording, modifier = Modifier.padding(top = Spacing.md))
            }

            is CaptureUiState.Packaging ->
                Text("Đang đóng gói phiên khảo sát...", style = MaterialTheme.typography.bodyLarge)

            is CaptureUiState.Packaged ->
                Text("Đã đóng gói xong", style = MaterialTheme.typography.bodyLarge)

            is CaptureUiState.PackagingFailed -> {
                Text(
                    "Không thể hoàn tất phiên khảo sát: ${state.reason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrimaryButton(text = "Dừng và đóng phiên", onClick = viewModel::onDismissFailure, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
```

(`HoldToStopButton` is a forward reference to Task 4 — this task will not compile on its own until Task 4 lands; both tasks' steps must be done in order, same session, before the first build/test check. `PackagingFailed`'s panel background is `MaterialTheme.colorScheme.background` even though that branch can be reached without ever opening the camera — the spec gives this screen one visual language throughout, not a special case for the no-camera path.)

Remove the now-unused `Icons.Filled.WarningAmber` import if the compiler flags it (it moved into `CaptureWarning.kt`); add `com.luxmap.core.theme.Radius`, `com.luxmap.core.ui.components.CaptureWarning`, `com.luxmap.core.ui.components.PrimaryButton`, `androidx.compose.foundation.shape.RoundedCornerShape` if not already imported.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: still fails to compile at this point, because `HoldToStopButton` does not exist until Task 4 — this is expected; proceed directly to Task 4 before running this check for real. Note this in the commit step below: Tasks 3 and 4 commit together.

- [ ] **Step 5: Do not commit yet** — continue to Task 4, then commit both together (the plan's own forward reference makes Task 3 non-buildable alone).

---

### Task 4: Hold-to-stop gesture with a circle→square shape morph

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/capture/CaptureScreenTest.kt`

**Interfaces:**
- Produces: `HoldToStopButton(onHoldComplete: () -> Unit, modifier: Modifier = Modifier)` — `internal`, consumed by Task 3's `CaptureBottomPanelContent`.

- [ ] **Step 1: Write the failing tests**

Add to `CaptureScreenTest.kt`:

```kotlin
    @Test
    fun releasing_the_hold_button_early_does_not_call_onHoldComplete() {
        var completed = false
        composeRule.setContent {
            LuxMapTheme { HoldToStopButton(onHoldComplete = { completed = true }) }
        }
        composeRule.onNodeWithText("Nhấn giữ để dừng quay").assertExists()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Nhấn giữ để dừng quay").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.onNodeWithText("Nhấn giữ để dừng quay").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(HOLD_TO_STOP_DURATION_MS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertFalse(completed)
        composeRule.onNodeWithText("Nhấn giữ để dừng quay").assertExists()
    }

    @Test
    fun holding_for_the_full_duration_calls_onHoldComplete() {
        var completed = false
        composeRule.setContent {
            LuxMapTheme { HoldToStopButton(onHoldComplete = { completed = true }) }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Nhấn giữ để dừng quay").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(HOLD_TO_STOP_DURATION_MS + 100L)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue(completed)
    }
```

Add imports to `CaptureScreenTest.kt`: `androidx.compose.ui.test.performTouchInput`, `org.junit.Assert.assertFalse`, `org.junit.Assert.assertTrue`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `HoldToStopButton` and `HOLD_TO_STOP_DURATION_MS` are unresolved.

- [ ] **Step 3: Implement `HoldToStopButton`**

Add to `CaptureScreen.kt`, after `CaptureBottomPanelContent`:

```kotlin
internal const val HOLD_TO_STOP_DURATION_MS = 1_500L

@Composable
internal fun HoldToStopButton(
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val label = if (progress.value > 0f) "Tiếp tục giữ…" else "Nhấn giữ để dừng quay"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                modifier
                    .size(84.dp)
                    .pointerInput(onHoldComplete) {
                        awaitEachGesture {
                            awaitFirstDown()
                            val job =
                                scope.launch {
                                    progress.animateTo(1f, animationSpec = tween(HOLD_TO_STOP_DURATION_MS.toInt(), easing = LinearEasing))
                                    onHoldComplete()
                                }
                            waitForUpOrCancellation()
                            job.cancel()
                            scope.launch { progress.snapTo(0f) }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = Danger600,
                strokeWidth = 4.dp,
                trackColor = Danger600.copy(alpha = 0.25f),
            )
            val innerCornerRadius by animateDpAsState(targetValue = if (progress.value > 0f) 8.dp else 32.dp, label = "stopShape")
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(Danger600, RoundedCornerShape(innerCornerRadius)),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
```

Add imports: `androidx.compose.animation.core.Animatable`, `androidx.compose.animation.core.LinearEasing`, `androidx.compose.animation.core.animateDpAsState`, `androidx.compose.animation.core.tween`, `androidx.compose.foundation.gestures.awaitEachGesture`, `androidx.compose.foundation.gestures.awaitFirstDown`, `androidx.compose.foundation.gestures.waitForUpOrCancellation`, `androidx.compose.foundation.layout.size`, `androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.material3.CircularProgressIndicator`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch` (`androidx.compose.ui.unit.dp` is already imported in this file).

(This replaces the current plain `Button(onClick = viewModel::onStopRecording, modifier = Modifier.defaultMinSize(minHeight = 64.dp)) { Text("Dừng quay") }` call inside the `Recording` branch — already removed from `CaptureBottomPanelContent` above in favor of `HoldToStopButton(onHoldComplete = viewModel::onStopRecording, ...)`, so there is nothing left to delete here; this step only adds the new function.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL — this also resolves Task 3's forward reference, so compile both together now.

- [ ] **Step 5: Commit Tasks 3 and 4 together**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/capture/CaptureScreenTest.kt
git commit -m "feat(redesign-phase5): restyle bottom panel to a Night sheet, add hold-to-stop gesture"
```

---

### Task 5: Log the HUD data gap, final build, open PR

**Files:**
- Modify: `docs/contract-drift.md`

- [ ] **Step 1: Add the HUD gap row**

```markdown
| F04 HUD row missing "AE khoá + shutter/ISO" chip | `CaptureHudRow` (Phase 5) ships 3 of the spec's 4 chips: GPS, lux, free storage | **Known gap, not fabricated (2026-10-12)** — `CaptureUiState.Recording` carries no live shutter-speed/ISO/AE-lock-status field; only `VideoCaptureSession.stop()` reads a real `CaptureResult` once, at the end of the session, for the manifest. Adding a live 4th chip needs `CaptureViewModel`/`VideoCaptureSession` to start streaming those values during recording, which is out of this restyle-only phase's scope | WP6 |
```

- [ ] **Step 2: Full build, ktlint, androidTest compile check**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Grep to confirm the camera-critical blocks are untouched**

Run: `git diff dev..HEAD -- app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt | grep -A5 -B5 "BackHandler\|FLAG_KEEP_SCREEN_ON\|SCREEN_ORIENTATION_LOCKED\|warningPulses\|onSessionPackaged\|SurfaceTextureListener"`
Expected: every matched block shows only context lines (no `+`/`-` inside them) — if any do, that is a plan violation per the Global Constraints; stop and revert that specific hunk before continuing.

- [ ] **Step 4: Commit**

```bash
git add docs/contract-drift.md
git commit -m "docs(redesign-phase5): log missing HUD shutter/ISO chip gap"
```

- [ ] **Step 5: Open PR**

Target `dev`. Title: `feat(redesign-phase5): restyle F04 capture mode screen`. Body must flag: no adb/emulator in this environment (every `androidTest` file in this plan is compile-verified only, including the `HoldToStopButton` gesture tests, which specifically need a real touch-input pipeline to prove), and that the camera-critical side effects were verified untouched via the Step 3 grep above (quote its clean result in the PR body).
