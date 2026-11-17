# Login Redesign Implementation Plan

> **For agentic workers:** Use `executing-plans` to implement task-by-task after approval. Steps use checkbox syntax. Follow the repository requirement to stop after each implementation step for user review.

**Status:** T-07 approved by the user on 2026-11-16. Step 1 extraction and nine Compose tests are written in the isolated worktree `C:/Users/Thinkpad/.codex/worktrees/f5a5/mobile`, branch `style/redesign-login-vnext` (based on `dev`). `ktlintCheck` passes. Unit tests and Android test compilation are blocked by a missing Android SDK; no runtime test pass or RED/GREEN cycle is claimed. Awaiting step review before restyling; no commit created.

**Goal:** Redesign F01 Login using the existing Civic Utility design system, with a clearer form and usable layout on small screens and at large font sizes.

**Architecture:** Keep `LoginScreen` as the Hilt/ViewModel and navigation boundary. Extract a stateless `LoginContent` for rendering, previews and Compose tests, passing existing state and callbacks down. Preserve authentication, error placement, session persistence and prefetch behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, existing LuxMap theme/components, JUnit and Compose UI tests. No new dependencies.

**Spec:** `docs/design-system/vNext/README.md` (approved decisions and T-07), `SHAPE_SURFACE.md`, `TYPOGRAPHY.md`, plus the proposed screen design below. `CLAUDE.md` identifies vNext as current despite the older status sentence at the top of its README.

## Screen design — T-07 approved

Two approaches considered:

- **Selected: compact flat navy header + surface form.** Retains Login's brand identity while removing the gradient and decorative streetlights. Navy is a Login-specific exception approved under T-07.
- **Alternative: background/surface throughout.** Matches content screens more closely; brand appears only in the wordmark and CTA. Choose this if no Login-specific navy surface is desired.

Recommended layout:

```text
[Flat navy header]
LUXMAP · Vận hành hiện trường
[network icon] Trực tuyến / Ngoại tuyến

[Surface form]
Đăng nhập
Sử dụng tài khoản nhân viên đã được cấp.

Mã nhân viên hoặc số điện thoại
[identifier input]
[field error when present]

Mật khẩu
[password input                  show/hide]
[field error when present]

[checkbox] Duy trì đăng nhập trên thiết bị này
           Lần mở sau vào thẳng, kể cả khi offline

[error banner when present]
[             Đăng nhập                  ]
[prefetch status + progress when present]
---------------- Hoặc -------------------
[       Đăng nhập bằng Google            ]

Cần hỗ trợ đăng nhập?
Phiên bản ứng dụng
```

- A single scrolling content flow includes header, form and footer. No measured hero height, overlapping layers or pinned footer competing with the IME.
- Use 16dp horizontal screen padding, existing spacing tokens, 12dp control radius through `MaterialTheme.shapes.medium`, and at least 48dp touch targets. Containers grow with text.
- The form is a page surface, not a modal bottom sheet: no heavy shadow or sheet handle. Avoid additional nested cards.
- Use bundled Be Vietnam Pro through M3 typography: `headlineMedium` for the form title, `labelMedium` for field labels, `bodyLarge` for instructions/errors, `labelLarge` for buttons, `bodySmall` only for metadata.
- Header background/content must use an existing semantic pair if suitable. If none exists, specify a named Login brand pair in `core/theme` for both themes; do not import primitive colors into the feature or change the app-wide scheme to obtain a navy header.
- Network status uses icon + readable label. Use connectivity-appropriate semantic colors, not an unrelated sync/work-order badge. Place it on its own row to avoid header overflow.
- Use semantic surface and text colors for fields, including Night Field. Remove the hardcoded cream field fill and decorative colors.
- Keep the existing placeholder wordmark until an official asset exists; simplify it without inventing a new brand logo.
- Keep the support text informational because no support destination exists. Use version metadata in the footer; avoid presenting an unverified security assurance as decoration.

## Global constraints

- Preserve identifier/password and Google sign-in, remember-me semantics, existing error messages/focus behavior, loading, prefetch and success navigation.
- Preserve `Idle`, `LoggingIn`, `Prefetching`, `Success`, `Error`; do not force Login into a list-screen state model.
- Do not change APIs, repositories, token persistence or navigation. Do not add signup, password reset, biometric login or a role picker.
- No gradient, new fonts, new libraries, dynamic color or feature-local hex values.
- WorkOrder changes already in the working tree are outside this task. Before implementation, isolate Login work according to the repository's branch rules without discarding those changes.
- Keep each commit at or below 400 added/deleted code lines. Split the large existing Login file migration into independently buildable steps; count the actual diff before committing.
- No implementation or commit is part of this planning turn.

## Review focus

1. At 360dp width with font scales 1.0, 1.3 and 2.0, labels, errors and actions wrap without clipping and remain reachable by scrolling.
2. With the keyboard open, password entry, field errors and both sign-in actions remain reachable; Next/Done work.
3. Repeated taps/IME Done during submission do not cause repeated login requests; existing field and button disabling is preserved.
4. Missing identifier/password and invalid credentials appear once at the correct field; network/account/Google failures remain banner errors.
5. Google cancellation, offline behavior and prefetch-to-success preserve the existing flow and do not navigate early.

