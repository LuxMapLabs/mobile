# Brand Visual Refresh "Cột đèn" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm bản sắc thương hiệu "Cột đèn" (hero navy phẳng + màu nhấn vàng Lumen + minh hoạ cột đèn) cho toàn bộ app mà không dùng gradient và không đổi nghiệp vụ.

**Architecture:** Thêm một lớp token thương hiệu riêng (`LuxBrandColors`, cung cấp qua `CompositionLocal` như `LuxExtendedColors`) và 3 component dùng chung (`LuxHero`/`LuxTabHero`, `LuxStatTile`, `LuxLampIllustration`). Các màn tab gốc thay `LuxTabTopBar` bằng `LuxTabHero`; mọi màn con nhận thanh tiêu đề navy chỉ bằng cách đổi màu `LuxChildTopBar`; empty/error state dùng chung `StateLayout` nên đổi một chỗ.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit (unit), Compose UI Test (androidTest, chạy trên máy thật SM-A075F). Không thêm thư viện.

**Spec:** `docs/superpowers/specs/2026-11-25-brand-visual-refresh-design.md`

## Global Constraints

- Không gradient, không M3 Expressive, không dynamic color (quyết định vNext #5, #7, #8).
- Navy700 `#1F3864` giữ nguyên; font Be Vietnam Pro; không tạo `TextStyle` ngoài 15 slot trong `Type.kt`.
- `Lumen` `#FFC44D` chỉ làm fill/viền/minh hoạ, **không** làm màu chữ hoặc icon đơn lẻ trên nền sáng.
- Trạng thái nghiệp vụ (quá hạn, cảnh báo, lỗi) giữ nguyên token hiện có, luôn kèm icon + nhãn; không mượn Lumen.
- Vùng chạm tối thiểu 48dp; chữ ≥ 14sp; hero phải đạt chữ ≥ 4,5:1 (nội dung chính ≥ 7:1).
- Không đổi nghiệp vụ, navigation, DTO, endpoint. Không thêm thư viện.
- `feature/` chỉ đọc `MaterialTheme.*` và `LuxTheme.*` (kể cả `LuxTheme.brand` mới), không import primitive hay viết `Color(0x…)`.
- Mỗi commit ≤ 400 dòng. Commit message tiếng Anh dạng `type(brand): description`. Không đụng `main`; nhánh làm việc tạo từ `dev`.
- Không có comment mới trong code (feedback của chủ dự án). Chạy `./gradlew ktlintCheck` sau mỗi task.
- Không tự chạy toàn bộ test suite; chỉ chạy các test nêu tên trong từng task.

## Spec deviations (đã thống nhất khi lập plan, cần chủ dự án biết)

1. **Bản đồ F12 không nằm trong plan này.** README vNext ghi "Cần duyệt trước khi redesign bản đồ: T-09, T-10" và hai điểm đó chưa được duyệt. Bản đồ giữ nguyên.
2. **Chỉ làm màn có thật trong code.** Không tồn tại màn riêng cho F08 (danh sách việc nằm trong Home), F11 và F13 trong `feature/`. Plan áp dụng cho: Login, Home, Hub Khảo sát, Cá nhân, và qua thanh tiêu đề dùng chung cho mọi màn con (Thông báo, danh sách cột, Chi tiết lệnh, chi tiết cột, kết quả ảnh, chụp bằng chứng).
3. **Hero "thu gọn" ở màn con = thanh tiêu đề navy cố định**, không phải hero cuộn cùng nội dung. Lý do: thanh nằm trong `Scaffold.topBar`; hero cuộn cần cấu trúc lại từng màn, rủi ro cao trong 1 tuần.
4. **Ô số liệu Home nằm trong hero**, không "nổi đè nửa trên nửa dưới". Lý do: thanh `topBar` của `Scaffold` luôn vẽ đè lên nội dung nên hiệu ứng nổi đè không ổn định.
5. **Thêm xử lý icon thanh trạng thái.** `enableEdgeToEdge()` mặc định dùng icon tối ở chế độ sáng; trên hero navy cần icon sáng. Spec chưa nêu.

## Review Focus

Các tình huống spec ngụ ý nhưng task thường không tự kiểm tra, mỗi dòng đã có test ở task tương ứng:

1. Lumen bị dùng làm chữ trên nền sáng → `BrandColorsTest` kiểm tra chữ trên Lumen luôn dùng `onLumen` và Lumen chỉ được xác nhận đạt AA trên hero (Task 1).
2. Font scale 1,3 làm vỡ ô số liệu hoặc hero che mất danh sách → test `HomeLargeFontTest` hiện có chạy lại, kèm kiểm tra tiêu đề hiển thị (Task 6).
3. Hero Night Field không đọc được → `BrandColorsTest` kiểm tra cả hai theme (Task 1).
4. Icon thanh trạng thái bị đảo sai sau khi chuyển màn qua lại → bộ đếm tham chiếu trong `StatusBarIcons` có test đơn vị (Task 2).
5. Màn trạng thái rỗng/lỗi mất thông tin (tiêu đề, nút Thử lại) khi thêm minh hoạ → `LuxDataStatesTest` hiện có chạy lại (Task 3).

## File Structure

| File | Trách nhiệm |
|---|---|
| `core/theme/BrandColors.kt` (tạo) | `LuxBrandColors`, bảng Light/Night Field, `LocalLuxBrandColors` |
| `core/theme/ExtendedColors.kt` (sửa) | thêm `LuxTheme.brand` |
| `core/theme/Theme.kt` (sửa) | cung cấp `LocalLuxBrandColors` |
| `core/ui/components/StatusBarIcons.kt` (tạo) | bộ đếm tham chiếu đặt icon thanh trạng thái sáng |
| `core/ui/components/LuxHero.kt` (tạo) | `LuxHero`, `LuxTabHero`, `LuxStatTile` |
| `core/ui/components/LuxLampIllustration.kt` (tạo) | minh hoạ cột đèn dùng cho empty/error |
| `core/ui/components/LuxDataStates.kt` (sửa) | `StateLayout` dùng minh hoạ |
| `core/ui/components/LuxTopBars.kt` (sửa) | thanh tiêu đề con navy; `NotificationAction` thành `internal` |
| `feature/auth/ui/LoginScreen.kt`, `feature/home/ui/HomeScreen.kt`, `feature/survey/ui/hub/SurveyHubScreen.kt`, `feature/profile/ui/ProfileScreen.kt` (sửa) | dùng hero |
| `docs/design-system/vNext/*`, `CLAUDE.md` (sửa) | ghi quyết định mới |

---

### Task 0: Chuẩn bị nhánh

**Files:** không có file code.

- [ ] **Step 1: Tạo nhánh từ `dev`**

```bash
git checkout dev
git pull
git checkout -b style/brand-visual-refresh
```

Spec và plan này đang là file chưa theo dõi, chúng sẽ đi theo sang nhánh mới.

- [ ] **Step 2: Commit spec và plan**

```bash
git add docs/superpowers/specs/2026-11-25-brand-visual-refresh-design.md docs/superpowers/plans/2026-11-25-brand-visual-refresh.md
git commit -m "docs(brand): add brand visual refresh spec and plan"
```

---

### Task 1: Token thương hiệu `LuxBrandColors`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/theme/BrandColors.kt`
- Modify: `app/src/main/java/com/luxmap/core/theme/ExtendedColors.kt` (object `LuxTheme`, cuối file)
- Modify: `app/src/main/java/com/luxmap/core/theme/Theme.kt:100-103`
- Test: `app/src/test/java/com/luxmap/core/theme/BrandColorsTest.kt`

**Interfaces:**
- Produces: `data class LuxBrandColors(hero, onHero, onHeroVariant, heroShapeA, heroShapeB, lumen, onLumen: Color)`; `internal val LightBrandColors`, `internal val NightFieldBrandColors`; `val LocalLuxBrandColors`; `LuxTheme.brand: LuxBrandColors` (`@Composable @ReadOnlyComposable`).

- [ ] **Step 1: Viết test thất bại**

```kotlin
package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandColorsTest {
    private val themes =
        listOf(
            "light" to LightBrandColors,
            "night" to NightFieldBrandColors,
        )

    private fun assertContrast(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label was $ratio, needs $minimum", ratio >= minimum)
    }

    @Test
    fun `hero text reaches AAA on the hero in both themes`() {
        themes.forEach { (name, brand) ->
            assertContrast("$name onHero", brand.onHero, brand.hero, 7.0)
            assertContrast("$name onHeroVariant", brand.onHeroVariant, brand.hero, 4.5)
        }
    }

    @Test
    fun `lumen is visible on the hero and carries AAA text`() {
        themes.forEach { (name, brand) ->
            assertContrast("$name lumen on hero", brand.lumen, brand.hero, 4.5)
            assertContrast("$name onLumen on lumen", brand.onLumen, brand.lumen, 7.0)
        }
    }

    @Test
    fun `lumen keeps the same value in both themes`() {
        assertEquals(LightBrandColors.lumen, NightFieldBrandColors.lumen)
        assertEquals(Color(0xFFFFC44D), LightBrandColors.lumen)
    }

    @Test
    fun `hero shapes are distinct from the hero so the art stays visible`() {
        themes.forEach { (name, brand) ->
            assertNotEquals("$name shapeA", brand.hero, brand.heroShapeA)
            assertNotEquals("$name shapeB", brand.hero, brand.heroShapeB)
            assertNotEquals("$name shapes", brand.heroShapeA, brand.heroShapeB)
        }
    }

    @Test
    fun `light hero keeps the approved Navy700 brand colour`() {
        assertEquals(Navy700, LightBrandColors.hero)
    }
}
```

- [ ] **Step 2: Chạy để thấy fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.theme.BrandColorsTest"`
Expected: FAIL, lỗi biên dịch `Unresolved reference: LightBrandColors`.

- [ ] **Step 3: Tạo `BrandColors.kt`**

```kotlin
package com.luxmap.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LuxBrandColors(
    val hero: Color,
    val onHero: Color,
    val onHeroVariant: Color,
    val heroShapeA: Color,
    val heroShapeB: Color,
    val lumen: Color,
    val onLumen: Color,
)

private val Lumen = Color(0xFFFFC44D)

internal val LightBrandColors =
    LuxBrandColors(
        hero = Navy700,
        onHero = White,
        onHeroVariant = Navy100,
        heroShapeA = Color(0xFF2A4A7F),
        heroShapeB = Color(0xFF33558F),
        lumen = Lumen,
        onLumen = Navy950,
    )

internal val NightFieldBrandColors =
    LuxBrandColors(
        hero = Navy950,
        onHero = White,
        onHeroVariant = Navy200,
        heroShapeA = Navy750,
        heroShapeB = Color(0xFF2A4570),
        lumen = Lumen,
        onLumen = Navy950,
    )

val LocalLuxBrandColors = staticCompositionLocalOf { LightBrandColors }
```

- [ ] **Step 4: Thêm `LuxTheme.brand`**

Trong `ExtendedColors.kt`, thêm vào object `LuxTheme` sau `val status`:

```kotlin
    val brand: LuxBrandColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLuxBrandColors.current
```

- [ ] **Step 5: Cung cấp trong `LuxMapTheme`**

Trong `Theme.kt`, thêm dòng thứ ba vào `CompositionLocalProvider(...)`:

```kotlin
        LocalLuxBrandColors provides if (useDarkTheme) NightFieldBrandColors else LightBrandColors,
```

- [ ] **Step 6: Chạy test, phải pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.theme.BrandColorsTest"`
Expected: PASS (5 tests). Nếu `lumen on hero` hay `onHero` fail, **dừng lại báo chủ dự án** trước khi đổi giá trị đã duyệt trong spec.

- [ ] **Step 7: ktlint và commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/core/theme/BrandColors.kt app/src/main/java/com/luxmap/core/theme/ExtendedColors.kt app/src/main/java/com/luxmap/core/theme/Theme.kt app/src/test/java/com/luxmap/core/theme/BrandColorsTest.kt
git commit -m "feat(brand): add Lumen and hero brand colour tokens"
```

---

### Task 2: `LuxHero`, `LuxTabHero`, `LuxStatTile` và icon thanh trạng thái

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/StatusBarIcons.kt`
- Create: `app/src/main/java/com/luxmap/core/ui/components/LuxHero.kt`
- Modify: `app/src/main/java/com/luxmap/core/ui/components/LuxTopBars.kt:120` (`private fun NotificationAction` → `internal fun NotificationAction`)
- Test: `app/src/test/java/com/luxmap/core/ui/components/StatusBarIconsTest.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/ui/components/LuxHeroTest.kt`

**Interfaces:**
- Consumes: `LuxTheme.brand` (Task 1); `NotificationAction(unreadCount: Int, onClick: () -> Unit)` trong `LuxTopBars.kt`.
- Produces:
  - `internal class StatusBarIconsCounter(apply: (lightIcons: Boolean) -> Unit)` với `acquire()` / `release()`.
  - `@Composable internal fun LightStatusBarIcons()`.
  - `@Composable fun LuxHero(modifier, contentPadding: PaddingValues = PaddingValues(Spacing.lg), verticalSpacing: Dp = Spacing.md, applyStatusBarInset: Boolean = false, content: @Composable ColumnScope.() -> Unit)`.
  - `@Composable fun LuxTabHero(title: String, unreadNotificationCount: Int, onNotificationsClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, content: (@Composable ColumnScope.() -> Unit)? = null)`.
  - `@Composable fun LuxStatTile(label: String, value: Int, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface)`.

- [ ] **Step 1: Viết test thất bại cho bộ đếm icon thanh trạng thái**

```kotlin
package com.luxmap.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarIconsTest {
    private val applied = mutableListOf<Boolean>()
    private val counter = StatusBarIconsCounter { applied += it }

    @Test
    fun `first acquire switches to light icons`() {
        counter.acquire()
        assertEquals(listOf(true), applied)
    }

    @Test
    fun `nested acquire does not apply twice`() {
        counter.acquire()
        counter.acquire()
        assertEquals(listOf(true), applied)
    }

    @Test
    fun `icons stay light until the last owner releases`() {
        counter.acquire()
        counter.acquire()
        counter.release()
        assertEquals(listOf(true), applied)
        counter.release()
        assertEquals(listOf(true, false), applied)
    }

    @Test
    fun `release without acquire is ignored`() {
        counter.release()
        assertEquals(emptyList<Boolean>(), applied)
    }
}
```

- [ ] **Step 2: Chạy để thấy fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.ui.components.StatusBarIconsTest"`
Expected: FAIL, `Unresolved reference: StatusBarIconsCounter`.

- [ ] **Step 3: Tạo `StatusBarIcons.kt`**

`apply(true)` nghĩa là icon thanh trạng thái phải **sáng** (nền hero tối).

```kotlin
package com.luxmap.core.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal class StatusBarIconsCounter(
    private val apply: (lightIcons: Boolean) -> Unit,
) {
    private var owners = 0

    fun acquire() {
        owners += 1
        if (owners == 1) apply(true)
    }

    fun release() {
        if (owners == 0) return
        owners -= 1
        if (owners == 0) apply(false)
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private var sharedCounter: StatusBarIconsCounter? = null
private var restoredLightStatusBars: Boolean = true

@Composable
internal fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val counter =
                sharedCounter
                    ?: StatusBarIconsCounter { light ->
                        if (light) restoredLightStatusBars = controller.isAppearanceLightStatusBars
                        controller.isAppearanceLightStatusBars = if (light) false else restoredLightStatusBars
                    }.also { sharedCounter = it }
            counter.acquire()
            onDispose { counter.release() }
        }
    }
}
```

- [ ] **Step 4: Chạy test, phải pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.ui.components.StatusBarIconsTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Viết test Compose thất bại cho hero**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LuxHeroTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabHeroShowsTitleSubtitleAndContentSlot() {
        composeRule.setContent {
            LuxMapTheme {
                LuxTabHero(
                    title = "Việc hôm nay",
                    subtitle = "Thứ Tư, 25/11/2026",
                    unreadNotificationCount = 0,
                    onNotificationsClick = {},
                ) {
                    LuxStatTile(label = "Tổng", value = 4)
                }
            }
        }
        composeRule.onNodeWithText("Việc hôm nay").assertIsDisplayed()
        composeRule.onNodeWithText("Thứ Tư, 25/11/2026").assertIsDisplayed()
        composeRule.onNodeWithText("Tổng").assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
    }

    @Test
    fun tabHeroBellReportsUnreadCountAndHandlesClick() {
        var clicked = false
        composeRule.setContent {
            LuxMapTheme {
                LuxTabHero(
                    title = "Khảo sát",
                    unreadNotificationCount = 3,
                    onNotificationsClick = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Thông báo, 3 chưa đọc").performClick()
        assertTrue(clicked)
    }

    @Test
    fun statTileMergesLabelAndValueForTalkBack() {
        composeRule.setContent {
            LuxMapTheme { LuxStatTile(label = "Quá hạn", value = 2) }
        }
        composeRule.onNodeWithText("Quá hạn").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
    }
}
```

- [ ] **Step 6: Tạo `LuxHero.kt`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxBrandColors
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing

@Composable
fun LuxHero(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    verticalSpacing: Dp = Spacing.md,
    applyStatusBarInset: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val brand = LuxTheme.brand
    LightStatusBarIcons()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clipToBounds()
                .background(brand.hero)
                .drawBehind { drawHeroArt(brand) }
                .then(if (applyStatusBarInset) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

private fun DrawScope.drawHeroArt(brand: LuxBrandColors) {
    val w = size.width
    val h = size.height
    drawCircle(brand.heroShapeA, radius = h * 0.9f, center = Offset(w + h * 0.1f, -h * 0.1f))
    drawCircle(brand.heroShapeB, radius = h * 0.42f, center = Offset(w - h * 0.2f, h * 0.65f))
    val baseX = w - 84.dp.toPx()
    val armY = 20.dp.toPx()
    val armEnd = baseX + 26.dp.toPx()
    val poleBottom = minOf(h, 96.dp.toPx())
    val pole =
        Path().apply {
            moveTo(baseX, poleBottom)
            lineTo(baseX, armY + 8.dp.toPx())
            quadraticTo(baseX, armY, baseX + 8.dp.toPx(), armY)
            lineTo(armEnd, armY)
        }
    drawPath(pole, brand.onHeroVariant, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    val cone =
        Path().apply {
            moveTo(armEnd - 2.dp.toPx(), armY + 5.dp.toPx())
            lineTo(armEnd - 18.dp.toPx(), armY + 34.dp.toPx())
            lineTo(armEnd + 14.dp.toPx(), armY + 34.dp.toPx())
            close()
        }
    drawPath(cone, brand.lumen.copy(alpha = 0.3f))
    drawRoundRect(
        color = brand.lumen,
        topLeft = Offset(armEnd - 8.dp.toPx(), armY - 3.dp.toPx()),
        size = Size(16.dp.toPx(), 8.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
}

@Composable
fun LuxTabHero(
    title: String,
    unreadNotificationCount: Int,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val brand = LuxTheme.brand
    LuxHero(
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.lg),
        applyStatusBarInset = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(top = Spacing.sm, end = Spacing.xl)) {
                Text(
                    text = "LUXMAP · VẬN HÀNH",
                    style = MaterialTheme.typography.labelMedium,
                    color = brand.onHeroVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = brand.onHero,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = brand.onHeroVariant,
                    )
                }
            }
            CompositionLocalProvider(LocalContentColor provides brand.onHero) {
                NotificationAction(unreadCount = unreadNotificationCount, onClick = onNotificationsClick)
            }
        }
        if (content != null) {
            Column(
                modifier = Modifier.padding(end = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}

@Composable
fun LuxStatTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .semantics(mergeDescendants = true) {},
        ) {
            Text(text = "$value", style = MaterialTheme.typography.titleLarge, color = valueColor)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 7: Đổi `NotificationAction` thành `internal`**

Trong `LuxTopBars.kt` dòng 120–121, đổi `private fun NotificationAction(` thành `internal fun NotificationAction(`.

- [ ] **Step 8: Biên dịch và chạy test trên máy thật**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run (máy thật đã cắm): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.core.ui.components.LuxHeroTest`
Expected: PASS (3 tests). Nếu thấy `NoSuchMethodException ... InputManager.getInstance` thì đang chạy nhầm emulator, chuyển sang máy thật SM-A075F.

- [ ] **Step 9: ktlint và commit** (nếu diff > 400 dòng thì tách `StatusBarIcons` thành commit riêng)

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/core/ui/components/StatusBarIcons.kt app/src/test/java/com/luxmap/core/ui/components/StatusBarIconsTest.kt
git commit -m "feat(brand): add status bar icon owner counter"
git add app/src/main/java/com/luxmap/core/ui/components/LuxHero.kt app/src/main/java/com/luxmap/core/ui/components/LuxTopBars.kt app/src/androidTest/java/com/luxmap/core/ui/components/LuxHeroTest.kt
git commit -m "feat(brand): add LuxHero, LuxTabHero and LuxStatTile"
```

---

### Task 3: Minh hoạ cột đèn cho trạng thái rỗng/lỗi

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/LuxLampIllustration.kt`
- Modify: `app/src/main/java/com/luxmap/core/ui/components/LuxDataStates.kt:39-40` và `StateLayout` (dòng 96-123)
- Test: `app/src/androidTest/java/com/luxmap/core/ui/components/LuxDataStatesTest.kt` (chạy lại, không sửa)

**Interfaces:**
- Consumes: `LuxTheme.brand.lumen` (Task 1).
- Produces: `@Composable fun LuxLampIllustration(modifier: Modifier = Modifier, size: Dp = 96.dp)`.

- [ ] **Step 1: Tạo `LuxLampIllustration.kt`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxTheme

@Composable
fun LuxLampIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val lumen = LuxTheme.brand.lumen
    val ground = MaterialTheme.colorScheme.primaryContainer
    val pole = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        val s = this.size.minDimension
        drawCircle(ground, radius = s * 0.5f)
        val baseX = s * 0.40f
        val armY = s * 0.26f
        val armEnd = s * 0.64f
        val path =
            Path().apply {
                moveTo(baseX, s * 0.82f)
                lineTo(baseX, armY + s * 0.06f)
                quadraticTo(baseX, armY, baseX + s * 0.06f, armY)
                lineTo(armEnd, armY)
            }
        drawPath(path, pole, style = Stroke(width = s * 0.035f, cap = StrokeCap.Round))
        val cone =
            Path().apply {
                moveTo(armEnd - s * 0.02f, armY + s * 0.05f)
                lineTo(s * 0.50f, s * 0.72f)
                lineTo(s * 0.78f, s * 0.72f)
                close()
            }
        drawPath(cone, lumen.copy(alpha = 0.35f))
        drawRoundRect(
            color = lumen,
            topLeft = Offset(armEnd - s * 0.08f, armY - s * 0.03f),
            size = Size(s * 0.18f, s * 0.09f),
            cornerRadius = CornerRadius(s * 0.04f),
        )
    }
}
```

- [ ] **Step 2: Dùng minh hoạ trong `StateLayout`**

Trong `LuxDataStates.kt`, thay hằng số:

```kotlin
private val StateIconBoxSize = 64.dp
private val StateIconSize = 32.dp
```

bằng:

```kotlin
private val StateIllustrationSize = 112.dp
private val StateBadgeSize = 40.dp
private val StateBadgeIconSize = 22.dp
```

và thay khối `Box(... size(StateIconBoxSize) ...) { Icon(...) }` trong `StateLayout` bằng:

```kotlin
        Box(
            modifier = Modifier.size(StateIllustrationSize),
            contentAlignment = Alignment.BottomEnd,
        ) {
            LuxLampIllustration(size = StateIllustrationSize)
            Box(
                modifier =
                    Modifier
                        .size(StateBadgeSize)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(StateBadgeIconSize),
                )
            }
        }
```

Thêm import `androidx.compose.foundation.border` và `androidx.compose.foundation.shape.CircleShape`. Giữ nguyên toàn bộ phần chữ, `heading()`, `liveRegion` và nút bên dưới.

- [ ] **Step 3: Biên dịch và chạy test hiện có trên máy thật**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.core.ui.components.LuxDataStatesTest`
Expected: PASS (không đổi hành vi chữ và nút).

- [ ] **Step 4: ktlint và commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/core/ui/components/LuxLampIllustration.kt app/src/main/java/com/luxmap/core/ui/components/LuxDataStates.kt
git commit -m "feat(brand): illustrate empty and error states"
```

---

### Task 4: Thanh tiêu đề màn con màu navy

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/ui/components/LuxTopBars.kt:37-46`, `:73-93`, `:95-118`
- Test: `app/src/androidTest/java/com/luxmap/core/ui/components/LuxTopBarsTest.kt` (chạy lại)

**Interfaces:**
- Consumes: `LuxTheme.brand` (Task 1), `LightStatusBarIcons()` (Task 2).

- [ ] **Step 1: Đổi màu `luxTopBarColors`**

Thay hàm `luxTopBarColors()` bằng:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun luxTopBarColors(): TopAppBarColors {
    val brand = LuxTheme.brand
    return TopAppBarDefaults.topAppBarColors(
        containerColor = brand.hero,
        scrolledContainerColor = brand.hero,
        titleContentColor = brand.onHero,
        navigationIconContentColor = brand.onHero,
        actionIconContentColor = brand.onHero,
    )
}
```

Thêm import `com.luxmap.core.theme.LuxTheme`.

- [ ] **Step 2: Thêm tham số màu phụ đề cho `TitleBlock`**

Đổi chữ ký:

```kotlin
@Composable
private fun TitleBlock(
    title: String,
    subtitle: String?,
    isTabRoot: Boolean,
    subtitleColor: Color,
    modifier: Modifier = Modifier,
)
```

Trong thân, dòng `color = MaterialTheme.colorScheme.onSurfaceVariant,` của phụ đề đổi thành `color = subtitleColor,`. Thêm import `androidx.compose.ui.graphics.Color`.

Cập nhật hai chỗ gọi:
- `LuxTabTopBar`: `TitleBlock(title = title, subtitle = subtitle, isTabRoot = true, subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))`
- `LuxChildTopBar`: `TitleBlock(title = title, subtitle = subtitle, isTabRoot = false, subtitleColor = LuxTheme.brand.onHeroVariant)`

- [ ] **Step 3: Icon thanh trạng thái sáng trong thanh con**

Trong thân `LuxChildTopBar`, thêm dòng đầu tiên: `LightStatusBarIcons()`.

- [ ] **Step 4: Biên dịch và chạy test hiện có**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.core.ui.components.LuxTopBarsTest`
Expected: PASS. Nếu test nào so màu cũ (`surface`) thì cập nhật kỳ vọng sang `LuxTheme.brand.hero`, không đổi hành vi khác.

- [ ] **Step 5: ktlint và commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/core/ui/components/LuxTopBars.kt
git commit -m "feat(brand): paint child top bar with the navy hero colour"
```

---

### Task 5: Màn Đăng nhập (F01)

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/auth/ui/LoginScreen.kt:167-195` (khối header), import
- Test: `app/src/androidTest/java/com/luxmap/feature/auth/ui/LoginScreenTest.kt` (chạy lại nếu có; tên file kiểm tra bằng Glob)

**Interfaces:**
- Consumes: `LuxHero` (Task 2).

- [ ] **Step 1: Thay khối header bằng `LuxHero`**

Thay:

```kotlin
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(LoginColors.headerContainer)
                    .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
```

bằng:

```kotlin
        LuxHero {
```

Giữ nguyên toàn bộ nội dung bên trong (hàng `LogoMark` + `LUXMAP`, `NetworkStatus`) và dấu `}` đóng. Thêm `import com.luxmap.core.ui.components.LuxHero`. Giữ `LoginColors.onHeader` cho chữ, `LoginColors.headerContainer` không còn dùng ở file này nhưng object `LoginColors` giữ nguyên (vẫn có `LoginColorsTest`).

- [ ] **Step 2: Biên dịch, dọn import thừa**

Run: `./gradlew :app:compileDebugKotlin ktlintFormat`
Expected: BUILD SUCCESSFUL; `ktlintFormat` gỡ import không còn dùng.

- [ ] **Step 3: Chạy test Đăng nhập hiện có và kiểm tra tay trên máy thật**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.auth.ui.LoginScreenTest`
Expected: PASS. Kiểm tra tay: header navy có hình học và cột đèn, chữ đọc rõ, nút Google và form không đổi, Night Field đọc được.

- [ ] **Step 4: ktlint và commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/auth/ui/LoginScreen.kt
git commit -m "feat(brand): add lamp hero to the login header"
```

---

### Task 6: Màn Việc hôm nay (F02)

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt` (`HomeScreen` dòng 142-153, `HomeContent` dòng 186-200, `SummaryStatsRow`/`SummaryStatCell`/`SummaryStatDivider` từ dòng 233)
- Test: `app/src/androidTest/java/com/luxmap/feature/home/ui/HomeScreenTest.kt`, `HomeLargeFontTest.kt` (chạy lại)

**Interfaces:**
- Consumes: `LuxTabHero`, `LuxStatTile` (Task 2); `HomeSummary` với trường `total`, `overdue`, `inProgress` đang dùng trong file.

- [ ] **Step 1: Hoist `summary` lên `HomeScreen` và thay `topBar`**

Trong `HomeScreen`, ngay sau `val resolvedTab = ...`, thêm:

```kotlin
    val summary =
        remember(uiState, today) {
            (uiState as? HomeUiState.Success)?.data?.visibleTasks()?.homeSummary(today)
        }
```

Thay khối `topBar = { LuxTabTopBar(...) },` bằng:

```kotlin
        topBar = {
            LuxTabHero(
                title = "Việc hôm nay",
                subtitle = homeGreeting(username, today, fullName),
                unreadNotificationCount = unreadNotificationCount,
                onNotificationsClick = onOpenNotifications,
            ) {
                if (summary != null) HomeSummaryTiles(summary = summary)
            }
        },
```

- [ ] **Step 2: Thay `SummaryStatsRow` bằng `HomeSummaryTiles`**

Xoá `SummaryStatsRow`, `SummaryStatCell`, `SummaryStatDivider` và thêm:

```kotlin
@Composable
private fun HomeSummaryTiles(summary: HomeSummary) {
    val overdueColor =
        if (summary.overdue > 0) LuxTheme.colors.dueOverdue else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LuxStatTile(label = "Tổng", value = summary.total, modifier = Modifier.weight(1f))
        LuxStatTile(
            label = "Quá hạn",
            value = summary.overdue,
            modifier = Modifier.weight(1f),
            valueColor = overdueColor,
        )
        LuxStatTile(label = "Đang làm", value = summary.inProgress, modifier = Modifier.weight(1f))
    }
}
```

- [ ] **Step 3: Bỏ phần số liệu cũ trong `HomeContent`**

Trong `HomeContent`, xoá dòng `val summary = remember(tasks, today) { tasks.homeSummary(today) }` và dòng `SummaryStatsRow(summary = summary)`. Giữ `LuxSegmented` và phần còn lại. Thêm import `com.luxmap.core.ui.components.LuxTabHero`, `LuxStatTile`; chạy `ktlintFormat` để gỡ import thừa.

- [ ] **Step 4: Biên dịch và chạy test**

Run: `./gradlew :app:compileDebugKotlin ktlintFormat`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.home.ui.HomeTaskListTest"`
Expected: PASS.

