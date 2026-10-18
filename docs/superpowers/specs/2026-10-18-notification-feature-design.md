# F14 Thông báo — design spec

Date: 2026-10-18
Status: approved in chat brainstorming, ready for implementation planning

## 1. Context

Backend module `LuxMap.Modules.Notifications` (BE-27) has been live on `luxmap_backend` `dev`
since before this spec — confirmed by reading the real source (`NotificationsController.cs`,
`NotificationContracts.cs`, `Entities/Notification.cs`, `Notifier.cs`, `WorkOrderNotices.cs`,
`SurveyNotices.cs`, `FaultNotices.cs`) on 2026-10-18, not from `docs/Backend_API_Requirements_For_Mobile.docx`
(stale) or `CLAUDE.md`'s own F14 note (partly stale — see §2).

`feature/notification/` does not exist in the mobile repo yet. `ProfileScreen` has a static,
non-clickable row (`SettingsRow(label = "Thông báo", value = "Chưa hỗ trợ trong phiên bản này")`)
that needs updating once this ships. `Routes.kt` already declares `Routes.Notification` (route
`"notification"`) but it was never wired into `NavGraph.kt`.

## 2. API contract (real, read from backend source — supersedes CLAUDE.md's "11 giá trị")

Base path: `api/v1/notifications`, policy `ReadNotifications` on every endpoint (needs the
authenticated `OkHttpClient`/Retrofit instance, same as every other `*Api` in `core/network/`).

| Method | Path | Query/body | Response |
|---|---|---|---|
| GET | `/notifications` | `?unread_only={bool}&page={int}&page_size={int}` (defaults: page=1, page_size=50, max 200, clamped silently by the server) | `NotificationPage` |
| GET | `/notifications/unread-count` | — | `{ unread_count: int }` |
| POST | `/notifications/{notification_id}/read` | — | 204, idempotent |
| POST | `/notifications/read-all` | — | 204 |

`NotificationPage`: `{ page, page_size, total, unread_count, items: NotificationItem[] }` — one
poll of the list refreshes both the list and the badge count.

`NotificationItem`: `{ notification_id, type, title, body, entity_type, entity_id, created_at, read_at }`.
`title`/`body` are server-composed Vietnamese text (a point-in-time snapshot — renaming the work
order later does not rewrite what the user was told). Render as-is, no client-side translation or
reformatting. `read_at` is `null` until read, set once (re-marking is a no-op).

### `type` (`NotificationType`, 13 real values — CLAUDE.md said 11, now stale)

Wire values are the enum name in `snake_case` (confirmed via `LuxMapJsonOptions.Configure` —
`JsonNamingPolicy.SnakeCaseLower` on every enum, project-wide, not special-cased for this one):

`work_order_assigned`, `work_order_unassigned`, `work_order_rescheduled`, `work_order_returned`,
`work_order_cancelled`, `work_order_completed`, `work_order_verified`, `work_order_overdue`,
`survey_returned`, `survey_ready_for_review`, `survey_processing_failed`, `fault_reported`,
`citizen_report_received`.

### `entity_type` (`NotificationEntityType`, 4 values)

`work_order`, `survey_sweep`, `fault`, `citizen_report`.

### Which `type` values a Field Engineer (mobile's only role) actually receives

Traced every `Notifier.Stage(...)` call site in the backend to its recipient list:

| type | Recipients | Reaches mobile? |
|---|---|---|
| `work_order_assigned` | the new assignee | yes |
| `work_order_unassigned` | the previous assignee | yes |
| `work_order_rescheduled` | the assignee | yes |
| `work_order_returned` | the assignee | yes |
| `work_order_cancelled` | the assignee | yes |
| `work_order_verified` | the assignee | yes |
| `work_order_overdue` | the assignee + covering Managers | yes |
| `work_order_completed` | covering Managers only | no (Manager/Web only) |
| `survey_returned` | the sweep's `holder` + `CapturedBy` (the surveying FE) | yes |
| `survey_processing_failed` | covering Managers + `CapturedBy` | yes |
| `survey_ready_for_review` | covering Managers only | no |
| `fault_reported` | covering Managers only | no (practically never seen on mobile) |
| `citizen_report_received` | covering Managers only | no (practically never seen on mobile) |

