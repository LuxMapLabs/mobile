# Compose Implementation Mapping: LuxMap Mobile Design System vNext

> Trạng thái: **Hướng dẫn ánh xạ, chưa phải code trong repo.** Đoạn mã dưới đây là bản phác thảo để chứng minh token chuyển được thành Kotlin. Khi triển khai thật phải theo quy ước repo: không thêm comment, commit ≤400 dòng, `ktlintCheck`.
> Phiên bản kiểm tra: Compose BOM `2024.10.01` (material3 1.3.x), Kotlin 2.0.21, minSdk 26.

## 1. Cấu trúc file đề xuất (`core/theme/`)

| File | Nội dung | Thay cho |
|---|---|---|
| `Color.kt` | Chỉ primitive (`internal`) | Primitive + gradient + badge functions trộn lẫn hiện nay |
| `ColorScheme.kt` 🆕 | `LuxLightColorScheme`, `LuxNightFieldColorScheme` | `LuxMapLightColorScheme`/`DarkColorScheme` trong `Theme.kt` |
| `ExtendedColors.kt` 🆕 | `LuxExtendedColors` (success/warning/info/disabled/focus/due/overlay) + `LuxStatusColors` (asset/wo/priority/sync/iot/kind) + `LuxMapColors` | `badgeColors()` rải rác, `DueTodayColor` |
| `Type.kt` | `LuxTypography` đủ 15 slot | 8 slot + 10 `*TextStyle` rời |
| `Shape.kt` 🆕 | `LuxShapes` | `Dimens.radius*` + `Radius.*` |
| `Dimens.kt` | `LuxSpacing`, kích thước component (touch target, nút, list item) | `Spacing`, `Dimens`, `ScreenMargin`… |
| `Theme.kt` | `LuxMapTheme(forceDark)` + `LuxTheme` accessor | |

Primitive để `internal` trong module `core/theme`, nên `feature/` **không import được**. Đây là luật ở `COLOR_SYSTEM.md` mục 1, có compiler hỗ trợ (chỉ đúng khi tách module; hiện app 1 module nên `internal` chưa đủ. ⚠️ T-15: cần ktlint/detekt rule hoặc review checklist thay thế).

## 2. ColorScheme

```kotlin
internal val LuxLightColorScheme = lightColorScheme(
    primary = Navy700, onPrimary = White,
    primaryContainer = Navy100, onPrimaryContainer = Navy800,
    secondary = Slate600, onSecondary = White,
    secondaryContainer = Gray200, onSecondaryContainer = Gray900,
    tertiary = Blue600, onTertiary = White,
    tertiaryContainer = Blue50, onTertiaryContainer = Blue900,
    background = Gray25, onBackground = Gray900,
    surface = White, onSurface = Gray900,
    surfaceVariant = Gray50, onSurfaceVariant = Slate600,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = White, surfaceContainerLow = Gray25,
    surfaceContainer = Gray50, surfaceContainerHigh = Gray150,
    surfaceContainerHighest = Gray200,
    surfaceDim = Gray200, surfaceBright = White,
    inverseSurface = Slate800, inverseOnSurface = Gray50, inversePrimary = Navy200,
    outline = Gray500, outlineVariant = Gray200,
    error = Red700, onError = White,
    errorContainer = Red50, onErrorContainer = Red900,
    scrim = Gray900,
)
```

