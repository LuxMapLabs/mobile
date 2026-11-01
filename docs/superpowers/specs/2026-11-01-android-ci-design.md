# Android CI design — PR checks + downloadable debug APK

Date: 2026-11-01
Status: approved in chat brainstorming, ready for implementation plan

## Problem

The `mobile` repo (`github.com/LuxMapLabs/mobile`) has no CI/CD at all — no
`.github/workflows` directory exists. `ktlintCheck` and the unit test suite
are only ever run by hand, and nothing blocks a PR from merging into `dev`
with a formatting violation or a broken test. There is also no way to get a
build of a PR's code onto a test device without pulling the branch and
building locally.

## Goal

Add a single GitHub Actions workflow that:

1. Runs `ktlintCheck` and the unit test suite on every PR into `dev` and
   every push to `dev`, blocking merge on failure (required status check).
2. Builds a debug APK and makes it downloadable as a workflow artifact, so
   a reviewer or tester can install a PR's build without a local checkout.

## Explicitly out of scope (this round)

- Instrumented/Compose UI tests in CI (device/emulator needed) — stays a
  manual, real-device step per CLAUDE.md's existing testing practice.
- Release signing, Play Store, Firebase App Distribution, or any other
  auto-publish step.
- Anything triggered on `main` — this repo never pushes to `main` directly
  (CLAUDE.md), and a future `dev` → `main` PR workflow is a separate
  decision, not covered here.
- `ktlintFormat` (auto-fixing/auto-committing format issues) — CI only
  checks, it never writes back to the branch.

These are left out on purpose, not forgotten — re-open as a new,
separately-scoped change if/when needed.

## Decisions made (chat brainstorming, 2026-11-01)

- **Scope**: PR quality gate (ktlint + unit tests) **and** a downloadable
  debug APK — not just checks, and not full auto-distribution.
- **`CaptureViewModelTest` hang**: known pre-existing bug (not caused by
  CI), unit test step gets a `timeout-minutes` cap so the job fails loudly
  instead of running forever; the test itself stays in the suite (not
  excluded), so its coverage isn't silently dropped.
- **Instrumented tests**: not run in CI for now — emulator startup in
  GitHub Actions is slow (~10-15 min) and adds flakiness; stays a manual
  real-device step as already practiced.
- **APK signing**: debug build only, using the Android Gradle Plugin's
  default debug keystore. No release keystore, no GitHub secret to manage.
- **Triggers**: `pull_request` targeting `dev`, and `push` to `dev`.

## Workflow structure

One file: `.github/workflows/android-ci.yml`. Two jobs:

### Job `checks` (required status check)

Runner: `ubuntu-latest`. JDK: Temurin 17 — required by this repo's Gradle
8.14 + AGP 8.7.2 to *run* Gradle itself (separate from the app's own
`compileOptions`/`kotlinOptions` targeting Java 11 bytecode, which is
unrelated).

1. `actions/checkout@v4`
2. `actions/setup-java@v4` (`distribution: temurin`, `java-version: '17'`)
3. `gradle/actions/setup-gradle@v4` — handles Gradle/dependency caching,
   no hand-rolled cache key needed
4. `./gradlew ktlintCheck`
5. `./gradlew testLocalDebugUnitTest --continue`, with `timeout-minutes: 15`
   on this step specifically. `--continue` surfaces every independent test
   failure in one run instead of stopping at the first; the step timeout is
   the actual guard against the known hang (a stuck test never returns on
   its own, `--continue` doesn't help with that part).
6. `actions/upload-artifact@v4` for the test report, `if: always()` (so it
   uploads on failure/timeout too): `app/build/reports/tests/testLocalDebugUnitTest/**`
   and `app/build/test-results/testLocalDebugUnitTest/*.xml` — lets anyone
   download and see exactly which tests failed or never got to run.

Flavor choice for the test step: `testLocalDebugUnitTest` (not `-Server-`).
The two flavors only differ in `BuildConfig.API_BASE_URL` and the app-name
resource — unit tests don't make real network calls — so either flavor's
unit-test task compiles and runs the same `app/src/test` sources with
equivalent behavior. `local` is picked because it's the flavor already
exercised by hand in this project (see CLAUDE.md/session history), not for
any CI-specific reason.

### Job `build-apk` (`needs: checks`)

Same runner/JDK/setup-gradle steps as above, then:

1. `./gradlew assembleServerDebug` — `server` flavor (points at
   `luxmap.com.vn`, useful to a tester without a local backend running),
   `debug` build type (default debug keystore, no signing config needed).
2. `actions/upload-artifact@v4`: path `app/build/outputs/apk/server/debug/*.apk`,
   artifact name includes the commit SHA (e.g.
   `luxmap-server-debug-${{ github.sha }}`), `retention-days: 14`.

### Workflow-level settings

- `concurrency`: group keyed on `github.ref`, `cancel-in-progress: true` —
  a new push to the same PR/branch cancels an in-flight older run.
- `permissions: contents: read` — least privilege; nothing in this version
  needs to write back to the repo (no auto-comment, no auto-release).

## Testing this change

There's no automated test for a CI workflow file itself. Verification is:
push the branch, open a throwaway PR into `dev` (or a test branch pointed
at `dev`), and confirm in the Actions tab that: `checks` runs ktlint +
unit tests and reports pass/fail correctly, the `timeout-minutes` guard
is actually in place (doesn't need to be proven by inducing a real hang —
reading the YAML confirms the value is set on the right step), `build-apk`
only starts after `checks` succeeds, and the resulting APK artifact is
downloadable and installs on a device.

## Open questions for the user before/while implementing

None outstanding — all four decision points were resolved in chat
brainstorming (scope, hang handling, instrumented tests, signing,
triggers). If GitHub Actions minutes/billing turns out to be a concern
once this is running for real, that's a follow-up conversation, not a
blocker for this plan.