The 4 "no" rows must still render without crashing (role assignment is not a client-side
guarantee forever), but the UI does not need to special-case them beyond the generic
fallback-icon / no-navigation handling in §6.

## 3. Entry point & navigation shell

**Not** added to `AppTopBar` — none of the 4 tab root screens (`HomeScreen`, `SurveyHubScreen`,
`MapScreen`, `ProfileScreen`) currently use it; each builds its own header. Touching 4 independent
custom headers to add one shared element would be a much larger, riskier change than necessary.

Instead: a new thin, persistent top element added to `NavGraph.kt`'s own `Scaffold`, as a
`topBar =` slot symmetric with the existing `bottomBar =` slot — reusing the same `showBottomBar`
boolean (shown only on the 4 tab routes, hidden on Login and full-screen child routes). Contains
just a bell icon + unread-count badge, right-aligned. Tap → `navController.navigate(Routes.Notification.route)`.

`ProfileScreen`'s existing row gets its `value` text updated (no longer "Chưa hỗ trợ trong phiên
bản này") but stays non-interactive — the bell is the one entry point, no duplicate affordance.

## 4. Feature structure

```
feature/notification/
  data/
    NotificationRepository.kt        # interface
    FakeNotificationRepository.kt
    RealNotificationRepository.kt
    dto/NotificationItemDto.kt
    dto/NotificationPageDto.kt
    NotificationType.kt              # enum, wire mapping fn with UNKNOWN fallback
    NotificationEntityType.kt        # enum, wire mapping fn with UNKNOWN fallback
  ui/
    NotificationListScreen.kt
    NotificationListViewModel.kt
    NotificationListUiState.kt
    NotificationBadgeViewModel.kt    # hoisted at NavGraph level, not under ui/ screen scope
```

`core/network/NotificationsApi.kt` — Retrofit interface, the 4 endpoints above, attached to the
same authenticated `Retrofit`/`OkHttpClient` instance every other `*Api` uses (`NetworkModule`'s
unqualified `OkHttpClient` bean — the same one the 2026-10-18 Coil fix pointed at, see
`CLAUDE.md`'s Coil note).

**No Room cache for v1** — decided explicitly in brainstorming, not an oversight. `CLAUDE.md`'s
architecture section already names F14 as one of the "simple" screens (`ViewModel gọi thẳng
Repository`), unlike the offline-first-mandatory list (F03/F04/F05/F06/F10/F11). `Repository`
calls the API directly and returns `Result`; a failed fetch is `UiState.Error` with a retry
button, same as any other simple screen's error branch — it does not show a stale list. The
unread badge count lives only in `NotificationBadgeViewModel`'s in-memory `StateFlow`, re-fetched
on app start; losing it on process death is an accepted trade-off (reloads within one poll tick).

## 5. Polling

`NotificationBadgeViewModel` is instantiated once in `NavGraph.kt` via `hiltViewModel()` at the
same call site as the existing `SessionViewModel` — this gives it the Activity-scoped
`ViewModelStoreOwner`, so it survives tab switches and is not recreated per-screen, mirroring the
existing `SessionViewModel` pattern exactly.

Loop: `viewModelScope.launch { while (isActive) { fetch unread-count; delay(45_000) } }`, gated by
`ProcessLifecycleOwner` so it pauses while the app is backgrounded (not required by the backend's
"while the app is open" wording, but consistent with `CLAUDE.md`'s "Công nghệ chủ động không
dùng" stance against always-on background work; this is a client-side battery/data choice, not a
contract requirement). Resumes immediately on foreground, does not try to "catch up" missed ticks.

`NotificationListViewModel.onMarkRead`/`onMarkAllRead` call back into the shared count so the
badge updates immediately on action, not just on the next poll tick — exact wiring (shared
`StateFlow` injected into both ViewModels vs. a thin event bus) is an implementation-plan decision,
not a spec-level one.

## 6. List screen & navigation per `entity_type`

`NotificationListScreen`: `LazyColumn`, each row shows a `type`-based icon (see below), `title`,
`body`, relative timestamp, and an unread dot when `read_at == null`. Top bar has a "Đánh dấu tất
cả đã đọc" action. Tapping a row marks it read (optimistic — flips the dot immediately, does not
wait for the 204) and then, **only for `entity_type = "work_order"`**, navigates to
`Routes.WorkOrderDetail.createRoute(entity_id)`.

