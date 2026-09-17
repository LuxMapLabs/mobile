# LUXMAP MOBILE — GLOBAL DESIGN SYSTEM FOR CLAUDE DESIGN v1.0

**Status:** Official global visual system  
**Scope:** All LuxMap Mobile interfaces  
**Platform:** Native Android · Kotlin · Jetpack Compose  
**Reference viewport:** 360 × 800 dp  
**Languages:** Vietnamese-first; English for internal component names  

> Import this file into Claude Design before generating any LuxMap Mobile interface.
> This is a screen-independent design system. It defines foundations, tokens, reusable components, patterns and generation constraints. Screen layout and business requirements must be supplied separately.

---

## 0. Instructions for Claude Design

When this file is imported, treat it as the visual source of truth for LuxMap Mobile.

### Required behavior

1. Reuse the tokens and components defined here.
2. Do not create a new color, gradient, radius, shadow or type style unless the prompt explicitly requires a new global token.
3. Design one requested interface or state at a time; do not combine unrelated screens.
4. Use Android-native behavior suitable for Jetpack Compose.
5. Keep the visual character professional, modern, operational and field-ready.
6. Prioritize outdoor readability, night operation, weak connectivity and one-handed use.
7. Show status using text plus icon/shape and color; never rely on color alone.
8. Use Vietnamese for user-facing content. Use English only for component/layer names and technical annotations.
9. Preserve safe areas and a minimum 48 × 48 dp touch target.
10. Make layouts resilient to long Vietnamese text and Android font scaling up to 200%.
11. Use Auto Layout for all structured content and reusable components.
12. Run the final QA checklist in this file before returning a design.

### Separation of concerns

This file defines **how LuxMap looks and behaves globally**. It does not prescribe the composition of a particular screen. For each design request, combine this system with a separate screen brief containing:

- user goal;
- required data;
- primary and secondary actions;
- business states;
- navigation destination;
- theme/context requirement.

Do not infer missing business rules from visual conventions.

---

## 1. Brand foundation

### 1.1 Product identity

- Product name: **LuxMap**
- Product type: GIS, IoT and computer-vision field-operations platform for rural road lighting
- Mobile users: surveyors, field technicians, repair teams and acceptance staff
- Core environments: daylight, night survey, roadside, weak network, intermittent GPS and gloved operation

### 1.2 Brand attributes

| Attribute | Design expression |
|---|---|
| Trustworthy | Deep navy, clear hierarchy, restrained effects |
| Operational | Dense enough to be useful, never decorative-first |
| Field-ready | Large targets, high contrast, direct status language |
| Technical | GIS route/node motifs, precise numbers and metadata |
| Calm | Mint accent, generous spacing, limited alert colors |
| Modern | Rounded geometry, clean surfaces, subtle gradients |

### 1.3 Visual signature

- Primary brand surface: deep navy.
- Primary accent: fresh mint.
- Supporting action/focus color: operational blue.
- Hero areas may use a navy-to-blue gradient.
- Subtle GIS motifs may use routes, nodes, coordinate grids or map contours.
- Decorative motifs must remain low contrast and never overlap text or interaction targets.
- Avoid generic neon dashboards, excessive glassmorphism and heavy 3D illustration.

### 1.4 Logo rules

- Preserve the LuxMap wordmark proportions.
- Minimum clear space: one-half of the logo-mark width on every side.
- Use the light logo on navy/dark surfaces and the navy logo on light surfaces.
- Do not recolor the wordmark with status colors.
- Do not place the logo on visually noisy map imagery without a solid/tonal container.

---

## 2. Token architecture

Use three token layers:

1. **Primitive tokens:** raw values such as `navy.700` or `space.16`.
2. **Semantic tokens:** role-based values such as `color.text.primary`.
3. **Component tokens:** scoped values such as `button.primary.container.start`.

Generated screens must reference semantic or component tokens. Raw hex values belong only in the global token definition.

### Naming convention

```text
{category}.{property}.{variant}.{state}
```

Examples:

```text
color.text.primary
surface.container.subtle
button.primary.container.default
field.border.focused
badge.asset.out.content
```

---

## 3. Color system

### 3.1 Primitive colors