`LuxNightFieldColorScheme` dùng `darkColorScheme(...)` với cột Night Field của `COLOR_SYSTEM.md` 4.1–4.4. Lưu ý:
- `scrim` truyền **màu đặc**. Alpha 56%/68% áp ở nơi vẽ scrim. M3 `ModalBottomSheet` dùng `scrimColor` riêng: truyền `MaterialTheme.colorScheme.scrim.copy(alpha = 0.56f)` (Light) / `0.68f` (NF).
- **Không** gọi `dynamicLightColorScheme`/`dynamicDarkColorScheme` (quyết định #8).

## 3. Token mở rộng: `LuxExtendedColors`, `LuxStatusColors`, `LuxMapColors`

M3 không có slot cho success/warning/info hay trạng thái nghiệp vụ, nên dùng `CompositionLocal`:

```kotlin
@Immutable
data class LuxBadgeColors(val container: Color, val content: Color)

@Immutable
data class LuxExtendedColors(
    val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val onWarning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val info: Color, val onInfo: Color, val infoContainer: Color, val onInfoContainer: Color,
    val disabledContainer: Color, val disabledContent: Color,
    val focusRing: Color, val overlayOnMedia: Color,
    val dueOverdue: Color, val dueToday: Color, val dueFuture: Color,
)

@Immutable
data class LuxStatusColors(
    val asset: Map<AssetCondition, LuxBadgeColors>,
    val wo: Map<WorkOrderStatus, LuxBadgeColors>,
    val priority: Map<WorkOrderPriority, LuxBadgeColors>,
    val sync: Map<SyncStatus, LuxBadgeColors>,
    val iot: Map<IotStatus, LuxBadgeColors>,
    val kind: LuxBadgeColors,
)

val LocalLuxExtendedColors = staticCompositionLocalOf<LuxExtendedColors> { error("LuxMapTheme missing") }
val LocalLuxStatusColors = staticCompositionLocalOf<LuxStatusColors> { error("LuxMapTheme missing") }
val LocalLuxMapColors = staticCompositionLocalOf<LuxMapColors> { error("LuxMapTheme missing") }

object LuxTheme {
    val colors: LuxExtendedColors @Composable @ReadOnlyComposable get() = LocalLuxExtendedColors.current
    val status: LuxStatusColors @Composable @ReadOnlyComposable get() = LocalLuxStatusColors.current
    val map: LuxMapColors @Composable @ReadOnlyComposable get() = LocalLuxMapColors.current
}
```

- **Thay** `AssetCondition.badgeColors(isDark)`, `SyncStatus.badgeColors()`… bằng `LuxTheme.status.sync.getValue(status)`. Badge tự đúng theme, không cần tham số `isDark`, sửa luôn A8 audit (`isSystemInDarkTheme()` sai trong màn `forceDark`).
- `IotStatus` (online/offline/none) là enum **trình bày** mới trong `core/theme`, ánh xạ từ `node_status` string + `has_iot_node`. **Không** đổi DTO.
- Nhãn (`label()`) và icon (`icon()`) giữ ở hàm mở rộng hiện có. Chỉ màu chuyển vào theme.
- `LuxMapColors` là màu marker/route/selection, **dùng cho MapLibre** (cần `toArgb()`). Marker phụ thuộc **nền bản đồ**, không phụ thuộc theme: cung cấp `selectionRing(isSatellite: Boolean)`.

## 4. Typography

```kotlin
private fun luxStyle(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = BeVietnamPro, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

val LuxTypography = Typography(
    displayLarge = luxStyle(34, 42, FontWeight.Bold),
    displayMedium = luxStyle(30, 38, FontWeight.Bold),
    displaySmall = luxStyle(28, 36, FontWeight.Bold),
    headlineLarge = luxStyle(26, 36, FontWeight.Bold),
    headlineMedium = luxStyle(24, 33, FontWeight.Bold),
    headlineSmall = luxStyle(22, 30, FontWeight.SemiBold),
    titleLarge = luxStyle(20, 28, FontWeight.Bold),
    titleMedium = luxStyle(17, 24, FontWeight.SemiBold),
    titleSmall = luxStyle(16, 24, FontWeight.SemiBold),
    bodyLarge = luxStyle(16, 24, FontWeight.Normal),
    bodyMedium = luxStyle(16, 22, FontWeight.Normal),
    bodySmall = luxStyle(14, 20, FontWeight.Medium),
    labelLarge = luxStyle(16, 24, FontWeight.SemiBold),
    labelMedium = luxStyle(14, 20, FontWeight.SemiBold),
    labelSmall = luxStyle(14, 20, FontWeight.Medium),
)
```

- **Không** đặt `fontFeatureSettings = "tnum"`: font không có feature này (`TYPOGRAPHY.md` 1). Số động dùng `Modifier.widthIn(min = …)`.
- Compose UI 1.7 mặc định `includeFontPadding = false`. Giữ mặc định, chỉ dựa vào `lineHeightStyle`.

## 5. Shapes

```kotlin
val LuxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
```

Bottom sheet: `ModalBottomSheet(shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)))`. Nút: `shape = MaterialTheme.shapes.medium`, vì M3 mặc định nút dùng pill `full` nên phải override ở wrapper `LuxPrimaryButton`.

## 6. MaterialTheme + theme switching

```kotlin
@Composable
fun LuxMapTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val night = forceDark || isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalLuxExtendedColors provides if (night) NightFieldExtended else LightExtended,
        LocalLuxStatusColors provides if (night) NightFieldStatus else LightStatus,
        LocalLuxMapColors provides LuxMapColorsDefault,
    ) {
        MaterialTheme(
            colorScheme = if (night) LuxNightFieldColorScheme else LuxLightColorScheme,
            typography = LuxTypography,
            shapes = LuxShapes,
            content = content,
        )
    }
}
```

- Giữ **chữ ký hiện có** `LuxMapTheme(forceDark: Boolean = false)`, nên mọi chỗ gọi (`MainActivity`, 2 màn camera) không phải sửa. Không đổi cơ chế chọn theme (CLAUDE.md "Dark Mode theo ngữ cảnh").
- Màn `forceDark` bọc riêng `LuxMapTheme(forceDark = true) { … }` như hiện nay. Token mở rộng **cũng** đổi theo, nhờ `CompositionLocalProvider` nằm trong cùng hàm.
- Status bar icon: màn `forceDark` phải đặt icon status bar sáng (`WindowCompat.getInsetsController(...).isAppearanceLightStatusBars = false`) trong lúc hiển thị, và khôi phục khi rời. Hiện chưa xử lý, cần kiểm tra khi redesign.

## 7. Quy ước component (reusable composables)

| Quy ước | Chi tiết |
|---|---|
| Tên | Tiền tố `Lux` (`LuxPrimaryButton`, `LuxStatusBadge`, `LuxTopBar`…). Đặt ở `core/ui/components/` |
| Tham số | `modifier: Modifier = Modifier` là tham số tuỳ chọn **đầu tiên**. Dữ liệu là tham số `@Immutable`/stable. Không nhận `ViewModel` |
| Stateless | Component không giữ business state. State hoisting qua tham số + callback (quy tắc Compose của UI UX Pro Max: "Pure UI composables", "Stateless by default") |
| Màu/chữ | Chỉ từ `MaterialTheme.*` / `LuxTheme.*`. **Cấm** `Color(0x…)`, primitive, `fontSize = …sp` trong `feature/` |
| Ripple | `Modifier.clip(shape)` **trước** `clickable`, hoặc dùng component M3 có `shape`. Ripple không tràn góc bo |
| Touch target | Component tự đảm bảo ≥48dp (`Modifier.minimumInteractiveComponentSize()` cho phần tử hình nhỏ hơn) |
| Lazy list | `items(list, key = { it.id })` bắt buộc |
| Thay thế | Component cũ (`PrimaryButton`, `StatusBadge`, `InlineBanner`, `MessageState`, `AppTopBar`, `BottomActionBar`, `OutcomeSegment`, `TopSlideToast`, 3× `SkeletonBar`…) được thay **dần theo từng màn** ở giai đoạn redesign, không xoá một lần |

## 8. Semantic properties: mẫu chuẩn

```kotlin
Modifier
    .clip(MaterialTheme.shapes.medium)
    .selectable(selected = isSelected, role = Role.Tab, onClick = onSelect)
    .semantics { stateDescription = if (isSelected) "Đang chọn" else "Chưa chọn" }

Modifier.semantics(mergeDescendants = true) { }
    .clickable(role = Role.Button, onClickLabel = "Mở chi tiết lệnh", onClick = onOpen)

Modifier.semantics { liveRegion = LiveRegionMode.Polite }

Modifier.semantics { heading() }

Modifier.semantics {
    disabled()
    stateDescription = "Bị khóa: còn 2 sự cố chưa có kết quả"
}
```

## 9. Preview Light / Night Field

```kotlin
@Preview(name = "Light", showBackground = true)
@Preview(name = "Night Field", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light · font 2.0", fontScale = 2f, showBackground = true)
annotation class LuxPreviews
```

- Mỗi component dùng chung có `@LuxPreviews` cho **mọi state** (Default/Disabled/Loading/Error…).
- Mỗi màn có preview cho **4 UiState** × 2 theme. F09 đã có tiền lệ preview nhiều state.
- Preview **không** thay thế kiểm tra thủ công (`ACCESSIBILITY.md` 2).

## 10. Phụ thuộc cần xác nhận (không tự thêm)

| Nhu cầu | Thư viện | Tình trạng |
|---|---|---|
| `collectAsStateWithLifecycle` (quy tắc Compose #15 của UI UX Pro Max; NavGraph/PoleDetail đang dùng `collectAsState`) | `androidx.lifecycle:lifecycle-runtime-compose` | ⚠️ **Chưa có** trong `libs.versions.toml` (chỉ có `lifecycle-runtime-ktx` 2.8.7). Thêm là **thư viện mới**, cần duyệt (T-16) |
| Icon `Sensors`, `SensorsOff`, `SatelliteAlt`, `WarningAmber`, `PriorityHigh`, `HelpOutline`, `RemoveCircleOutline`, `MyLocation`, `Layers` | `material-icons-extended` | ✅ Đã có |
| Font số `tnum` | (Roboto hệ thống, không cần thư viện) | ⚠️ T-05 |
