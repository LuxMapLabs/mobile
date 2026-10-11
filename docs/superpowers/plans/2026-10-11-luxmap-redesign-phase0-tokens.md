# LuxMap Redesign Phase 0: Design Token Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `core/theme` (`Color.kt`, `Theme.kt`) from Design System v2.0's brand/semantic palette to the approved v2.1 "Night Ledger" palette (Paper/Ink/Lumen), while leaving status colors (asset condition, sync, work-order status/priority) and the existing M3 `Typography`/`Dimens` slot *meanings* untouched, so this phase recolors every screen without silently breaking any existing layout that depends on a specific spacing/type value.

**Architecture:** Additive-then-swap. Task 1 adds the new v2.1 primitives to `Color.kt` next to the existing ones (nothing deleted yet — `BrandHeroGradient`/`Navy900`/`Blue600` stay until Phase 11 decides F01's hero color, per the roadmap's open conflict). Task 2 repoints `LightSemanticColors`/`DarkSemanticColors` at the new primitives, which is the one change that recolors the whole app, because every existing screen already reads background/surface/text through `LuxMapSemanticColors` → `MaterialTheme.colorScheme`. Task 3 extends `Theme.kt`'s M3 `ColorScheme` mapping with the roles the spec calls out explicitly (`primaryContainer`, `surfaceContainerHigh`, `outlineVariant`, dark-mode `error`) that today fall back to M3 defaults. Task 4 only *adds* new `Dimens`/`Radius` values the spec needs for new components (Phase 1+); it does not change any existing `Spacing`/`Dimens` value, because `Spacing.xxxl` already has a different value (40dp) than the spec's proposal (32dp) and this plan doesn't have call-site evidence yet that changing it is safe. Task 5 adds the new typography scale (`numeric`/`display`/`headline`/`metric`/`title`/`bodyStrong`/`body`/`label`/`overline`/`badge`) as new named `TextStyle` vals, not by redefining the M3 `Typography` object's existing slots — redefining e.g. `bodyMedium`'s meaning (16sp/400 body text today → 14sp/500 metadata in the spec's table) would silently re-skin every existing `MaterialTheme.typography.bodyMedium` call site to the wrong size with no compile error. Later phases migrate their own screens' `Text()` calls to the correct new token explicitly; a final cleanup phase (not in this plan) can retire the old M3 slot meanings once nothing reads them.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`lightColorScheme`/`darkColorScheme`), no new dependencies.

**Spec:** `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (this phase's entry, including the two flagged cross-doc conflicts); primary design spec read from the Claude Design project `82538650-3a87-4ecd-8cbc-37aaf17badc1`, files `handoff/LuxMap_Redesign_Spec.md` §2 and `handoff/LuxMap_Design_System_v2.1.md` §1–3 (both fetched 2026-10-11, quoted inline in the tasks below since they're not yet copied into this repo's `docs/`).

## Global Constraints

- Branch off `dev`, from a fresh `git checkout dev && git pull`. Branch name: `style/fm-xx-redesign-phase0-tokens` (confirm the real `FM-xx` task code against `docs/LuxMap_TaskList_v2.xlsx` / WP lead before opening the PR — this plan doesn't invent one).
- Every commit ≤ 400 changed lines (CLAUDE.md). This phase's total diff is small (two files plus one new file for Task 4/5's additions); expect 1–2 commits, not 5.
- `./gradlew ktlintCheck` after every task, fix before moving on.
- No new comments unless the WHY is genuinely non-obvious (this user's standing feedback) — the existing Vietnamese section-header comments in `Color.kt`/`Dimens.kt` stay as-is (out of scope for this phase; don't touch unrelated lines).
- Status-color enums (`AssetCondition`, `SyncStatus`, `WorkOrderStatus`, `WorkOrderPriority`, `WorkOrderTaskKind`) and their `badgeColors()`/`label()`/`icon()` functions in `Color.kt` are **not touched** by this phase at all — the spec explicitly keeps them.
- Default for the `Disabled` token conflict (see roadmap): use `LuxMap_Design_System_v2.1.md`'s table (light container `#E2E8F0` / content `#475569`, dark container `#2A2A2A` / content `#A0A0A0`), not `LuxMap_Redesign_Spec.md`'s inline primitive pair. Flag this choice in the PR description for a quick owner glance.