| Token | Hex | Intended role |
|---|---:|---|
| `navy.900` | `#102443` | Deepest brand surface |
| `navy.800` | `#193B6A` | Strong CTA/gradient start |
| `navy.700` | `#1F3864` | Core brand and light-theme navigation |
| `blue.700` | `#2B6589` | CTA/hero gradient end |
| `blue.600` | `#2D6388` | Supporting brand surface |
| `blue.500` | `#3E86C9` | Link, information and focus |
| `mint.300` | `#7AE2CA` | Highlight on dark surfaces |
| `mint.400` | `#63D0B9` | Brand accent and ready state |
| `green.400` | `#5FC4B0` | Normal asset marker |
| `success.600` | `#059669` | Successful completion |
| `amber.500` | `#E9A23B` | Warning, dim asset, high priority |
| `rose.600` | `#D64545` | Out asset and urgent condition |
| `danger.600` | `#DC2626` | Destructive action and critical error |
| `white` | `#FFFFFF` | Base light surface |
| `gray.25` | `#F8FAFC` | Main light background |
| `gray.50` | `#F1F5F9` | Secondary light surface |
| `gray.100` | `#EEF4FA` | Icon/input tonal surface |
| `gray.200` | `#E2E8F0` | Standard light border |
| `gray.300` | `#CBD5E1` | Strong light border |
| `gray.400` | `#94A3B8` | Disabled and placeholder |
| `gray.500` | `#64748B` | Secondary content |
| `gray.600` | `#475569` | Strong metadata |
| `gray.700` | `#334155` | Secondary heading |
| `gray.900` | `#0F172A` | Primary light-theme text |
| `dark.950` | `#0D0D0D` | Main dark background |
| `dark.900` | `#121212` | Dark recessed surface |
| `dark.800` | `#1A1A1A` | Dark raised surface |
| `dark.700` | `#2A2A2A` | Dark border |
| `dark.text` | `#F5F5F5` | Primary dark-theme text |
| `dark.muted` | `#A0A0A0` | Secondary dark-theme text |

### 3.2 Semantic color tokens

| Token | Light | Dark |
|---|---:|---:|
| `color.background` | `#F8FAFC` | `#0D0D0D` |
| `color.surface` | `#FFFFFF` | `#1A1A1A` |
| `color.surface.subtle` | `#F1F5F9` | `#121212` |
| `color.surface.strong` | `#EEF4FA` | `#2A2A2A` |
| `color.text.primary` | `#0F172A` | `#F5F5F5` |
| `color.text.secondary` | `#64748B` | `#A0A0A0` |
| `color.text.onBrand` | `#FFFFFF` | `#FFFFFF` |
| `color.border` | `#E2E8F0` | `#2A2A2A` |
| `color.border.strong` | `#CBD5E1` | `#475569` |
| `color.focus` | `#3E86C9` | `#7AE2CA` |
| `color.link` | `#3E86C9` | `#7AE2CA` |
| `color.navigation.active` | `#1F3864` | `#63D0B9` |
| `color.navigation.inactive` | `#64748B` | `#A0A0A0` |
| `color.disabled.container` | `#94A3B8` | `#475569` |
| `color.disabled.content` | `#F1F5F9` | `#CBD5E1` |
| `color.overlay.scrim` | `rgba(15,23,42,.56)` | `rgba(0,0,0,.68)` |

### 3.3 Brand gradients

| Token | Definition | Use |
|---|---|---|
| `gradient.brand.hero` | `#102443 → #1F3864 58% → #2D6388` | Brand/entry hero areas |
| `gradient.action.primary` | `#193B6A → #2B6589` | High-emphasis primary CTA |

Rules:

- Use no more than one prominent gradient within one viewport.
- Do not use gradients for warnings, errors or status badges.
- Loading, disabled and destructive actions use solid semantic colors.
- Do not place small low-contrast text directly on a gradient.

### 3.4 Asset-condition colors

| State | Label | Light container | Light content | Dark container | Dark content | Map shape |
|---|---|---:|---:|---:|---:|---|
| `normal` | Bình thường | `#D1FAE5` | `#065F46` | `#123D34` | `#8CE3D1` | Circle + check |
| `dim` | Đèn mờ | `#FEF3C7` | `#92400E` | `#4A3310` | `#F7C66D` | Triangle + warning |
| `out` | Hỏng/Tắt | `#FEE2E2` | `#991B1B` | `#4A1717` | `#FF9A9A` | Diamond + X |
| `unknown` | Chưa xác định | `#EFEFEF` | `#555555` | `#303030` | `#D0D0D0` | Dashed ring + question mark |

Map fills use `green.400`, `amber.500`, `rose.600` and `gray.500` respectively. Never swap asset-state colors with sync-state colors.

### 3.5 Work priority colors