Run (máy thật): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.home.ui.HomeScreenTest`
và `... .class=com.luxmap.feature.home.ui.HomeLargeFontTest`
Expected: PASS. Nếu test cũ tìm `Việc hôm nay` mà ra hai node (chữ trên hero và chỗ khác) thì chỉ chỉnh test sang `onAllNodesWithText(...)[0]`, không đổi UI.

- [ ] **Step 5: Kiểm tra tay trên máy thật**

Cuộn danh sách ở bốn trạng thái (đang tải, có dữ liệu, rỗng, lỗi), font scale 1,3 và Night Field. Điều kiện chấp nhận: thấy ít nhất 1,5 thẻ việc mà không cần cuộn khi có dữ liệu; chữ trên hero không bị cột đèn che; icon thanh trạng thái là icon sáng.

- [ ] **Step 6: ktlint và commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt app/src/androidTest/java/com/luxmap/feature/home/ui
git commit -m "feat(brand): use lamp hero and stat tiles on Home"
```

---

### Task 7: Hub Khảo sát

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt:124` (chỗ gọi `LuxTabTopBar`)
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreenTest.kt` (chạy lại)

**Interfaces:**
- Consumes: `LuxTabHero` (Task 2). `LuxTabHero` có cùng tên tham số `title`, `subtitle`, `unreadNotificationCount`, `onNotificationsClick` như `LuxTabTopBar`.