## Review Focus

- A screen that reads `MaterialTheme.colorScheme.primary` expecting the old `Navy700` (e.g. any default-styled `Button`) will now render `Lumen`/`LumenNight` — this is intended by Task 2/3, but is exactly the kind of change that needs one full-app visual pass (not just the touched files) before this PR merges, since every default-M3-colored component shifts at once.
- `error`/`onError` in dark mode currently falls back to the same `Danger600` as light mode (Task 3 adds the dark-specific `#FF9A9A` the spec calls for) — pin this with a quick check that `LuxMapDarkColorScheme.error` is no longer equal to `LuxMapLightColorScheme.error` after Task 3.
- `Spacing.xxxl` and `Dimens.radiusSmall` (used by `StatusBadge` today) must **not** change value in this phase — Task 4 is additive-only; a reviewer should be able to diff `Dimens.kt` and see only new lines, no edited existing numbers.
- The five existing M3 slots the spec's new typography table reassigns (`titleMedium`, `labelLarge`, `bodySmall`, `labelSmall`, and implicitly `bodyMedium`'s new neighbor `bodyLarge`) must still resolve to their *current* `TextStyle` values after Task 5 — a reviewer should be able to diff `Type.kt` and see the existing `Typography(...)` block completely unchanged, with only new top-level vals added below it.
- `LuxMapSemanticColors.focus`/`navigationActive`/`navigationInactive` aren't given explicit values anywhere in the spec's M3-role table (that table maps M3 `ColorScheme` roles, not this app's own semantic data class) — Task 2 documents the chosen mapping (`focus`→Lumen/LumenNight replacing Blue500's old "link/focus/info" role; `navigationActive`→Lumen/LumenNight; `navigationInactive`→Ink2/NightText2, matching the old Gray500/DarkMuted pattern) so Phase 1's `AppNavigationBar` restyle isn't guessing at an undocumented decision.

---

### Task 1: Add v2.1 color primitives to `Color.kt`

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Color.kt:35` (insert after the existing `DarkMuted` primitive, before the "Extra primitives" comment block at line 37)

**Interfaces:**
- Produces: new top-level `Color` vals — `Paper`, `Ink`, `Ink2`, `Line`, `LineSoft`, `DisabledContainer`, `DisabledContent`, `DisabledContainerDark`, `DisabledContentDark`, `Lumen`, `LumenCont`, `LumenOnCont`, `Night`, `NightSurf`, `NightSurf2`, `NightLine`, `NightText`, `NightText2`, `LumenNight`, `LumenNightCont`, `LumenNightOnCont`, `ErrorDark` (the dark-mode `#FF9A9A` error color) — consumed by Task 2 and Task 3.

- [ ] **Step 1: Add the new primitives**

Insert this block in `Color.kt` right after `val DarkMuted = Color(0xFFA0A0A0)` (currently line 35):