| State | Label | Content color |
|---|---|---:|
| `low` | Thấp | `#64748B` |
| `normal` | Bình thường | `#3E86C9` |
| `high` | Cao | `#E9A23B` |
| `urgent` | Khẩn | `#D64545` |

### 3.6 Sync colors

| State | Label | Container | Content |
|---|---|---:|---:|
| `queuedOffline` | Chờ mạng | `#EFEFEF` | `#555555` |
| `queuedOnline` | Chờ đồng bộ | `#E8EEF7` | `#1F3864` |
| `syncing` | Đang đồng bộ | `#FFF3DC` | `#8A5A00` |
| `failed` | Đồng bộ lỗi | `#FBE4E4` | `#9B2C2C` |
| `conflict` | Xung đột | `#FDE8D0` | `#8A3B00` |
| `done` | Đã đồng bộ | `#E3F6F1` | `#1E6B5C` |

`failed` and `conflict` are distinct states and must never share one generic visual.

---

## 4. Typography

### 4.1 Font families

- Primary production font: **Be Vietnam Pro**.
- Fallback/prototype font: **Inter**, then system sans-serif.
- Use tabular figures for measurements, time, progress, counts and distances.

### 4.2 Type scale

| Token | Size / line height | Weight | Usage |
|---|---|---:|---|
| `type.display` | 28sp / 34sp | 700 | Lux, distance, high-value metric |
| `type.h1` | 20sp / 26sp | 700 | Screen title |
| `type.h2` | 17sp / 24sp | 600 | Section/card title |
| `type.body` | 16sp / 24sp | 400 | Main content |
| `type.bodyStrong` | 16sp / 24sp | 600 | Button and important value |
| `type.caption` | 14sp / 20sp | 500 | Metadata, timestamp, helper text |
| `type.captionStrong` | 14sp / 20sp | 600 | Badge and emphasized metadata |
| `type.overline` | 12sp / 16sp | 700 | Short uppercase label |

### 4.3 Typography rules

- Use sentence case for headings, labels and actions.
- ALL CAPS is reserved for short overlines, field labels and brand text.
- Never use a placeholder as the only field label.
- Allow headings to wrap to two lines.
- Allow error, warning and sync messages to wrap without truncation.
- Never truncate asset IDs, work-order IDs or critical measurements.
- Support Android font scaling up to 200% without overlap.
- SVG/prototype pixel sizes are visual approximations; implementation must map to this `sp` scale.

---

## 5. Spacing, grid and sizing

### 5.1 Spacing scale

| Token | Value |
|---|---:|
| `space.0` | 0dp |
| `space.4` | 4dp |
| `space.8` | 8dp |
| `space.12` | 12dp |
| `space.16` | 16dp |
| `space.24` | 24dp |
| `space.32` | 32dp |
| `space.40` | 40dp |

Do not introduce arbitrary gaps such as 13dp, 19dp or 27dp.

### 5.2 Mobile grid

- Base frame: 360 × 800dp.
- Standard horizontal page margin: 16dp.
- Spacious/brand form margin: 24dp when width remains usable.
- Minimum gap between separate touch targets: 8dp.
- Section spacing: 24dp.
- Card-list gap: 12dp.
- Card internal padding: 16dp.
- Maximum form width on tablet: 720dp.

### 5.3 Size tokens

| Token | Value | Use |
|---|---:|---|
| `size.touch.minimum` | 48dp | Minimum interactive target |
| `size.control.standard` | 48dp | Standard button/control height |
| `size.control.comfortable` | 54dp | Prominent form and CTA height |
| `size.appBar` | 56dp | App bar excluding inset |
| `size.bottomNav` | 72–80dp | Bottom navigation including content area |
| `size.cameraShutter` | 64–72dp | Capture action |
| `size.icon.small` | 16dp | Inline metadata icon |
| `size.icon.medium` | 20–24dp | Standard icon |
| `size.icon.large` | 28–32dp | Empty state/feature icon |

---

## 6. Shape, border and elevation

### 6.1 Radius

| Token | Value | Use |
|---|---:|---|
| `radius.small` | 8dp | Small controls and compact elements |
| `radius.medium` | 12dp | Standard cards and controls |
| `radius.large` | 16dp | Prominent cards, fields and banners |
| `radius.sheet` | 28dp | Top corners of modal/bottom sheets |
| `radius.pill` | 999dp | Badge, chip and segmented item |

### 6.2 Borders

| Token | Value |
|---|---|
| `border.default` | 1dp `color.border` |
| `border.strong` | 1dp `color.border.strong` |
| `border.focus` | 2dp `color.focus` |
| `border.error` | 1–2dp `danger.600` |
| `border.selected` | 2dp `navy.700` in Light / `mint.400` in Dark |

