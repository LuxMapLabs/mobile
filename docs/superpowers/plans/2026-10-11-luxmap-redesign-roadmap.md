# LuxMap Redesign Roadmap

> **For agentic workers:** This is a roadmap, not a single executable plan. Each phase below becomes its own dated sub-plan (`docs/superpowers/plans/YYYY-MM-DD-redesign-phase-N-<name>.md`) written with superpowers:writing-plans immediately before that phase starts, then run with superpowers:executing-plans or superpowers:subagent-driven-development. Do not start a phase whose sub-plan hasn't been written and reviewed yet.

**Goal:** Roll out the "Night Ledger" redesign (`LuxMap Redesign.dc.html`, Claude Design project `82538650-3a87-4ecd-8cbc-37aaf17badc1`) to the Android app one screen/subsystem at a time, so each phase ships as its own small, reviewable PR instead of one giant rewrite.

**Spec sources (all read 2026-10-11 from the Claude Design project's `handoff/` folder):**
- `handoff/LuxMap_Redesign_Spec.md` v0.1 (2026-10-10) — the primary spec: tokens, component inventory, per-screen behavior, navigation, motion, responsive, a11y.
- `handoff/LuxMap_Design_System_v2.1.md` — proposed token/typography additions and the `Disabled` value correction. **Needs project-owner sign-off before `core/theme` uses it** (per CLAUDE.md's token rule) — treated as approved per this planning session (2026-10-11); flag if that's not what was intended.
- `handoff/LuxMap_Screen_Review.md` (2026-10-11) — diff between the interactive prototype and the current app code; this is the authoritative list of "design says X, code does Y" mismatches. Every phase below that touches a screen in that doc must resolve its mismatches, not just restyle on top of them.

**Decisions already made for this roadmap (2026-10-11, confirmed with the project owner in this session):**
1. **Token migration v2.0 → v2.1 is approved.** `core/theme` will move to the new Paper/Ink/Lumen palette, the v2.1 typography additions, and the v2.1 `Disabled` correction.
2. **The 3 screens blocked on unconfirmed backend schema** (AI classification confirmation, Inspection Task outcome-per-fault, Repair Task's extra lifecycle fields) **get UI-only treatment**: build them now against `Fake...Repository` + clearly-local, clearly-placeholder model names (not real entity/field names — e.g. prefix with `Draft`/`Local` and a code comment-free note is not allowed per the no-new-comments rule, so name the class itself unambiguously, like `LocalAiClassificationReview`). Rewire to real entities once WP2/WP5 confirm schema. This does **not** relax CLAUDE.md's "don't invent field/endpoint names" rule for the real API layer — it only means the Compose UI and its Fake repo can exist before the contract does.

## Two cross-cutting conflicts found while reading the handoff docs — resolve before/at the phase that hits them

These are real disagreements between the two spec files, not implementation details I can silently pick a side on:

1. **`Disabled` token value conflicts between the two docs.** `LuxMap_Redesign_Spec.md` §2.1 lists primitives `Disabled = 0xFFE4E2DB` / `DisabledOn = 0xFF8A8D96`. `LuxMap_Design_System_v2.1.md` §3 gives a *different* pair with a written contrast rationale: light `container #E2E8F0` / `content #475569`, dark `container #2A2A2A` / `content #A0A0A0`. Phase 0 defaults to the v2.1.md table (it has the contrast math and is the file explicitly about `core/theme` additions) — flag this default for a quick owner glance when Phase 0's PR goes up.
2. **F01 hero color conflicts.** `LuxMap_Redesign_Spec.md`'s F01 section describes the hero as `inverseSurface` (Ink `#15171C`, dark block with glow). `LuxMap_Design_System_v2.1.md` §5 says "Hero F01 dùng `navy-700`" (the *old* v2.0 brand color, explicitly kept). This only matters at Phase 11 (F01) — noted there, not resolved now.

Also found, lower-stakes, confirm at the owning phase instead of guessing:
- Current `Dimens.kt` `Spacing.xxxl = 40.dp`; the redesign spec's `Spacing.xxxl = 32.dp`. Grep call sites before changing — don't silently shrink an existing spacing value other screens may depend on.
- `StatusBadge` radius changes from the current `radiusSmall` (8dp) to a new `6dp` per the spec's component table — confirm no other component relies on `StatusBadge`'s current corner radius for visual alignment before changing it project-wide.

## Phase order and status

Each phase's own sub-plan will do the detailed file-by-file breakdown; this table is the routing/dependency map only.

| # | Phase | Screens / spec codes | Status | Depends on | Notes |
|---|---|---|---|---|---|
| 0 | Design tokens foundation | — (`core/theme/*`) | **Ready — detailed plan written**, see `2026-10-11-luxmap-redesign-phase0-tokens.md` | none | Recolors every existing screen immediately (M3 colorScheme swap); typography/dimens handled additively to avoid silent regressions — see that plan's rationale. |
| 1 | Shared component restyle | `core/ui/components/*` (StatusBadge, PrimaryButton, WorkOrderCard, OfflineBanner, ErrorBanner, BottomNavBar) + new: `PoleStrip`, `MetricRow`, `TaskRow`, `ClusterSection`, `PriorityDot`, `SyncPill`, `InlineBanner`, `AppTopBar`, `BottomActionBar`, `MessageState` (doesn't exist yet — HomeScreen's empty/error UI is inline; this phase extracts it) | Ready after Phase 0 | Phase 0 | `InlineBanner` replaces `OfflineBanner` + `ErrorBanner` with one `tone` param per spec §3 — check both call sites before deleting either file. |
| 2 | F02 Việc hôm nay | `HomeScreen.kt`/`HomeViewModel.kt`/`HomeUiState.kt` | Ready after Phase 1 | Phases 0–1 | Screen Review item A5: card fields must stay within what `GET /work-orders` actually returns (`work_order_id`, `wo_status`, `due_date`, `priority_score`) — do not add address/fault-type/distance fields the spec's mockups show but the API doesn't send. |
| 3 | Survey Hub (new screen) | spec code `1f` | Ready after Phase 1 | Phases 0–1 | **New route** — today `Routes.Survey` goes straight to `SurveyPlanScreen`. Needs a new `composable()` entry in `NavGraph.kt` and a route rename/insert. "Bị từ chối" and "chờ xác nhận AI" cards are UI-only per the blocked-screens decision above — gate them behind a local flag, not a real sweep-status enum value. |
| 4 | F03 Chuẩn bị khảo sát | `SurveyPlanScreen.kt` + friends, spec `1g–1j` | Ready after Phase 1 | Phases 0–1, 3 (new route `survey/plan/{surveySweepId}`) | Keep `CheckSurveyReadinessUseCase` logic untouched — this phase is visual only. |
| 5 | F04 Chế độ quay | `CaptureScreen.kt` + friends, spec `1k–1o` | Ready after Phase 1 | Phases 0–1 | Screen Review items A3/A4: copy must read the real `GpsAccuracyGate` threshold (not hardcode "≤10m"), and label the sensor "Bluetooth" not "BLE" (it's classic SPP, confirmed 2026-09-30). Missing states to add: `Connecting`, `ScanTimedOut`, `PackagingFailed`, lux-sensor-disconnect-mid-recording warning. |
| 6 | F06 Nộp đợt khảo sát | `SubmitScreen.kt` + friends, spec `1p` | Ready after Phase 1 | Phases 0–1 | "Chỉ tải lên qua Wi-Fi" toggle is a **new** WorkManager constraint, not just UI — needs its own small plan section for the constraint wiring, not just a switch that does nothing. Byte-level progress also doesn't exist yet (today: 0%/100% per file) — confirm whether this phase adds it or defers to a later ticket. |
| 7 | Xác nhận kết quả AI (new screen) | spec `1q–1r` | **UI-only, mock data** (per decision #2 above) | Phases 0–1 | No real route/entity exists. New `Fake`-only repository, local-only model (e.g. `LocalAiClassificationReview`), never touches a real DTO or endpoint. Minimum fields needed per spec: ai_class, ai_confidence, frame ref/timestamp, final_class, override_reason, overridden_at, overridden_by — these stay local field names until WP2/WP5 confirm the real contract. |
| 8 | Lệnh kiểm tra / sửa chữa restyle | `WorkOrderDetailScreen.kt`, `WorkOrderCompletionScreen.kt`, spec `1s–1v` | **Partially blocked** | Phases 0–1 | Good news: the split already exists in code as `WorkOrderDetail.taskKind` ("inspection"/"repair"/"survey") — no new entity needed for the *tab/badge* restyle. Blocked part: `LifecycleTracker`'s 5-step model and `OutcomeSegment`'s per-fault outcome (`fault_present`/`fault_absent`/`inconclusive`) aren't in `WorkOrderCompletionViewModel` today — build these **UI-only with local state**, don't wire a submit path for them until WP2/WP5 confirm `inspection_outcome`. The existing notes+evidence submit contract (F10's real rule: ≥1 after-photo + ≥10-char note) keeps working underneath. |
| 9 | F12 Bản đồ | `MapScreen.kt` + friends, spec `1w–1x` | Ready after Phase 1 | Phases 0–1 | Keep `MapSearchBar.kt`/`PoleQuickViewBottomSheet.kt` logic, restyle only. Filter-chip counts need checking against what `MapUiState`/`MapViewModel` actually track today. |
| 10 | F15 Cá nhân | `ProfileScreen.kt` + friends, spec `1y` | Ready after Phase 1 | Phases 0–1 | Screen Review item B: today's screen is just account name + logout; spec wants Device/Sync/App groups and a logout confirmation dialog (`isLoggingOut` state is also currently missing — add it here, it's a real gap not just a style issue). "Thông báo" row must say "Chưa hỗ trợ trong phiên bản này" (F14 genuinely doesn't exist, per CLAUDE.md's known-gaps note) — do not build a notification settings row that implies it works. |
| 11 | F01 Đăng nhập | `LoginScreen.kt` + friends, spec `2a–2h` | Ready after Phase 1, **needs one product decision first** | Phases 0–1 | Screen Review item A2: design sends the user to Home after login; code sends them to Map (`Routes.Map` is the back-stack root). That's a navigation/product decision, not a style one — ask before changing `NavGraph.kt`'s post-login destination. Also resolve the hero-color conflict noted above. The 3-step prefetch UI needs per-step progress the ViewModel doesn't expose yet (today: one `Prefetching` state) — confirm whether this phase adds step tracking or ships a single progress bar. |

## Explicitly out of scope for this redesign pass

Per the spec's own §1 scope line and Screen Review section C/D — do not touch these without a separate, explicit request:
- **F05** (`CoverageScreen.kt`, video review/approve) — the spec skips straight from capture to submit; the real app keeps the existing review step. Don't delete or bypass it.
- **F11** (fault report), **F13** (its own sync-queue UI, separate from the `SyncPill` header widget Phase 1 adds), **F14** (notifications — doesn't exist on either side), **PoleDetail** screen, landscape F04 layout, the 409-conflict dialog.

## Global constraints (apply to every phase)

- Branch off `dev`, never `main`. One PR per phase where possible; a phase whose diff would exceed ~400 changed lines splits into multiple commits per CLAUDE.md's commit-size rule — flag this in that phase's own sub-plan before writing code.
- Work in small steps with a stop-and-confirm after each, per CLAUDE.md's "từng bước nhỏ" rule — this applies within a phase, not just between phases.
- `./gradlew ktlintCheck` after every task.
- All code comments in English, plain words, only where the WHY is non-obvious — per CLAUDE.md and the existing "no new comments" feedback from this user: default to zero new comments unless something is genuinely non-obvious.
- No new third-party dependencies (spec §9 confirms Material Symbols Rounded already ships via `material-icons-extended`).
- Any screen with async data keeps the mandatory 4-state `UiState` (`Loading`/`Success`/`Empty`/`Error`) — restyling must not collapse or skip a state.
- Offline-first behavior (Room-first writes, `sync_queue`) is never changed by a restyle phase — these phases are visual/structural UI only unless a phase's table entry says otherwise (Phase 6's Wi-Fi-only toggle, Phase 10's logout dialog).

## Review focus (apply across phases, not just within one)

- Any phase that touches `MaterialTheme.typography.*` call sites risks picking up Phase 0's deliberately-deferred typography slot remap by accident — check Phase 0's own plan note before assuming a slot means what the new spec table says.
- Any screen using `WorkOrderPriority.color()` directly as text color must switch to the spec's "8dp dot + onSurface text" pattern (§2.1) — current code may paint priority text in the priority color itself, which is the exact low-contrast case the spec calls out (2.2:1 for Amber500-on-light).
- Every "blocked" UI-only screen/field (Phase 3's rejected/AI-pending cards, Phase 7, Phase 8's `OutcomeSegment`) must be reachable only through a local flag or mock entry point — never behind a real API response shape, so turning the flag off fully removes them with no dangling schema assumption.
- Phase 6 and Phase 11 each add a feature with no backing ViewModel state today (Wi-Fi-only constraint; per-step prefetch progress) — don't let "restyle" scope quietly grow into "add the feature" without that phase's own plan calling it out as a separate, estimated task.
- Nav destination changes (Phase 11's post-login target, Phase 3's new route) touch `NavGraph.kt`'s back-stack root logic — a wrong back-stack root breaks system Back across the whole app, not just one screen; each such change needs its own manual back-button walkthrough before merge.