- [ ] **Step 1: Đổi lời gọi**

Mở dòng 124, đổi `LuxTabTopBar(` thành `LuxTabHero(`, giữ nguyên các đối số hiện có. Đổi import `com.luxmap.core.ui.components.LuxTabTopBar` thành `com.luxmap.core.ui.components.LuxTabHero`.

- [ ] **Step 2: Biên dịch, chạy test, kiểm tra tay**

Run: `./gradlew :app:compileDebugKotlin ktlintFormat`
Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.survey.ui.hub.SurveyHubScreenTest`
Expected: PASS. Kiểm tra tay bốn trạng thái và font scale 1,3.

- [ ] **Step 3: ktlint và commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt
git commit -m "feat(brand): use lamp hero on the survey hub"
```

---

### Task 8: Màn Cá nhân

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/profile/ui/ProfileScreen.kt:108` (chỗ gọi `LuxTabTopBar`)
- Test: test màn Cá nhân hiện có (tìm bằng `Glob app/src/androidTest/**/Profile*Test.kt`)

**Interfaces:**
- Consumes: `LuxTabHero` (Task 2).

- [ ] **Step 1: Đổi lời gọi**

Giống Task 7: tại dòng 108 đổi `LuxTabTopBar(` thành `LuxTabHero(`, giữ đối số, đổi import.

- [ ] **Step 2: Biên dịch, chạy test, kiểm tra tay**

Run: `./gradlew :app:compileDebugKotlin ktlintFormat`
Chạy các test Profile tìm được bằng Glob (chỉ các class đó).
Expected: PASS. Kiểm tra tay: thẻ thông tin người dùng, mục Đồng bộ và Đăng xuất không bị đẩy ra ngoài tầm với.

- [ ] **Step 3: ktlint và commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/profile/ui/ProfileScreen.kt
git commit -m "feat(brand): use lamp hero on the profile tab"
```

---

### Task 9: Cập nhật tài liệu Design System

**Files:**
- Modify: `docs/design-system/vNext/README.md` (mục 4, cuối danh sách "Duyệt bổ sung")
- Modify: `docs/design-system/vNext/COLOR_SYSTEM.md` (cuối file)
- Modify: `docs/design-system/vNext/COMPONENT_SPECIFICATIONS.md` (cuối file)
- Modify: `CLAUDE.md` (mục "Design tokens", ngay trước "Quy tắc dùng token trong code")

- [ ] **Step 1: README vNext**

Thêm sau mục "Duyệt bổ sung cho F01":

```markdown
**Duyệt bổ sung (2026-11-25, sau buổi chấm đồ án):**

18. **Brand visual refresh "Cột đèn" (A1):** toàn bộ app. Quyết định #5, #7, #8 và T-07 **giữ nguyên** (không gradient, không Expressive, không dynamic color). Thêm màu nhấn **Lumen `#FFC44D`** (chỉ trang trí thương hiệu) và 3 component `LuxHero`/`LuxTabHero`, `LuxStatTile`, `LuxLampIllustration`. Hero thay thanh tiêu đề trắng ở 3 tab gốc, thanh tiêu đề màn con chuyển sang navy. Bản đồ F12 chưa đổi vì T-09, T-10 chưa duyệt. Spec: `docs/superpowers/specs/2026-11-25-brand-visual-refresh-design.md`.
```

- [ ] **Step 2: COLOR_SYSTEM.md**

Thêm cuối file:

```markdown
## 🆕 Màu thương hiệu (2026-11-25)

Truy cập qua `LuxTheme.brand`. Chỉ là trang trí thương hiệu, không mang ngữ nghĩa trạng thái.

| Token | Light | Night Field | Vai trò |
|---|---:|---:|---|
| `hero` | `#1F3864` | `#0E1E38` | Nền hero và thanh tiêu đề màn con |
| `onHero` | `#FFFFFF` | `#FFFFFF` | Chữ chính trên hero |
| `onHeroVariant` | `#D9E2EF` | `#A9BCDD` | Chữ phụ, nét cột đèn |
| `heroShapeA` / `heroShapeB` | `#2A4A7F` / `#33558F` | `#21375C` / `#2A4570` | Hình tròn hoạ tiết |
| `lumen` | `#FFC44D` | `#FFC44D` | Điểm nhấn: đầu đèn, tia sáng |
| `onLumen` | `#0E1E38` | `#0E1E38` | Chữ/icon trên nền Lumen |

Quy tắc: Lumen không làm chữ hoặc icon đơn lẻ trên nền sáng (tương phản ≈ 1,6:1). Tương phản được khoá bởi `BrandColorsTest`.
```

- [ ] **Step 3: COMPONENT_SPECIFICATIONS.md**

Thêm cuối file ba mục ngắn theo mẫu của file (mục đích, khi dùng, anatomy, màu, states, a11y, anti-pattern) cho `LuxTabHero`/`LuxHero`, `LuxStatTile`, `LuxLampIllustration`. Nội dung tối thiểu cho mỗi mục: mục đích một câu, tham số công khai lấy từ Task 2/3, màu chỉ qua `LuxTheme.brand` và `MaterialTheme.colorScheme`, a11y (tiêu đề có `heading()`, ô số liệu gộp semantics, minh hoạ `clearAndSetSemantics`), anti-pattern (không dùng Lumen làm chữ, không đặt hero ở màn camera và nộp báo cáo, không thêm gradient).

- [ ] **Step 4: CLAUDE.md**

Thêm vào mục "Design tokens", trước "### Quy tắc dùng token trong code":

```markdown
**Cập nhật 2026-11-25 — Brand visual refresh "Cột đèn":** thêm `LuxTheme.brand` (hero navy, `lumen` `#FFC44D`, hình hoạ tiết). Lumen **chỉ là trang trí thương hiệu**, không làm chữ trên nền sáng và không thay màu trạng thái (quá hạn/cảnh báo giữ `LuxTheme.colors.*`). Tab gốc dùng `LuxTabHero`, màn con dùng `LuxChildTopBar` (navy), trạng thái rỗng/lỗi có `LuxLampIllustration`. Vẫn không gradient. Bản đồ chưa đổi vì T-09/T-10 chưa duyệt.
```

- [ ] **Step 5: Commit**

```bash
git add docs/design-system/vNext/README.md docs/design-system/vNext/COLOR_SYSTEM.md docs/design-system/vNext/COMPONENT_SPECIFICATIONS.md CLAUDE.md
git commit -m "docs(brand): record brand visual refresh in the design system"
```

---

### Task 10: Kiểm tra cuối trên máy thật

**Files:** không sửa code (chỉ sửa lỗi nhỏ nếu phát hiện, mỗi lỗi một commit riêng).

- [ ] **Step 1: ktlint toàn dự án**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Chạy riêng các test liên quan (không chạy toàn bộ suite)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.theme.*" --tests "com.luxmap.core.ui.components.StatusBarIconsTest" --tests "com.luxmap.feature.home.ui.HomeTaskListTest"`
Expected: PASS.

- [ ] **Step 3: Kiểm tra tay trên SM-A075F**

Đi qua từng màn ở Light và Night Field, font scale mặc định và 1,3:

| Màn | Điều kiện chấp nhận |
|---|---|
| Đăng nhập | Header navy có hoạ tiết, form và nút không đổi vị trí lạ |
| Việc hôm nay | 4 trạng thái đủ; thấy ≥ 1,5 thẻ việc không cần cuộn |
| Khảo sát, Cá nhân | Hero đúng, nội dung bên dưới không bị cắt |
| Thông báo, Chi tiết lệnh, danh sách cột, kết quả ảnh | Thanh tiêu đề navy, nút Quay lại và chữ đọc rõ |
| Màn rỗng/lỗi ở mọi màn | Minh hoạ cột đèn hiện, tiêu đề, nội dung, nút Thử lại còn nguyên |
| Chuyển qua lại giữa tab và màn con | Icon thanh trạng thái luôn sáng khi trên navy, trở lại đúng khi về màn không có hero |
| Camera (chụp ảnh cột, bằng chứng) | Không bị hỏng; chỉ có thanh tiêu đề navy |

- [ ] **Step 4: Báo cáo cho chủ dự án**

Liệt kê: màn nào đã đổi, màn nào cố ý chưa đổi (bản đồ), lỗi phát hiện khi kiểm tra tay và trạng thái. Mở PR vào `dev` (không vào `main`) với tiêu đề `style(brand): add lamp hero and Lumen accent across the app`, nội dung PR bằng tiếng Anh, không thêm dòng ghi công Claude.