### 6.3 Elevation

| Token | Value | Use |
|---|---:|---|
| `elevation.none` | 0dp | Base surfaces |
| `elevation.card` | 1dp | Cards; border may replace elevation |
| `elevation.floating` | 4dp | Floating control |
| `elevation.sheet` | 8dp | Bottom sheet/dialog |

Avoid heavy shadows in Dark Mode. Use border, tone and spacing to establish hierarchy.

---

## 7. Iconography and imagery

### 7.1 Icon style

- Use one consistent outlined icon family, visually compatible with Material Symbols Rounded.
- Standard stroke: 1.5–2dp.
- Standard optical size: 20–24dp.
- Icons use current semantic content color.
- Filled icons are reserved for selected navigation or strong status emphasis.
- Every icon-only action requires a content description.

### 7.2 Status icon mapping

| Meaning | Icon/shape |
|---|---|
| Success/normal | Check in circle |
| Warning/dim | Warning triangle |
| Fault/out | X or fault symbol in diamond/circle |
| Unknown | Question mark in dashed circle |
| Offline | Cloud-off or disconnected link |
| Sync | Circular arrows |
| Pending | Clock |
| Location | Pin |
| Current position | Navigation arrow/dot with accuracy ring |

### 7.3 Imagery

- Prefer real field photography only when it supports evidence, inspection or context.
- Evidence images use neutral containers and must not receive decorative filters.
- Empty-state illustrations should be simple line/duotone visuals using navy and mint.
- Maps and camera previews are operational content, not background decoration.

---

## 8. Core component library

Name components using `Mobile/{Category}/{Component}`.

### 8.1 Buttons

Components:

- `Mobile/Button/Primary`
- `Mobile/Button/Secondary`
- `Mobile/Button/Success`
- `Mobile/Button/Danger`
- `Mobile/Button/Text`
- `Mobile/Button/Icon`

Variant properties:

```text
State = Default | Pressed | Focused | Loading | Disabled
Size = Standard | Comfortable
Icon = None | Leading | Trailing
Width = Hug | Fill
Theme = Light | Dark
```

Rules:

- Minimum height 48dp.
- High-emphasis CTA may use `gradient.action.primary`; normal primary actions may use solid `navy.700`.
- Loading preserves button dimensions and prevents repeated taps.
- Disabled controls require nearby explanatory copy when the missing condition is not obvious.
- Danger is used only for destructive or irreversible actions.
- Do not place more than one high-emphasis primary CTA in one action region.

### 8.2 Icon Button

- Container: 48 × 48dp minimum.
- Icon: 20–24dp.
- Variants: standard, tonal, floating and destructive.
- States: default, pressed, focused, disabled and selected.
- Tooltip/content description is mandatory for ambiguous icons.

### 8.3 Text Field

Components:

- `Mobile/Input/TextField`
- `Mobile/Input/PasswordField`
- `Mobile/Input/NumericField`
- `Mobile/Input/TextArea`
- `Mobile/Input/SearchField`

Anatomy:

1. Persistent label
2. Input container
3. Optional leading icon
4. Input/placeholder text
5. Optional trailing action/unit
6. Helper or error message

Properties:

```text
State = Empty | Focused | Filled | Error | Disabled | ReadOnly
LeadingIcon = True | False
TrailingAction = None | Clear | Visibility | Selector
Theme = Light | Dark
```

Rules:

- Standard authentication/prominent height: 54dp; minimum 48dp.
- Default radius: `radius.medium`; prominent forms may use `radius.large` consistently.
- Error includes icon, explanation and corrective action when needed.
- Numeric measurements show their unit outside the editable value.
- Password visibility uses mutually exclusive eye and eye-off states.

### 8.4 Selection controls

Components: Checkbox, Radio, Switch, Segmented Control and Selector Row.

- Visual checkbox/radio: 22–24dp inside a 48dp minimum target.
- Label and control form one touch target.
- Switch is for immediate settings; checkbox is for selection/confirmation.
- Automated technical checks are represented as system status, not manual checkboxes.
- Segmented Control has 2–4 mutually exclusive options only.

### 8.5 Chips

Types: filter, input, selection and action.

- Minimum height: 36dp; interaction target must still be at least 48dp through padding/container.
- Selected state uses `navy.700`/`mint.400` plus a check or clear tonal change.
- Keep labels short; avoid multi-line chips.
- Provide `Xóa bộ lọc` when several filters can hide all data.

