# Android CI Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions workflow that runs `ktlintCheck` + unit tests on every PR into `dev` (and every push to `dev`), and builds a downloadable debug APK once those checks pass.

**Architecture:** One workflow file, two jobs. `checks` runs lint and unit tests and always uploads the test report. `build-apk` depends on `checks` passing (`needs: checks`) and uploads a `server`-flavor debug APK as a workflow artifact. No release signing, no auto-distribution, no instrumented tests — see the spec's "Explicitly out of scope" section.

**Tech Stack:** GitHub Actions (`ubuntu-latest` runner), `actions/checkout@v4`, `actions/setup-java@v4` (Temurin 17), `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`. This repo's own Gradle 8.14 / AGP 8.7.2 / Kotlin 2.0.21.

**Spec:** `docs/superpowers/specs/2026-11-01-android-ci-design.md`

## Global Constraints

- JDK 17 (Temurin) on the runner — Gradle 8.14 + AGP 8.7.2 require it to run, independent of the app's own Java 11 bytecode target.
- Unit test step uses the `local` flavor task (`testLocalDebugUnitTest`), not `server` — the two flavors only differ in `BuildConfig.API_BASE_URL`/app name, unit tests make no real network calls, so either flavor's test task behaves identically; `local` is picked only because it's the one already exercised by hand in this project.
- APK build step uses the `server` flavor (`assembleServerDebug`), `debug` build type — points at `luxmap.com.vn`, useful to a tester with no local backend; default debug keystore, no signing config.
- Unit test step gets `timeout-minutes: 15` — `CaptureViewModelTest` has a known pre-existing hang (not caused by this CI work); the timeout turns an infinite hang into a loud, visible job failure instead of burning CI minutes forever. The test itself stays in the suite — it is not excluded.
- Triggers: `pull_request` targeting `dev`, and `push` to `dev`. Never `main` — this repo never pushes to `main` directly.
- `concurrency` cancels an in-flight run when a newer commit lands on the same ref. `permissions: contents: read` — nothing in this workflow writes back to the repo.
- Branch: all work happens on `chore/android-ci-setup` (already created from `dev`), commits follow this repo's Conventional Commits convention (`type(scope): description`, English, imperative, no trailing period) — scope `ci` fits every commit in this plan.
- All commands below are run from the repo root (`D:\ki9\SEP490\App\mobile`, already the default working directory in this environment) using the Bash tool (git-bash) — use forward-slash-style paths, not `D:\...` backslash paths, which git-bash does not handle the way this plan's commands assume.

## Review Focus

- **Branch protection is a GitHub repo setting, not YAML** — adding this workflow does not, by itself, block a failing PR from being merged. Task 4 calls this out explicitly and does not change the setting without asking first (it is a shared-infrastructure change on `dev`).
- **Gradle task names must actually exist in this project** — `ktlintCheck`, `testLocalDebugUnitTest`, `assembleServerDebug` are all verified against this repo's real Gradle output in Task 1–3's steps, not assumed from a generic Android project template.
- **Artifact upload paths must match this project's real build output directories** — a wrong glob uploads nothing and the step still reports success, which is a silent failure mode worse than an error. Task 2 and Task 3 verify the exact paths against a real local build before trusting them in the workflow.
- **The 15-minute test timeout must actually fail the job**, not silently pass it, when a hang happens — Task 4's real-PR run is the only way to see GitHub Actions' actual timeout behavior for this workflow, so it is checked there explicitly rather than assumed.
- **`build-apk` must not start when `checks` fails** — `needs: checks` is the mechanism, but Task 4 confirms it actually gates the second job on a real failing run, not just on reading the YAML.

---

## Task 1: Scaffold the workflow file with the `checks` job's setup + lint step

**Files:**
- Create: `.github/workflows/android-ci.yml`

**Interfaces:**
- Produces: a `checks` job on `ubuntu-latest` that later tasks add steps to. Job name `checks` is the exact string Task 4's branch-protection step and the Global Constraints' `needs: checks` reference.

- [ ] **Step 1: Confirm the `ktlintCheck` task name is real in this project**