## Task 1: Make the existing screen testable

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/auth/ui/LoginScreen.kt`
- Keep `LoginContent` in `LoginScreen.kt` for this extraction step; moving the existing 600+ lines would exceed the 400-line commit limit. Extract smaller files during restyling only when that improves the resulting structure.
- Create: `app/src/androidTest/java/com/luxmap/feature/auth/ui/LoginScreenTest.kt`

**Interfaces:** Keep `LoginScreen(onLoginSuccess, modifier, viewModel)` unchanged. `LoginContent` consumes `LoginUiState`, connectivity, identifier/password/visibility/remember-me values, and callbacks for their updates, submit and Google sign-in. It has no Hilt or repository dependency. The route retains success navigation and bridges Google sign-in with its Android context.

- [x] Extract the existing rendering without restyling. `LoginScreen` retains state collection, success navigation and Google context bridging; `LoginContent` owns focus/error rendering and receives form state and callbacks. Runtime verification is still pending.
- [x] Write nine Compose tests for input callbacks, remember-me, password visibility, submit/IME and Google callbacks, field errors/focus and submission disabling. Tests use real UI with local state/callback counters, not real authentication/network. Execution is still pending.
- [ ] Verify extraction compiles and tests pass before restyling. Run existing auth unit tests as a behavior baseline.
- [ ] Review this step and its diff with the user before proceeding.

## Task 2: Apply the approved visual design

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/auth/ui/LoginContent.kt`
- Modify only if a semantic brand pair is missing: `app/src/main/java/com/luxmap/core/theme/ExtendedColors.kt` and corresponding theme tests.
- Reuse: `PrimaryButton.kt`, `LuxButtons.kt`, `LuxBanner.kt`, `Shape.kt`, `Type.kt`.

**Interfaces:** Reuse Task 1's state/callback boundary. Reuse `PrimaryButton`, `LuxSecondaryButton` for Google, and the existing Lux error presentation where it fits. Keep M3 outlined inputs; do not introduce a shared form framework for this one screen.

- [x] Confirm T-07: the user selected a compact flat navy header.
- [ ] Implement the chosen header/form hierarchy and single scroll flow with status/navigation bar insets and IME accommodation.
- [ ] Apply semantic colors, M3 typography/shapes, visible field labels and accessible show/hide labels. Ensure input values and interaction states survive the same lifecycle events as before.
- [ ] Preserve both submitting phases: login loading feedback and the distinct prefetch message/progress. Reuse existing reduced-motion behavior where animation is retained.
- [ ] Remove `StreetlightSilhouette`, hero measuring/overlap logic, cream field color and imports/helpers made unused by this redesign. Do not remove shared tokens still used elsewhere.
- [ ] Add representative previews for Light, Night Field, field error, banner error, logging in, prefetching and 360dp large font. Render a previewable content composable, not a Hilt-backed route.
- [ ] Review visual output and the actual diff; split into smaller buildable steps if this migration exceeds the commit limit.

## Task 3: Validate interactions and small-screen layout

**Files:**
- Extend: `app/src/androidTest/java/com/luxmap/feature/auth/ui/LoginScreenTest.kt`
- Existing regression: `app/src/test/java/com/luxmap/feature/auth/ui/LoginViewModelTest.kt`
- Modify ViewModel tests only if a required preserved behavior lacks coverage.

- [ ] Cover missing identifier/password, invalid credentials, account/network/Google banner errors, and exactly one occurrence of each error message.
- [ ] Cover logging-in and prefetch states: disabled submission controls, visible phase-specific feedback; preserve the existing success navigation boundary.
- [ ] Exercise 360dp width at font scales 1.0, 1.3 and 2.0. Assert important controls can be scrolled into view; visually inspect wrapped text because existence assertions do not prove absence of clipping.
- [ ] On a device/emulator, open the keyboard and verify Next/Done, focus after validation, long identifiers, password visibility and Google cancellation. Check Light/Night Field, TalkBack reading order, labels and 48dp touch targets.
- [ ] Run `./gradlew.bat ktlintCheck`.
- [ ] Run `./gradlew.bat :app:testLocalDebugUnitTest --tests "com.luxmap.feature.auth.*"`.
- [ ] With a connected device, run `./gradlew.bat :app:connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.auth.ui.LoginScreenTest`.
- [ ] Run relevant theme/component tests if a shared semantic token/component was changed. Record actual counts, failures and device configuration; distinguish unavailable checks from passing ones.
- [ ] Run `git diff --check` and inspect the final Login-only diff. Report results and outstanding limitations before requesting any commit.

## Acceptance

- The chosen T-07 layout is approved and implemented with the existing design system.
- No gradient, decorative streetlight canvas, hardcoded cream field or measured hero overlap remains in Login.
- Authentication behavior remains intact, including Google, remember-me, error placement, prefetch and navigation.
- Form/actions remain usable at 360dp with large fonts and the keyboard open in both themes.
- Relevant tests and formatting checks pass, or concrete environment blockers are explicitly reported.