### 8.6 Status Badge

Properties:

```text
Type = Asset | Priority | WorkOrder | Sync | Quality
State = token-specific value
Size = Compact | Standard
Theme = Light | Dark
```

- Shape: pill.
- Content: icon/dot plus label.
- Badge is informational, not a button unless an action affordance is explicit.
- Use the correct state family; never represent sync with an asset badge.

### 8.7 Card

Base components:

- `Mobile/Card/Standard`
- `Mobile/Card/Metric`
- `Mobile/Card/Asset`
- `Mobile/Card/WorkOrder`
- `Mobile/Card/Evidence`
- `Mobile/Card/Setting`

Properties:

```text
State = Default | Pressed | Selected | Disabled | Warning | Error
Elevation = Flat | Raised
Theme = Light | Dark
```

- Default padding: 16dp.
- Default radius: 12–16dp.
- Prefer 1dp borders over strong shadows.
- If navigable, the entire card is one touch target.
- Avoid nested buttons unless actions are clearly separated.

### 8.8 Work Order Card pattern

Content priority:

1. Work-order ID and priority
2. Fault type/title
3. Address or pole ID
4. SLA and distance
5. Local/sync status

Overdue content always includes the word `Quá hạn`; a red border alone is insufficient.

### 8.9 List Row

- Minimum height: 56dp.
- Leading area: icon/avatar/status.
- Center: title and optional secondary metadata.
- Trailing: value, status or one action.
- Divider begins after leading content when applicable.
- Swipe actions are optional and must have a visible alternative.

### 8.10 App Bar

Properties:

```text
Navigation = None | Back | Close
Actions = 0 | 1 | 2
SyncIndicator = Hidden | Visible
Notification = Hidden | Visible
Theme = Light | Dark | Overlay
```

- Height: 56dp plus status-bar inset.
- Navigation/action targets: 48dp minimum.
- Titles remain concise and may wrap only when the screen structure supports it.
- Focused camera/navigation contexts use an overlay bar instead of a solid standard bar.

### 8.11 Bottom Navigation

Exactly four global destinations:

1. `Việc hôm nay`
2. `Khảo sát`
3. `Bản đồ`
4. `Cá nhân`

Properties:

```text
Selected = Today | Survey | Map | Profile
SyncBadge = None | Dot | Count
Theme = Light | Dark
```

- Icon plus label is always visible.
- Selected item uses color plus filled/stronger icon treatment.
- Hide in authentication, full-screen camera, focused navigation and full-screen confirmation contexts.

### 8.12 Tabs

- Use for peer sections within one context, not global navigation.
- Minimum target height: 48dp.
- Active state uses label weight plus indicator; not color alone.
- Keep the tab count low enough to avoid clipped Vietnamese labels.

### 8.13 Banner

Types: information, offline, stale data, warning, error, sync blocked and success.

Anatomy:

1. Status icon
2. Title or concise statement
3. Optional detail
4. Optional action

Canonical offline language:

- `Đang ngoại tuyến — thay đổi sẽ lưu trên thiết bị`
- `Dữ liệu cập nhật lần cuối lúc …`
- `Đang chờ Wi-Fi để tải … GB`

Banner must not cover navigation or a fixed CTA. Important banners receive a TalkBack announcement.

### 8.14 Snackbar

- Use for short feedback, local save, retry and successful non-blocking actions.
- Default duration: long enough to read in Vietnamese.
- One optional text action.
- Do not use Snackbar for destructive confirmation or long error instructions.
- Correct offline feedback: `Đã lưu trên thiết bị — sẽ đồng bộ khi có mạng`.

### 8.15 Dialog

Types: confirmation, destructive, permission explanation and unsaved changes.

- Title states the decision.
- Body explains consequence.
- Primary and secondary actions use explicit verbs.
- Destructive action appears as Danger and is not the default focus.
- Do not use dialogs for routine information.

### 8.16 Bottom Sheet

Types: selector, action menu, map preview, filter, conflict detail and contextual detail.

- Top radius: `radius.sheet`.
- Include a drag handle when dismissible.
- Respect navigation/gesture inset.
- Use a sticky CTA only when the content can scroll beneath it safely.
- Modal sheet elevation: `elevation.sheet`.

### 8.17 Progress

Components: linear progress, circular progress, step progress and upload progress.

- Determinate work shows percentage or item/byte count.
- Indeterminate spinner is used only when progress cannot be measured.
- Long-running sync displays state, completed count, total count and retry/pause when supported.
- Never remove the user's content and show only a spinner if cached content remains usable.