Run: `./gradlew ktlintCheck --dry-run`
Expected: Gradle lists `:app:ktlintCheck` (and its dependency tasks) with no "task not found" error. This confirms the task name before it goes into CI.

- [ ] **Step 2: Create the workflow file with triggers, concurrency, permissions, and the start of the `checks` job**

Create `.github/workflows/android-ci.yml`:

```yaml
name: Android CI

on:
  pull_request:
    branches: [dev]
  push:
    branches: [dev]

concurrency:
  group: android-ci-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  checks:
    name: Lint & Unit Tests
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run ktlint
        run: ./gradlew ktlintCheck
```

- [ ] **Step 3: Validate the YAML parses correctly**

Run: `npx -y js-yaml .github/workflows/android-ci.yml`
Expected: prints the parsed structure as JSON (a `name`, `on`, `concurrency`, `permissions`, `jobs.checks` tree) with no "YAMLException" or syntax error. This only checks YAML syntax, not GitHub Actions semantics — that is what Task 4's real run is for.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/android-ci.yml
git commit -m "ci: add Android CI workflow with checks job (checkout, JDK, ktlint)"
```

## Task 2: Add the unit test step and test report upload to `checks`

**Files:**
- Modify: `.github/workflows/android-ci.yml`

**Interfaces:**
- Consumes: the `checks` job created in Task 1 (same job, appending steps — the `ktlintCheck` step from Task 1 stays unchanged above these new ones).
- Produces: `checks` job now ends with a real pass/fail signal from both lint and unit tests, plus an always-uploaded `unit-test-report` artifact later tasks and future debugging rely on by this exact name.

- [ ] **Step 1: Confirm the unit test task name and real output paths**

Run: `./gradlew testLocalDebugUnitTest --dry-run`
Expected: Gradle lists `:app:testLocalDebugUnitTest` with no "task not found" error.

Run: `ls app/build/reports/tests/testLocalDebugUnitTest app/build/test-results/testLocalDebugUnitTest`
Expected: both directories exist from a prior local test run (they do — confirmed in this project's build output already). These are the exact paths the upload step below uses.

- [ ] **Step 2: Add the unit test step and report-upload step**

Edit `.github/workflows/android-ci.yml`, inserting these two steps right after the existing `Run ktlint` step (still inside `jobs.checks.steps`):

```yaml
      - name: Run unit tests
        timeout-minutes: 15
        run: ./gradlew testLocalDebugUnitTest --continue

      - name: Upload unit test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-report
          path: |
            app/build/reports/tests/testLocalDebugUnitTest/**
            app/build/test-results/testLocalDebugUnitTest/*.xml
          retention-days: 14
```

The full `checks` job now reads, top to bottom: Checkout, Set up JDK 17, Set up Gradle, Run ktlint, Run unit tests, Upload unit test report.

- [ ] **Step 3: Validate the YAML parses correctly**

Run: `npx -y js-yaml .github/workflows/android-ci.yml`
Expected: parses cleanly; the printed structure now shows 6 steps under `jobs.checks.steps`, and the `Run unit tests` step's parsed object includes `"timeout-minutes": 15`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/android-ci.yml
git commit -m "ci: run unit tests with a 15-minute timeout and upload the report"
```

## Task 3: Add the `build-apk` job

**Files:**
- Modify: `.github/workflows/android-ci.yml`

**Interfaces:**
- Consumes: the `checks` job's name (`checks`) from Task 1, via `needs: checks`.
- Produces: a `build-apk` job whose only consumer is a human downloading the artifact from the Actions run UI — no later task depends on this job's internals.

- [ ] **Step 1: Confirm the APK build task name and real output path**

Run: `./gradlew assembleServerDebug --dry-run`
Expected: Gradle lists `:app:assembleServerDebug` with no "task not found" error.

Run: `ls app/build/outputs/apk/server/debug`
Expected: `app-server-debug.apk` is listed (confirmed present in this project's build output already). This is the exact directory the upload step below globs.

- [ ] **Step 2: Add the `build-apk` job**

Edit `.github/workflows/android-ci.yml`, appending a new top-level job after `checks` (same indentation level as `checks:`, i.e. under `jobs:`):

```yaml
  build-apk:
    name: Build Debug APK
    needs: checks
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build server-flavor debug APK
        run: ./gradlew assembleServerDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: luxmap-server-debug-${{ github.sha }}
          path: app/build/outputs/apk/server/debug/*.apk
          retention-days: 14
```

- [ ] **Step 3: Validate the YAML parses correctly**

Run: `npx -y js-yaml .github/workflows/android-ci.yml`
Expected: parses cleanly; the printed structure now shows two jobs under `jobs`: `checks` and `build-apk`, and `build-apk.needs` equals `"checks"`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/android-ci.yml
git commit -m "ci: add build-apk job to build and upload a debug APK"
```

## Task 4: Verify the whole workflow on a real PR, then decide on branch protection

**Files:** none (verification task — no file changes beyond what Tasks 1–3 already made)

**Interfaces:**
- Consumes: the complete `.github/workflows/android-ci.yml` from Tasks 1–3.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin chore/android-ci-setup
```

- [ ] **Step 2: Open a PR into `dev`**

```bash
gh pr create --base dev --head chore/android-ci-setup --title "ci: add Android CI workflow (lint, unit tests, debug APK)" --body "$(cat <<'EOF'
## Summary
- Adds .github/workflows/android-ci.yml: ktlintCheck + unit tests on every PR into dev and every push to dev, with a 15-minute timeout on the test step
- Builds a server-flavor debug APK as a downloadable artifact once checks pass
- Spec: docs/superpowers/specs/2026-11-01-android-ci-design.md

## Test plan
- [ ] checks job runs and reports ktlint + unit test results
- [ ] build-apk job only starts after checks succeeds
- [ ] APK artifact is downloadable from the run and installs on a device
EOF
)"
```

- [ ] **Step 3: Watch the Actions run and confirm each Review Focus item**

Run: `gh pr checks chore/android-ci-setup --watch`

While it runs, confirm, in order:
1. `checks` starts first and its steps execute in the order: Checkout, Set up JDK 17, Set up Gradle, Run ktlint, Run unit tests, Upload unit test report.
2. If `Run unit tests` is still running past 15 minutes, GitHub Actions cancels it and the step — and therefore the `checks` job — shows as **failed**, not passed. (If the known `CaptureViewModelTest` hang does not trigger on this run, the job simply passes in well under 15 minutes — both outcomes are acceptable; what matters is that a timeout, if it happens, shows as a failure, not a silent pass.)
3. `build-apk` does not start until `checks` finishes successfully — check the run's timeline/graph view in the Actions UI, not just job order in the YAML.
4. Once `build-apk` finishes, open the run's summary page and confirm the `luxmap-server-debug-<sha>` artifact is listed and downloadable.
5. Download it, unzip it, and confirm the `.apk` file inside installs on a device/emulator via `adb install`.

- [ ] **Step 4: Ask before touching branch protection**

Adding this workflow does not, by itself, block merging a PR with failing checks — that requires marking the `checks` job's status check as required in `dev`'s branch protection rule, which is a shared GitHub repo setting, not something this plan changes unilaterally. GitHub exposes the job's display name as the check context, not its YAML job id — for this workflow that is **`Lint & Unit Tests`**, not `checks`. Ask the user: "Workflow confirmed working on the real PR. Do you want `Lint & Unit Tests` set as a required status check on `dev` now? That's **Settings → Branches → Branch protection rules → dev → Require status checks to pass → Lint & Unit Tests**." Only apply it (via the Settings UI, or `gh api repos/LuxMapLabs/mobile/branches/dev/protection` with the user's explicit go-ahead on the exact payload) if the user says yes in this conversation — note this also requires Admin permission on the repo, which the account running Claude Code may not have.

- [ ] **Step 5: Merge the PR**

Once Step 3's checklist is confirmed, merge `chore/android-ci-setup` into `dev` through the normal PR review the user already uses for this repo — do not self-approve or auto-merge.