**`entity_type = "survey_sweep" | "fault" | "citizen_report"` do not navigate in v1** — this is a
deliberate scope decision, documented here so nobody "fixes" it as a bug later:

- `survey_sweep`: the only mobile screens that touch a sweep today (`AiConfirmationScreen`, the
  "Bị từ chối"/"Chờ xác nhận AI" cards on `SurveyHubScreen`) are keyed by a **local** Room UUID
  generated on-device, not the backend's real `SurveySweep.SweepId` that `entity_id` carries here
  — and the "rejected" card state is UI-only mock data (see `CLAUDE.md`'s "Khoảng trống đã biết"
  entry for F06/F13). There is no real screen to open yet. Not resolving that gap here.
  `survey_returned`/`survey_processing_failed` (the two FE-relevant survey notification types)
  will simply not be tappable-through for now — mark-as-read still works.
- `fault` / `citizen_report`: in practice sent to Managers only (see table in §2); kept
  non-navigating for safety/consistency with the other unmapped type rather than special-cased.

### Icon mapping (`type` → existing `ImageVector`, no new icon set)

Reuses icons already imported elsewhere in `core/theme/Color.kt`/feature screens — no new
dependency:

| type | Icon |
|---|---|
| `work_order_assigned`, `work_order_rescheduled` | `Icons.Filled.Assignment` |
| `work_order_overdue` | `Icons.Filled.Assignment` (same as above; urgency comes from the badge/unread dot, not a different icon) |
| `work_order_returned`, `work_order_cancelled`, `fault_reported` | `Icons.Filled.ErrorOutline` |
| `work_order_completed`, `work_order_verified` | `Icons.Filled.CheckCircle` / `Icons.Filled.Verified` |
| `survey_returned`, `survey_ready_for_review`, `survey_processing_failed` | `Icons.Filled.Videocam` |
| `citizen_report_received` | `Icons.Filled.Search` (reuses the inspection icon — no closer existing match, acceptable since this type is never realistically seen) |
| unknown/unrecognized wire value | `Icons.Filled.Notifications` (generic fallback, never throws) |

## 7. UiState & testing

`NotificationListUiState`: standard `sealed interface` with `Loading` / `Success(items, unreadCount)`
/ `Empty` / `Error(message)`, per `CLAUDE.md`'s mandatory 4-state convention — no `isStale` field
(no cache, see §4).

- `RealNotificationRepositoryTest` — MockWebServer, one case per endpoint + one for an unknown
  `type`/`entity_type` wire value decoding to the `UNKNOWN` fallback without throwing.
- `NotificationListViewModelTest` — mirrors `PoleListViewModelTest`'s shape (Loading→Success/Empty/Error,
  mark-read optimistic update, mark-all-read).
- `NotificationBadgeViewModelTest` — polling tick behavior under a `TestDispatcher`/fake clock;
  foreground/background gating via an injectable lifecycle fake (exact seam decided in the
  implementation plan).
- `NotificationListScreenTest` — Compose UI test, same pattern as the just-shipped `PoleListScreenTest.kt`
  (all 4 states, row tap → navigation callback invoked only for `work_order`, mark-all-read button).

## 8. Non-goals (explicitly out of scope for this pass)

- Firebase Cloud Messaging / push — polling only, per BE's own doc comment ("a push channel added
  later reads the same rows; these endpoints stay"). Not needed for this feature to work.
- Fixing the F06/F13 "sweep rejected" gap or wiring a real `SurveySweep`-keyed detail screen —
  tracked separately in `CLAUDE.md`, not touched here.
- Making the `ProfileScreen` notification row itself clickable/a second entry point.
- Notification preferences/settings (mute by type, etc.) — not in the backend contract at all.