### 8.18 Empty State

Anatomy: simple icon/illustration, clear title, one-sentence guidance and optional CTA.

Differentiate:

- no data exists;
- no filter results;
- data unavailable offline;
- permission prevents access.

### 8.19 Loading and Skeleton

- Skeleton geometry matches final content.
- Use subtle tonal animation and respect Reduce Motion.
- Do not show fake values inside loading cards.
- Preserve layout to avoid large content jumps.

---

## 9. Operational patterns

### 9.1 Data-state model

Every asynchronous region supports, where relevant:

```text
Loading → Data
Loading → Empty
Loading → Error + Retry
Data → Cached/Stale
Data → Refreshing
Local change → Queued → Syncing → Done
                         ↘ Failed
                         ↘ Conflict
```

The UI must state whether displayed data is live, cached or stale when that distinction affects field decisions.

### 9.2 Offline-first feedback

- Local persistence and server synchronization are separate events.
- Use `Đã lưu trên thiết bị` after a local save.
- Use `Đã đồng bộ` only after server confirmation.
- Do not disable normal field data entry merely because the network is unavailable if offline operation is supported.
- Never auto-delete unsynchronized data.
- Warn before logout or cache removal when pending data exists.

### 9.3 Error hierarchy

| Level | Component | Example use |
|---|---|---|
| Field | Inline Error | Invalid value or missing evidence |
| Section | Inline State/Card | One data panel failed |
| Screen | Full-page Error | Core screen content unavailable |
| Global temporary | Banner/Snackbar | Network loss, retryable sync issue |
| Blocking decision | Dialog | Destructive action, unresolved conflict |

Every actionable error answers:

1. What happened?
2. Is the user's data safe?
3. What should the user do next?

### 9.4 Permission pattern

- Explain benefit before requesting sensitive permission.
- Show which feature is limited when permission is denied.
- Provide `Mở cài đặt` only after the system no longer allows a direct request.
- Never imply permission is granted before Android confirms it.

### 9.5 Destructive action pattern

- Use explicit verbs such as `Xóa dữ liệu`, `Đăng xuất` or `Hủy bản nháp`.
- State whether the action is recoverable.
- Surface pending-sync impact before confirmation.
- Use a Danger button and a neutral cancel action.

---

## 10. Map system

### 10.1 Map component set

- `Mobile/Map/AssetMarker`
- `Mobile/Map/WorkOrderMarker`
- `Mobile/Map/UserLocation`
- `Mobile/Map/HeadingCone`
- `Mobile/Map/Cluster`
- `Mobile/Map/Route`
- `Mobile/Map/Legend`
- `Mobile/Map/LayerControl`
- `Mobile/Map/ZoomControl`
- `Mobile/Map/OfflineRegionStatus`
- `Mobile/Map/Attribution`

### 10.2 Map rules

- Basemap remains visually quieter than operational markers.
- Asset markers use the four asset-state color/shape mappings.
- Selected marker gains a high-contrast ring and modest scale increase.
- Cluster shows point count and highest severity; it must remain distinguishable from a single asset marker.
- User location uses a blue/mint dot with accuracy ring; never use an asset marker shape.
- Heading cone is translucent and must not obscure nearby markers.
- Legend names every visible status and supports Light/Dark surfaces.
- Attribution remains visible and is never covered permanently by sheets/navigation.
- When tiles fail, show an explanatory state while preserving available operational data when technically possible.
- Offline region state states whether map coverage is downloaded, partial, expired or unavailable.

### 10.3 Map interaction

- Tap cluster: zoom/expand.
- Tap asset marker: select and open contextual preview.
- Tap empty map area: clear selection when safe.
- Long press: only for explicitly supported placement/reporting actions.
- Map controls have 48dp targets and maintain edge-safe placement.

---

## 11. Camera and evidence system

### 11.1 Camera Readiness Panel

Automatic checks may include:

- camera permission;
- manual exposure support;
- exposure lock;
- GPS accuracy and heading;
- free storage;
- battery threshold.

Each check has `Checking`, `Pass` and `Fail` states plus corrective guidance. Automated checks are not manual checkboxes.

### 11.2 Camera Overlay

- Dark full-screen preview.
- No global Bottom Navigation.
- Shutter: 64–72dp.
- Pause/Resume/End targets: 48dp minimum.
- Show relevant capture metadata: exposure, GPS accuracy, heading, frame count, distance and storage.
- Warnings use icon + text + distinctive haptic.
- Keep safety-critical data clear of the shutter and gesture area.