```kotlin
// v2.1 redesign primitives — "Night Ledger" palette, replaces the brand/semantic layer above.
// Status colors (AssetCondition/SyncStatus/WorkOrderStatus/WorkOrderPriority) are unchanged.
val Paper = Color(0xFFF5F4F0)
val Ink = Color(0xFF15171C)
val Ink2 = Color(0xFF5B5F68)
val Line = Color(0xFFDEDBD3)
val LineSoft = Color(0xFFECEAE4)
val DisabledContainer = Color(0xFFE2E8F0)
val DisabledContent = Color(0xFF475569)
val DisabledContainerDark = Color(0xFF2A2A2A)
val DisabledContentDark = Color(0xFFA0A0A0)
val Lumen = Color(0xFF3448D6)
val LumenCont = Color(0xFFE4E7FB)
val LumenOnCont = Color(0xFF1B2A8F)
val Night = Color(0xFF0B0C0F)
val NightSurf = Color(0xFF16181D)
val NightSurf2 = Color(0xFF1F2128)
val NightLine = Color(0xFF2C2F38)
val NightText = Color(0xFFEDEBE6)
val NightText2 = Color(0xFF9A9DA6)
val LumenNight = Color(0xFFA3ADFF)
val LumenNightCont = Color(0xFF262C5E)
val LumenNightOnCont = Color(0xFFDADEFF)
val ErrorDark = Color(0xFFFF9A9A)
```