### 11.3 Evidence Card

States: captured, processing, valid, invalid metadata, upload queued, upload failed and synced.

- Show thumbnail, capture time, location/asset relation and sync state.
- Never apply aesthetic filters to evidence photos.
- Deletion requires confirmation when evidence has not been synchronized or is required for completion.

---

## 12. Motion and haptics

### Motion tokens

| Token | Duration | Use |
|---|---:|---|
| `motion.fast` | 150ms | Press/focus state |
| `motion.standard` | 200ms | Component transition |
| `motion.slow` | 250ms | Sheet/content transition |

- Use standard easing; avoid playful bounce in operational flows.
- Honor Android Reduce Motion.
- Map camera animation should be functional and interruptible.
- Do not animate critical values in a way that delays reading.

### Haptic rules

- Light confirmation: capture or local save success.
- Distinct warning: invalid GPS/exposure or blocked completion.
- Strong warning: destructive confirmation only.
- Haptics supplement visual/audio feedback; they never replace it.

---

## 13. Accessibility

- Normal text contrast ≥ 4.5:1.
- Large text and UI component contrast ≥ 3:1.
- Touch targets ≥ 48 × 48dp.
- Support TalkBack with meaningful content descriptions.
- Focus order follows visual and business order.
- Merge related label/value semantics where it improves reading.
- Announce significant offline, sync and validation state changes.
- Support font scaling to 200% without clipped controls.
- Never encode status using color alone.
- Provide text alternatives for map markers and image evidence.
- Avoid rapid flashing and unnecessary continuous motion.
- Keep time-sensitive actions usable without requiring precise gestures.

---

## 14. Content design

### Voice and tone

- Direct, calm and respectful.
- Action-oriented without blaming the user.
- Prefer familiar Vietnamese over technical implementation terms.
- Keep titles concise; put details in helper text.

### Canonical terminology

| Use | Avoid |
|---|---|
| Cột đèn | Asset (in user-facing UI) |
| Lệnh công việc | Work order when Vietnamese context is expected |
| Đã lưu trên thiết bị | Saved/Done when only local persistence occurred |
| Đã đồng bộ | Synced before server confirmation |
| Chờ Wi-Fi | Network error when Wi-Fi policy is the reason |
| Thử lại | Retry |
| Chưa xác định | Unknown without explanation |

### Numbers and units

- Use Vietnamese number formatting where applicable.
- Put a non-breaking space between value and unit when supported: `25 lux`, `120 m`, `1,2 GB`.
- Show timestamps with enough context for field decisions.
- Keep IDs exactly as stored; do not localize or abbreviate them.

### Error-writing template

```text
[What happened]. [Whether data is safe]. [Next action].
```

Example:

```text
Chưa thể đồng bộ ảnh. Ảnh vẫn được lưu trên thiết bị. Hãy thử lại khi kết nối ổn định.
```

---

## 15. Theme rules

### Light theme

- Default for general daytime and administrative use.
- Main background: `color.background`.
- Cards: white surface with border or low elevation.
- Primary action: navy or approved primary gradient.

### Dark theme

- Required for full-screen night capture; recommended for night navigation/planning.
- Use `dark.950` background and `dark.800` raised surfaces.
- Mint becomes the primary focus/navigation accent.
- Avoid large pure-white areas and high-glare backgrounds.
- Do not generate Dark Mode by simply inverting Light Mode.
- Re-evaluate images, maps, borders, badges and overlays individually.

### Theme inheritance

- Default to system theme unless the operating context requires Dark Mode.
- A focused dark operational mode may override the system theme temporarily.
- Preserve state meaning across themes.

---

## 16. Responsive and system behavior

- Respect Android status bar, navigation bar, display cutout and gesture insets.
- When the keyboard opens, keep the focused field, validation message and relevant CTA visible.
- Lists and forms scroll; maps and camera previews fill available space.
- Sticky CTAs sit above gesture areas and never cover scrollable content.
- On compact-height devices, reduce decorative space before reducing content readability.
- On landscape, prioritize map/camera content and move controls to safe edges.
- On tablets, center forms at maximum 720dp or use master-detail for maps and lists.

---

## 17. Design asset structure

Recommended editable hierarchy:

```text
LuxMap Mobile
├── Foundations
│   ├── Colors
│   ├── Typography
│   ├── Spacing
│   ├── Radius
│   ├── Elevation
│   ├── Icons
│   └── Motion
├── Components
│   ├── Actions
│   ├── Inputs
│   ├── Navigation
│   ├── Cards
│   ├── Status
│   ├── Feedback
│   ├── Maps
│   └── Camera & Evidence
└── Patterns
    ├── Loading & Empty
    ├── Offline & Sync
    ├── Permission
    ├── Errors
    └── Destructive Actions
```

### Component properties

Use consistent variant keys:

```text
Theme = Light | Dark
State = Default | Pressed | Focused | Selected | Loading | Error | Disabled
Size = Compact | Standard | Comfortable
Icon = None | Leading | Trailing | Only
Width = Hug | Fill
```

Do not flatten text, vector icons or component instances in editable deliverables.

---

## 18. Claude Design output contract

For every requested interface, Claude Design must return:

1. One primary artboard for the requested state.
2. Additional artboards only when the user explicitly requests variants.
3. Reusable `Mobile/*` component instances.
4. Clear Auto Layout and constraints.
5. Named layers: `system-bars`, `app-bar`, `content`, `navigation`, `overlay`, `feedback` as applicable.
6. A short note listing the semantic tokens and components used.
7. A QA result using the checklist below.

When the screen brief is incomplete, ask only for business information that changes layout or workflow. Do not ask the user to choose arbitrary visual styles already settled by this system.

### Recommended generation prompt

```text
Use the imported “LUXMAP MOBILE — GLOBAL DESIGN SYSTEM FOR CLAUDE DESIGN v1.0” as the only visual source of truth.

Create: [screen or component requested]
User goal: [goal]
Required content: [data/content]
Primary action: [action]
Secondary actions: [actions]
Required states: [states]
Theme/context: [Light, Dark, system, night, map, camera]
Frame: 360×800dp Android mobile

Requirements:
- Reuse global tokens and Mobile/* components.
- Use Vietnamese user-facing copy.
- Do not invent business rules or a new visual language.
- Respect safe areas, 48dp touch targets, TalkBack and 200% font scaling.
- Produce only the requested interface/state.
- Complete the mandatory QA checklist before delivery.
```

---

## 19. Mandatory QA checklist

### Visual consistency

- [ ] Uses only approved primitive, semantic and component tokens.
- [ ] Belongs clearly to the LuxMap navy–mint visual language.
- [ ] Uses no unnecessary gradient, radius, shadow or decorative motif.
- [ ] Maintains clear hierarchy and consistent spacing.
- [ ] Uses one outlined icon family consistently.

### Usability

- [ ] All interactive targets are at least 48 × 48dp.
- [ ] Primary action is obvious and unique within its action region.
- [ ] Long Vietnamese text wraps without collision or clipping.
- [ ] Keyboard, safe areas and gesture navigation do not hide content.
- [ ] Screen remains understandable without relying on color.

### State completeness

- [ ] Relevant loading, data, empty and error states are defined.
- [ ] Offline cached/stale state is included where relevant.
- [ ] Local save and server sync are communicated separately.
- [ ] Disabled actions explain unmet conditions.
- [ ] Destructive actions disclose pending-data impact.

### Accessibility

- [ ] Contrast meets 4.5:1 for normal text and 3:1 for large/UI content.
- [ ] Layout remains usable at 200% font scaling.
- [ ] Icon-only controls have meaningful labels.
- [ ] Focus order follows visual and operational sequence.
- [ ] Important state changes can be announced to TalkBack.

### Operational quality

- [ ] Map attribution remains visible when a map is present.
- [ ] Camera controls stay clear of system gestures when a camera is present.
- [ ] GPS, storage, network and sync limitations are explicit when relevant.
- [ ] Evidence and unsynchronized data are never implied to be safely uploaded before confirmation.
- [ ] The design does not invent unsupported business behavior.

If any applicable item fails, revise the design before presenting it.

---

## 20. Governance

- This file is the single global visual source of truth for LuxMap Mobile.
- Screen specifications must reference this system rather than duplicate global tokens.
- A screen-specific need becomes a global component only after it is reusable or intentionally standardized.
- New tokens require a name, role, Light/Dark value where applicable and migration note.
- Do not change the meaning of an existing status color between versions.
- Versioning follows semantic intent:
  - Patch: wording or clarification with no visual change.
  - Minor: new backward-compatible token/component/pattern.
  - Major: breaking visual or component architecture change.
- Every update must include a token audit, component audit, accessibility review and Claude-generation test.

## Version record

| Version | Status | Scope |
|---|---|---|
| 1.0 | Official | Initial screen-independent Global Design System for Claude Design |