- [ ] **Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (new vals are unused so far, which is fine — Kotlin doesn't warn on unused top-level `val`).

- [ ] **Step 3: ktlint**

Run: `./gradlew ktlintCheck`
Expected: no violations. If there are import-order or formatting issues, run `./gradlew ktlintFormat` and re-check the diff only touches `Color.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Color.kt
git commit -m "feat(fm-xx): add v2.1 redesign color primitives"
```

---

### Task 2: Repoint `LightSemanticColors`/`DarkSemanticColors` at the new primitives

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Color.kt:64-88` (the `LightSemanticColors`/`DarkSemanticColors` value assignments)

**Interfaces:**
- Consumes: primitives from Task 1 (`Paper`, `Ink`, `Ink2`, `Line`, `LineSoft`, `Lumen`, `LumenNight`, `Night`, `NightSurf`, `NightSurf2`, `NightText`, `NightText2`, `NightLine`).
- Produces: `LightSemanticColors`/`DarkSemanticColors` keep their existing `LuxMapSemanticColors` shape (no field added/removed) — Task 3 and every existing screen that reads `MaterialTheme.colorScheme.*` consume the result unchanged in type, changed in value.

- [ ] **Step 1: Update `LightSemanticColors`**

Replace (current lines 64-75):

```kotlin
val LightSemanticColors =
    LuxMapSemanticColors(
        background = Gray25,
        surface = Color(0xFFFFFFFF),
        surfaceSubtle = Gray50,
        textPrimary = Gray900,
        textSecondary = Gray500,
        border = Gray200,
        focus = Blue500,
        navigationActive = Navy700,
        navigationInactive = Gray500,
    )
```

with:

```kotlin
val LightSemanticColors =
    LuxMapSemanticColors(
        background = Paper,
        surface = Color(0xFFFFFFFF),
        surfaceSubtle = LineSoft,
        textPrimary = Ink,
        textSecondary = Ink2,
        border = Line,
        focus = Lumen,
        navigationActive = Lumen,
        navigationInactive = Ink2,
    )
```

- [ ] **Step 2: Update `DarkSemanticColors`**

Replace (current lines 77-88):

```kotlin
val DarkSemanticColors =
    LuxMapSemanticColors(
        background = Dark950,
        surface = Dark800,
        surfaceSubtle = Dark900,
        textPrimary = DarkText,
        textSecondary = DarkMuted,
        border = Dark700,
        focus = Green400,
        navigationActive = Green400,
        navigationInactive = DarkMuted,
    )
```

with:

```kotlin
val DarkSemanticColors =
    LuxMapSemanticColors(
        background = Night,
        surface = NightSurf,
        surfaceSubtle = NightSurf2,
        textPrimary = NightText,
        textSecondary = NightText2,
        border = NightLine,
        focus = LumenNight,
        navigationActive = LumenNight,
        navigationInactive = NightText2,
    )
```

Note: `DarkSemanticColors.focus` moves from `Green400` to `LumenNight`. `Green400` stays defined (still used by `AssetCondition.NORMAL`'s dark badge and `Theme.kt`'s `tertiary`/`onTertiary` — untouched by this task).

- [ ] **Step 3: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. `Theme.kt` consumes these fields by name, so no signature changed — only values.

- [ ] **Step 4: Manual visual check**

Launch the app (debug build) on an emulator or device, light and dark, and open Home, Map, Profile. Expected: background/surface/text colors have visibly shifted to the warmer paper/ink tones (light) or the cooler near-black/indigo tones (dark); no screen crashes, no text that's now invisible against its background (spot-check at least one screen with a card and one with a top bar).

- [ ] **Step 5: ktlint**

Run: `./gradlew ktlintCheck`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Color.kt
git commit -m "feat(fm-xx): repoint semantic colors to v2.1 palette"
```

---

### Task 3: Extend `Theme.kt`'s M3 `ColorScheme` mapping

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Theme.kt:10-46`

**Interfaces:**
- Consumes: `LightSemanticColors`/`DarkSemanticColors` (Task 2), primitives `Lumen`, `LumenCont`, `LumenOnCont`, `LumenNight`, `LumenNightCont`, `LumenNightOnCont`, `LineSoft`, `NightSurf2`, `ErrorDark` (Task 1).
- Produces: `LuxMapLightColorScheme`/`LuxMapDarkColorScheme` now set `primaryContainer`/`onPrimaryContainer`/`surfaceContainerHigh`/`outlineVariant`, and dark mode gets its own `error`/`onError` — consumed by every `MaterialTheme`-aware composable via `LuxMapTheme`.

- [ ] **Step 1: Update `LuxMapLightColorScheme`**

Replace (current lines 10-27):

```kotlin
private val LuxMapLightColorScheme =
    lightColorScheme(
        primary = LightSemanticColors.navigationActive,
        onPrimary = LightSemanticColors.surface,
        secondary = LightSemanticColors.focus,
        onSecondary = LightSemanticColors.surface,
        tertiary = Green400,
        onTertiary = Dark950,
        background = LightSemanticColors.background,
        onBackground = LightSemanticColors.textPrimary,
        surface = LightSemanticColors.surface,
        onSurface = LightSemanticColors.textPrimary,
        surfaceVariant = LightSemanticColors.surfaceSubtle,
        onSurfaceVariant = LightSemanticColors.textSecondary,
        outline = LightSemanticColors.border,
        error = Danger600,
        onError = Color(0xFFFFFFFF),
    )
```

with:

```kotlin
private val LuxMapLightColorScheme =
    lightColorScheme(
        primary = LightSemanticColors.navigationActive,
        onPrimary = LightSemanticColors.surface,
        primaryContainer = LumenCont,
        onPrimaryContainer = LumenOnCont,
        secondary = LightSemanticColors.focus,
        onSecondary = LightSemanticColors.surface,
        tertiary = Green400,
        onTertiary = Dark950,
        background = LightSemanticColors.background,
        onBackground = LightSemanticColors.textPrimary,
        surface = LightSemanticColors.surface,
        onSurface = LightSemanticColors.textPrimary,
        surfaceVariant = LightSemanticColors.surfaceSubtle,
        onSurfaceVariant = LightSemanticColors.textSecondary,
        surfaceContainerHigh = LineSoft,
        outline = LightSemanticColors.border,
        outlineVariant = LineSoft,
        error = Danger600,
        onError = Color(0xFFFFFFFF),
    )
```

- [ ] **Step 2: Update `LuxMapDarkColorScheme`**

Replace (current lines 29-46):

```kotlin
private val LuxMapDarkColorScheme =
    darkColorScheme(
        primary = DarkSemanticColors.navigationActive,
        onPrimary = Dark950,
        secondary = DarkSemanticColors.focus,
        onSecondary = Dark950,
        tertiary = Green400,
        onTertiary = Dark950,
        background = DarkSemanticColors.background,
        onBackground = DarkSemanticColors.textPrimary,
        surface = DarkSemanticColors.surface,
        onSurface = DarkSemanticColors.textPrimary,
        surfaceVariant = DarkSemanticColors.surfaceSubtle,
        onSurfaceVariant = DarkSemanticColors.textSecondary,
        outline = DarkSemanticColors.border,
        error = Danger600,
        onError = Color(0xFFFFFFFF),
    )
```

with:

```kotlin
private val LuxMapDarkColorScheme =
    darkColorScheme(
        primary = DarkSemanticColors.navigationActive,
        onPrimary = Night,
        primaryContainer = LumenNightCont,
        onPrimaryContainer = LumenNightOnCont,
        secondary = DarkSemanticColors.focus,
        onSecondary = Night,
        tertiary = Green400,
        onTertiary = Dark950,
        background = DarkSemanticColors.background,
        onBackground = DarkSemanticColors.textPrimary,
        surface = DarkSemanticColors.surface,
        onSurface = DarkSemanticColors.textPrimary,
        surfaceVariant = DarkSemanticColors.surfaceSubtle,
        onSurfaceVariant = DarkSemanticColors.textSecondary,
        surfaceContainerHigh = NightSurf2,
        outline = DarkSemanticColors.border,
        outlineVariant = Color(0xFF23262D),
        error = ErrorDark,
        onError = Night,
    )
```

Note: `onPrimary`/`onSecondary` move from the hardcoded `Dark950` to `Night` (Task 1/2's new dark background primitive) — same near-black role, now expressed through the new primitive instead of the old one, so dark-mode primary-colored surfaces (e.g. a filled `Button`) keep readable text.

- [ ] **Step 3: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual visual check**

Dark mode: open a screen with a `Button` (e.g. Login's submit button or Home's any primary CTA) and confirm the button is no longer the old green/navy but the new indigo `LumenNight`, with legible text. Confirm an error-state UI (e.g. trigger a login failure) shows the new lighter dark-mode error red, not identical to light mode's `Danger600`.

- [ ] **Step 5: ktlint**

Run: `./gradlew ktlintCheck`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Theme.kt
git commit -m "feat(fm-xx): map v2.1 primary container and surface roles in M3 color scheme"
```

---

### Task 4: Add new `Radius`/`Dimens` values (additive only)

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Dimens.kt`

**Interfaces:**
- Produces: new `object Radius` (`badge = 6.dp`, `chip = 10.dp`, `control = 14.dp`, `card = 16.dp`, `block = 20.dp`, `sheet = 28.dp`) and new `Dimens` vals (`screenMargin = 20.dp`, `primaryButton = 56.dp`, `recordButton = 84.dp`, `topBar = 64.dp`, `navBar = 76.dp`) — consumed by Phase 1's new components (`PoleStrip`, `BottomActionBar`, `RecordButton`, etc.), not by anything in this phase.

- [ ] **Step 1: Grep for existing `Spacing.xxxl` usage**

Run: `grep -rn "Spacing.xxxl" app/src/main/java`
Record the result in the PR description. If there are call sites, do **not** change `Spacing.xxxl`'s value in this task — the spec's proposed `32.dp` stays a Phase 1+ decision made with those call sites in view, not here.

- [ ] **Step 2: Add the new objects/vals**

Append to `Dimens.kt` (after the existing `Dimens` object, which stays unchanged):

```kotlin
// v2.1 redesign radii — additive, existing Dimens.radiusSmall/Medium/Large/Sheet/Pill above
// are unchanged and still used by existing components until Phase 1 migrates them.
object Radius {
    val badge = 6.dp
    val chip = 10.dp
    val control = 14.dp
    val card = 16.dp
    val block = 20.dp
    val sheet = 28.dp
}

// v2.1 redesign layout constants — additive, for new Phase 1+ components.
val ScreenMargin = 20.dp
val PrimaryButtonHeight = 56.dp
val RecordButtonSize = 84.dp
val TopBarHeight = 64.dp
val NavBarHeight = 76.dp
```

- [ ] **Step 3: Build check**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: ktlint**

Run: `./gradlew ktlintCheck`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Dimens.kt
git commit -m "feat(fm-xx): add v2.1 radius and layout dimension tokens"
```

---

### Task 5: Add new typography scale as additive `TextStyle` vals

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Type.kt`

**Interfaces:**
- Consumes: `BeVietnamPro` (existing `FontFamily`).
- Produces: new top-level `TextStyle` vals — `NumericTextStyle` (44sp/48sp/700), `DisplayTextStyle` (30sp/36sp/700), `HeadlineTextStyle` (24sp/30sp/700), `MetricTextStyle` (28sp/32sp/700), `TitleTextStyle` (18sp/24sp/600), `BodyStrongTextStyle` (16sp/22sp/600), `BodyTextStyle` (16sp/24sp/400), `LabelTextStyle` (14sp/20sp/500), `OverlineTextStyle` (13sp/16sp/600, uppercase handled at call site via `.uppercase()` on the string, not baked into the style), `BadgeTextStyle` (13sp/18sp/600) — consumed by Phase 1+ screens' own `Text()` calls, not by the existing `Typography` object.

- [ ] **Step 1: Add the new styles**

Append to `Type.kt` (after the existing `Typography` object, which stays byte-for-byte unchanged):

```kotlin
// v2.1 redesign typography scale — additive TextStyle vals, deliberately NOT remapped onto
// the Typography object's existing M3 slots (see Phase 0 plan rationale: several slot names
// here collide with different current meanings, e.g. the existing bodyMedium is 16sp/400 body
// text while the spec's "label" role is 14sp/500 metadata — redefining the slot in place would
// silently reskin every current MaterialTheme.typography.bodyMedium call site). Screens migrate
// to these explicit vals one at a time in their own redesign phase.
val NumericTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp)
val DisplayTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp)
val HeadlineTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
val MetricTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
val TitleTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
val BodyStrongTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
val BodyTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
val LabelTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
val OverlineTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)
val BadgeTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
```

Note: `OverlineTextStyle`/`BadgeTextStyle` are 13sp, one step below CLAUDE.md's general 14sp-minimum-for-metadata rule. The redesign spec itself flags this as a deliberate exception (§2.3) and offers bumping both to 14sp as a no-layout-cost alternative — leave at 13sp here to match the spec exactly; raise to 14sp in Phase 1 if the project owner prefers the stricter reading when the first component using them (`StatusBadge`) ships.

- [ ] **Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 3: ktlint**

Run: `./gradlew ktlintCheck`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Type.kt
git commit -m "feat(fm-xx): add v2.1 typography scale as additive text styles"
```

---

### Task 6: Full-app visual regression pass

**Files:** none (manual verification task — no code changes)

- [ ] **Step 1: Walk every existing screen in both themes**

Launch the debug build. For each of: Login, Home, Survey Plan, Capture (force dark, already was dark), Coverage/Review, Submit, Map, PoleDetail, Work Order Detail, Work Order Completion, Profile — open it once in light and, where the screen doesn't force a theme, once with the device in dark mode. Confirm: no text is invisible against its background, no badge lost its color pairing (status badges are untouched by this phase, so any badge color change here is a bug), default `Button`/`OutlinedButton` colors look intentional (indigo `Lumen`/`LumenNight`, not a leftover navy/green).

- [ ] **Step 2: Record findings**

If anything looks wrong, fix it as a follow-up commit within this same phase (still ≤400 lines) before opening the PR — don't carry a known visual bug into Phase 1.

- [ ] **Step 3: Open PR**

Target `dev`. Title: `feat(fm-xx): migrate core theme to v2.1 redesign palette`. Body notes the `Disabled` token conflict default (see Global Constraints) and the `Spacing.xxxl` grep result (Task 4 Step 1) for a quick owner glance.
