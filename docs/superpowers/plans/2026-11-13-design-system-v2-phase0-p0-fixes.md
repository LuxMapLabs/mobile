# Design System v2.0 — Giai đoạn 0: Sửa lỗi P0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sửa ba lỗi hiển thị đang chạy thật trên máy người dùng — thanh điều hướng đổ về màu tím mặc định Material, chấm mức ưu tiên "Cao" không đạt ngưỡng tương phản, và chữ phụ sát ngưỡng AA — kèm lưới an toàn chặn hồi quy.

**Architecture:** Ba thay đổi độc lập, tất cả nằm trong `core/theme` và hai nơi gọi. Không đụng nghiệp vụ, API, Room, sync queue, camera/GPS. Mỗi task tự khép kín, có test riêng, commit riêng. Lỗi tím sống được một tháng vì không có test nào canh — task 1 dựng luôn hạ tầng đo tương phản để hai task sau dùng lại.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit 4 (unit test thuần JVM, không cần Robolectric vì `Color` và `ColorScheme` là kiểu Kotlin thuần), ktlint.

**Spec:** `docs/superpowers/specs/2026-11-13-design-system-v2-civic-grade-design.md`

## Global Constraints

- **Nhánh:** tạo từ `dev`, đặt tên `fix/design-system-p0-contrast`. Tuyệt đối không commit vào `main`.
- **Commit:** tiếng Anh, format `<type>(scope): description`, scope dùng `theme`. Mỗi commit ≤ 400 dòng thay đổi.
- **Attribution:** repo đã tắt sẵn qua `.claude/settings.json` — không thêm dòng attribution nào.
- **Không viết comment mới trong code.** Đây là yêu cầu thường trực của chủ dự án.
- **Nhãn tiếng Việt giữ nguyên tuyệt đối:** `Thấp` · `Bình thường` · `Cao` · `Khẩn`. Không đổi chữ.
- **Ngưỡng tương phản:** chữ ≥ 4.5:1 (WCAG 1.4.3 AA). Phần tử phi văn bản ≥ 3:1 (WCAG 1.4.11).
- **Chạy `./gradlew ktlintCheck`** trước mỗi commit và sửa hết lỗi format.
- **Phạm vi Giai đoạn 0 là sửa lỗi, không phải redesign.** Chỉ thêm đúng các token cần cho ba lỗi này. Ramp màu đầy đủ, thang elevation, thang chữ thuộc Giai đoạn 1 — không làm ở đây.

## Review Focus

Năm điều spec ngụ ý nhưng không task nào tự nhiên kiểm tới; mỗi dòng đã được gắn test vào task sở hữu nó:

1. **Badge ưu tiên ở Dark Mode** — `badgeColors()` cố ý **không nhận tham số theme**, giống hệt `WorkOrderStatus.badgeColors()` đã có: badge giữ nền sáng ở cả hai theme. Đây là hành vi nhất quán với hệ badge hiện tại, không phải thiếu sót. Xử lý badge riêng cho Dark Mode thuộc Giai đoạn 1. → kiểm bằng mắt ở Task 4, Bước 3.
2. **Giá trị `severity` lạ từ backend** — `severityFromWire` fallback về `NORMAL`; badge phải vẫn render được màu hợp lệ, không ném lỗi. → test ở Task 2, Bước 1.
3. **Viên chỉ báo tab ở Dark Mode** — lỗi tím được phát hiện ở Light; Dark cũng tham chiếu cùng role và cũng hỏng. → test ở Task 1, Bước 4.
4. **Chữ phụ trên cả hai loại nền** — `text.secondary` xuất hiện trên `background` (`#F8FAFC`) *và* `surface` (`#FFFFFF`); nền xám là trường hợp xấu hơn. → test ở Task 3, Bước 2.
5. **Hồi quy role M3 trong tương lai** — bất kỳ role nào code gọi mà scheme không khai báo sẽ âm thầm đổ về bảng màu Material. Test pin từng role cụ thể là chưa đủ: nó chỉ canh hai role đã biết. → test quét toàn bộ scheme ở Task 1, Bước 4 (`no role in either scheme is a Material baseline purple`).

---

## File Structure

| File | Trách nhiệm | Thao tác |
|---|---|---|
| `app/src/test/java/com/luxmap/core/theme/ColorContrast.kt` | Hàm đo tỉ lệ tương phản WCAG dùng chung cho test | Tạo |
| `app/src/test/java/com/luxmap/core/theme/LuxMapColorSchemeTest.kt` | Chặn hồi quy role M3 thiếu | Tạo |
| `app/src/main/java/com/luxmap/core/theme/Theme.kt` | Khai báo đủ role M3 | Sửa |
| `app/src/main/java/com/luxmap/core/theme/Color.kt` | Token mới + `WorkOrderPriority.badgeColors()` | Sửa |
| `app/src/test/java/com/luxmap/core/theme/WorkOrderPriorityBadgeTest.kt` | Kiểm tương phản badge ưu tiên | Tạo |
| `app/src/test/java/com/luxmap/core/theme/SemanticTextContrastTest.kt` | Kiểm tương phản chữ phụ | Tạo |
| `app/src/main/java/com/luxmap/core/ui/components/StatusBadge.kt` | Xoá `PriorityDot` chết | Sửa |
| `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt` | `PriorityDotLabel` dùng `StatusBadge` | Sửa |

---

### Task 1: Khai báo đủ role M3 và dựng lưới chặn hồi quy

Nguyên nhân gốc: commit `2187ba1` thêm `primaryContainer`/`onPrimaryContainer` vào scheme, commit rollback `530f772` xoá khỏi `Theme.kt` nhưng không dọn 4 nơi gọi. Hai role này rơi về bảng màu baseline M3 (tím `#EADDFF`), hiển thị ở `BottomNavBar.kt:37-39`, `CoverageScreen.kt:108,116,122`, `SubmitScreen.kt:142,150,156`, `MapScreen.kt:653`.

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Theme.kt:10,29` (đổi `private` → `internal`), `Theme.kt:10-46` (thêm role)
- Modify: `app/src/main/java/com/luxmap/core/theme/Color.kt` (thêm 3 token navy)
- Create: `app/src/test/java/com/luxmap/core/theme/ColorContrast.kt`
- Create: `app/src/test/java/com/luxmap/core/theme/LuxMapColorSchemeTest.kt`

**Interfaces:**
- Produces: `internal val LuxMapLightColorScheme: ColorScheme`, `internal val LuxMapDarkColorScheme: ColorScheme` — Task 3 đọc lại để kiểm `onSurfaceVariant`.
- Produces: `fun contrastRatio(foreground: Color, background: Color): Double` trong source set `test` — Task 2 và Task 3 dùng lại.
- Produces: token `Navy50`, `Navy100`, `Navy800` trong `Color.kt`.

- [ ] **Step 1: Thêm 3 token navy vào `Color.kt`**

Chèn ngay dưới dòng `val Navy700 = Color(0xFF1F3864)`:

```kotlin
val Navy50 = Color(0xFFEEF2F8)
val Navy100 = Color(0xFFD9E2EF)
val Navy800 = Color(0xFF172B4D)
```

- [ ] **Step 2: Mở visibility của hai color scheme**

Trong `Theme.kt`, đổi `private val LuxMapLightColorScheme` thành `internal val LuxMapLightColorScheme`, và `private val LuxMapDarkColorScheme` thành `internal val LuxMapDarkColorScheme`. Không đổi gì khác ở bước này.

- [ ] **Step 3: Viết hàm đo tương phản dùng chung cho test**

Tạo `app/src/test/java/com/luxmap/core/theme/ColorContrast.kt`:

```kotlin
package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

private fun channelLuminance(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

fun relativeLuminance(color: Color): Double =
    0.2126 * channelLuminance(color.red) +
        0.7152 * channelLuminance(color.green) +
        0.0722 * channelLuminance(color.blue)

fun contrastRatio(
    foreground: Color,
    background: Color,
): Double {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}
```

- [ ] **Step 4: Viết test thất bại cho role đang thiếu**

Tạo `app/src/test/java/com/luxmap/core/theme/LuxMapColorSchemeTest.kt`:

```kotlin
package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxMapColorSchemeTest {
    private val materialDefaultPurpleContainer = Color(0xFFEADDFF)

    @Test
    fun `light primaryContainer is the navy token, not the Material default`() {
        assertEquals(Navy50, LuxMapLightColorScheme.primaryContainer)
    }

    @Test
    fun `light onPrimaryContainer is the navy token`() {
        assertEquals(Navy700, LuxMapLightColorScheme.onPrimaryContainer)
    }

    @Test
    fun `light primaryContainer is never the Material baseline purple`() {
        assertTrue(LuxMapLightColorScheme.primaryContainer != materialDefaultPurpleContainer)
    }

    @Test
    fun `dark primaryContainer is the navy token`() {
        assertEquals(Navy800, LuxMapDarkColorScheme.primaryContainer)
    }

    @Test
    fun `dark onPrimaryContainer is the navy token`() {
        assertEquals(Navy100, LuxMapDarkColorScheme.onPrimaryContainer)
    }

    @Test
    fun `selected tab label meets AA against its own container in light`() {
        val ratio =
            contrastRatio(
                LuxMapLightColorScheme.onPrimaryContainer,
                LuxMapLightColorScheme.primaryContainer,
            )
        assertTrue("contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `selected tab label meets AA against its own container in dark`() {
        val ratio =
            contrastRatio(
                LuxMapDarkColorScheme.onPrimaryContainer,
                LuxMapDarkColorScheme.primaryContainer,
            )
        assertTrue("contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `no role the app references is a Material baseline purple`() {
        val baselinePurples =
            setOf(
                Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFF21005D), Color(0xFF4F378B),
                Color(0xFF625B71), Color(0xFFE8DEF8), Color(0xFF1D192B), Color(0xFF7D5260),
                Color(0xFFFFD8E4), Color(0xFF31111D),
            )
        listOf(
            "light" to LuxMapLightColorScheme,
            "dark" to LuxMapDarkColorScheme,
        ).forEach { (name, scheme) ->
            val referenced =
                mapOf(
                    "primary" to scheme.primary,
                    "onPrimary" to scheme.onPrimary,
                    "primaryContainer" to scheme.primaryContainer,
                    "onPrimaryContainer" to scheme.onPrimaryContainer,
                    "secondary" to scheme.secondary,
                    "background" to scheme.background,
                    "surface" to scheme.surface,
                    "onSurface" to scheme.onSurface,
                    "surfaceVariant" to scheme.surfaceVariant,
                    "onSurfaceVariant" to scheme.onSurfaceVariant,
                    "outline" to scheme.outline,
                    "error" to scheme.error,
                )
            referenced.forEach { (role, value) ->
                assertTrue("$name.$role leaked a Material baseline colour", value !in baselinePurples)
            }
        }
    }
}
```

Danh sách `referenced` là đúng những role mà code hiện có gọi tới, đối chiếu bằng:
`grep -rho "colorScheme\.[a-zA-Z]*" app/src/main/java/com/luxmap --include=*.kt | sort -u`

Khi một màn hình bắt đầu dùng role mới, thêm role đó vào map này. Test không tự phát hiện role mới — nó chỉ canh những role đã liệt kê. Cách canh triệt để (khai báo đủ toàn bộ role của `ColorScheme`) thuộc Giai đoạn 1.

- [ ] **Step 5: Chạy test để xác nhận thất bại**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "com.luxmap.core.theme.LuxMapColorSchemeTest"`

Expected: FAIL. Test đầu báo kỳ vọng `Navy50` nhưng nhận được `Color(0xFFEADDFF)` — chính là màu tím baseline Material, chứng minh lỗi có thật.

- [ ] **Step 6: Khai báo hai role trong cả hai scheme**

Trong `Theme.kt`, thêm vào `LuxMapLightColorScheme` ngay sau dòng `onPrimary = ...`:

```kotlin
        primaryContainer = Navy50,
        onPrimaryContainer = Navy700,
```

Và vào `LuxMapDarkColorScheme` ngay sau dòng `onPrimary = ...`:

```kotlin
        primaryContainer = Navy800,
        onPrimaryContainer = Navy100,
```

- [ ] **Step 7: Chạy test để xác nhận đã đạt**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "com.luxmap.core.theme.LuxMapColorSchemeTest"`

Expected: PASS, 8/8. Tương phản Light đạt 10.34:1, Dark đạt 10.79:1.

- [ ] **Step 8: Chạy toàn bộ unit test của `core.theme` để chắc không vỡ chỗ khác**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "com.luxmap.core.theme.*"`

Expected: PASS toàn bộ.

- [ ] **Step 9: ktlint**

Run: `./gradlew ktlintCheck`
Nếu có lỗi format: `./gradlew ktlintFormat` rồi chạy lại `ktlintCheck`.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Theme.kt \
        app/src/main/java/com/luxmap/core/theme/Color.kt \
        app/src/test/java/com/luxmap/core/theme/ColorContrast.kt \
        app/src/test/java/com/luxmap/core/theme/LuxMapColorSchemeTest.kt
git commit -m "fix(theme): restore navy primary container roles dropped in the v2.1 rollback"
```

---

### Task 2: Mức ưu tiên chuyển từ chấm màu sang badge

Hai vấn đề: màu "Cao" `#E9A23B` chỉ đạt 2.17:1 trên nền trắng, dưới cả ngưỡng 3:1 của phần tử phi văn bản; và tài liệu (CLAUDE.md, `Color.kt:212`) mô tả đây là "màu chữ trên nền surface" — nếu ai triển khai đúng như tài liệu thì 3/4 màu không đạt AA. Chuyển sang badge nền+chữ xử lý cả hai.

Lưu ý: `PriorityDot` trong `StatusBadge.kt` hiện **không có nơi nào gọi** (code chết) — xoá luôn trong task này.

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Color.kt` (thêm `WorkOrderPriority.badgeColors()`)
- Modify: `app/src/main/java/com/luxmap/core/ui/components/StatusBadge.kt` (xoá `PriorityDot`)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt:1134-1139`
- Create: `app/src/test/java/com/luxmap/core/theme/WorkOrderPriorityBadgeTest.kt`

**Interfaces:**
- Consumes: `contrastRatio(Color, Color): Double` từ Task 1.
- Produces: `fun WorkOrderPriority.badgeColors(): BadgeColors`.
- Giữ nguyên: `fun WorkOrderPriority.label(): String`, `fun WorkOrderPriority.color(): Color` (chưa xoá — xem Bước 7).

- [ ] **Step 1: Viết test thất bại cho hàm chưa tồn tại**

Tạo `app/src/test/java/com/luxmap/core/theme/WorkOrderPriorityBadgeTest.kt`:

```kotlin
package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkOrderPriorityBadgeTest {
    @Test
    fun `every priority badge meets AA for text`() {
        WorkOrderPriority.entries.forEach { priority ->
            val colors = priority.badgeColors()
            val ratio = contrastRatio(colors.text, colors.background)
            assertTrue("${priority.name} contrast was $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun `the high priority badge is no longer the unreadable amber`() {
        val colors = WorkOrderPriority.HIGH.badgeColors()
        val ratio = contrastRatio(colors.text, colors.background)
        assertTrue("HIGH contrast was $ratio", ratio >= 6.0)
    }

    @Test
    fun `each priority has a distinct background so the four stay tellable apart`() {
        val backgrounds = WorkOrderPriority.entries.map { it.badgeColors().background }
        assertEquals(backgrounds.size, backgrounds.toSet().size)
    }

    @Test
    fun `every priority badge still carries a Vietnamese label`() {
        assertEquals("Thấp", WorkOrderPriority.LOW.label())
        assertEquals("Bình thường", WorkOrderPriority.NORMAL.label())
        assertEquals("Cao", WorkOrderPriority.HIGH.label())
        assertEquals("Khẩn", WorkOrderPriority.URGENT.label())
    }

    @Test
    fun `an unknown wire severity still resolves to a readable badge`() {
        val colors = severityFromWire("something_the_backend_added_later").badgeColors()
        val ratio = contrastRatio(colors.text, colors.background)
        assertTrue("fallback contrast was $ratio", ratio >= 4.5)
        assertTrue(colors.background != Color.Unspecified)
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận thất bại**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "com.luxmap.core.theme.WorkOrderPriorityBadgeTest"`

Expected: FAIL khi biên dịch — `badgeColors()` chưa tồn tại trên `WorkOrderPriority`.

- [ ] **Step 3: Thêm `badgeColors()` vào `Color.kt`**

Chèn ngay dưới `fun WorkOrderPriority.color(): Color { ... }`:

```kotlin
fun WorkOrderPriority.badgeColors(): BadgeColors =
    when (this) {
        WorkOrderPriority.LOW -> BadgeColors(Color(0xFFF1F5F9), Color(0xFF475569))
        WorkOrderPriority.NORMAL -> BadgeColors(Color(0xFFE8EEF7), Color(0xFF1F3864))
        WorkOrderPriority.HIGH -> BadgeColors(Color(0xFFFEF3C7), Color(0xFF92400E))
        WorkOrderPriority.URGENT -> BadgeColors(Color(0xFFFEE2E2), Color(0xFF991B1B))
    }
```

- [ ] **Step 4: Chạy test để xác nhận đã đạt**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "com.luxmap.core.theme.WorkOrderPriorityBadgeTest"`

Expected: PASS, 5/5. Tương phản lần lượt 6.91 · 9.96 · 6.37 · 6.80.

- [ ] **Step 5: Đổi nơi gọi sang `StatusBadge`**

Trong `WorkOrderDetailScreen.kt`, thay toàn bộ hàm `PriorityDotLabel` (dòng 1134-1139) bằng:

```kotlin
@Composable
private fun PriorityDotLabel(priority: WorkOrderPriority) {
    StatusBadge(text = priority.label(), colors = priority.badgeColors())
}
```

Thêm import `com.luxmap.core.theme.badgeColors` nếu chưa có. Gỡ các import nay không dùng nữa (`CircleShape`, `Box`, `Modifier.size`, `BadgeTextStyle`) **chỉ khi** không còn chỗ nào khác trong file dùng tới — kiểm bằng cách tìm trong file trước khi xoá.

- [ ] **Step 6: Xoá component chết `PriorityDot`**

Trong `StatusBadge.kt`, xoá toàn bộ hàm `PriorityDot` và comment phía trên nó. Gỡ các import nay thừa: `Box`, `CircleShape`, `border`, `size`, `MaterialTheme`, `Color` — xác nhận từng cái không còn dùng ở `StatusBadge` trước khi xoá.

- [ ] **Step 7: Giữ lại `WorkOrderPriority.color()`**

Không xoá `color()` trong task này. `MapLibreConfig.kt` và `LuminanceTrendChart.kt` dùng màu đơn cho ghim bản đồ và biểu đồ — là phần tử phi văn bản, ngưỡng khác, thuộc phạm vi Giai đoạn 1. Xoá ở đây sẽ làm vỡ build.

Xác minh bằng: `grep -rn "\.color()" app/src/main/java/com/luxmap --include=*.kt`

- [ ] **Step 8: Build và chạy test liên quan**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:testLocalDebugUnitTest --tests "com.luxmap.core.theme.*" --tests "com.luxmap.feature.workorder.*"`
Expected: PASS toàn bộ.

- [ ] **Step 9: ktlint**

Run: `./gradlew ktlintCheck` (dùng `ktlintFormat` nếu cần).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Color.kt \
        app/src/main/java/com/luxmap/core/ui/components/StatusBadge.kt \
        app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt \
        app/src/test/java/com/luxmap/core/theme/WorkOrderPriorityBadgeTest.kt
git commit -m "fix(theme): render work order priority as a badge to clear the contrast floor"
```

---

### Task 3: Nâng tương phản chữ phụ

`Gray500 #64748B` làm `text.secondary` chỉ đạt **4.55:1** trên nền `background #F8FAFC` — vừa đủ AA, không còn dư địa cho ánh sáng thực địa. Đổi sang `#475569` đưa lên **7.24:1** (AAA).

Chỉ đổi vai trò **semantic**, không đổi primitive `Gray500` — 6 nơi gọi `Gray500` trực tiếp (bản đồ, panel camera, chỉ báo GPS, biểu đồ, hint trong `WorkOrderDetailScreen`) là mục đích khác, thuộc Giai đoạn 1.

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Color.kt:104,108` (trong `LightSemanticColors`)
- Create: `app/src/test/java/com/luxmap/core/theme/SemanticTextContrastTest.kt`

**Interfaces:**
- Consumes: `contrastRatio(Color, Color): Double` từ Task 1; `LuxMapLightColorScheme`/`LuxMapDarkColorScheme` từ Task 1.
- Produces: token `Slate600`.

- [ ] **Step 1: Thêm token `Slate600` vào `Color.kt`**

Chèn ngay dưới `val Gray500 = Color(0xFF64748B)`:

```kotlin
val Slate600 = Color(0xFF475569)
```

- [ ] **Step 2: Viết test thất bại**

Tạo `app/src/test/java/com/luxmap/core/theme/SemanticTextContrastTest.kt`:

```kotlin
package com.luxmap.core.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticTextContrastTest {
    @Test
    fun `light secondary text clears AAA on the page background`() {
        val ratio =
            contrastRatio(
                LuxMapLightColorScheme.onSurfaceVariant,
                LuxMapLightColorScheme.background,
            )
        assertTrue("contrast was $ratio", ratio >= 7.0)
    }

    @Test
    fun `light secondary text clears AAA on a card surface`() {
        val ratio =
            contrastRatio(
                LuxMapLightColorScheme.onSurfaceVariant,
                LuxMapLightColorScheme.surface,
            )
        assertTrue("contrast was $ratio", ratio >= 7.0)
    }

    @Test
    fun `dark secondary text clears AAA on the page background`() {
        val ratio =
            contrastRatio(
                LuxMapDarkColorScheme.onSurfaceVariant,
                LuxMapDarkColorScheme.background,
            )
        assertTrue("contrast was $ratio", ratio >= 7.0)
    }

    @Test
    fun `primary text clears AAA in both themes`() {
        val light = contrastRatio(LuxMapLightColorScheme.onSurface, LuxMapLightColorScheme.surface)
        val dark = contrastRatio(LuxMapDarkColorScheme.onSurface, LuxMapDarkColorScheme.surface)
        assertTrue("light was $light", light >= 7.0)
        assertTrue("dark was $dark", dark >= 7.0)
    }
}
```

- [ ] **Step 3: Chạy test để xác nhận thất bại**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "com.luxmap.core.theme.SemanticTextContrastTest"`

Expected: FAIL ở test đầu — tương phản thực tế 4.55, dưới ngưỡng 7.0. Ba test còn lại đạt sẵn.

- [ ] **Step 4: Đổi vai trò semantic sang `Slate600`**

Trong `Color.kt`, bên trong `LightSemanticColors`, đổi:

```kotlin
        textSecondary = Slate600,
```
```kotlin
        navigationInactive = Slate600,
```

Không đụng `DarkSemanticColors` — chữ phụ Dark hiện đạt 7.43:1, đã vượt ngưỡng.

- [ ] **Step 5: Chạy test để xác nhận đã đạt**

Run: `./gradlew :app:testLocalDebugUnitTest --tests "com.luxmap.core.theme.SemanticTextContrastTest"`

Expected: PASS, 4/4. Light nền trang 7.24:1, nền thẻ 7.58:1.

- [ ] **Step 6: Chạy toàn bộ unit test**

Run: `./gradlew :app:testLocalDebugUnitTest`

Expected: PASS toàn bộ. `onSurfaceVariant` có 129 nơi gọi — thay đổi chỉ làm chữ đậm hơn, nhưng cần xác nhận không test nào đang khoá cứng giá trị cũ. Nếu có test nào so sánh trực tiếp `Gray500`, cập nhật test đó theo giá trị mới và ghi rõ lý do trong commit.

- [ ] **Step 7: ktlint**

Run: `./gradlew ktlintCheck` (dùng `ktlintFormat` nếu cần).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/core/theme/Color.kt \
        app/src/test/java/com/luxmap/core/theme/SemanticTextContrastTest.kt
git commit -m "fix(theme): raise secondary text contrast from AA floor to AAA"
```

---

### Task 4: Kiểm tra trên máy thật và mở PR

Ba task trên đều là test JVM thuần. Lỗi tím là lỗi **thị giác** — phải nhìn tận mắt mới chắc.

**Files:** không sửa file nào.

- [ ] **Step 1: Cài lên máy thật**

Run: `./gradlew :app:installDebug`

Nếu `adb devices` trống, dừng lại và báo chủ dự án — không tự kết luận là đã xong.

- [ ] **Step 2: Kiểm bằng mắt, Light Mode**

Mở app và xác nhận từng điểm:
- Viên chỉ báo tab đang chọn ở thanh dưới là **navy nhạt**, không phải tím. Kiểm cả 4 tab.
- Màn Chi tiết lệnh có sự cố: nhãn ưu tiên hiện dạng **badge nền+chữ**, không còn chấm tròn. Nhãn "Cao" đọc rõ.
- Chữ phụ (timestamp, mã cột, dòng metadata) đậm hơn trước, vẫn phân biệt được với chữ chính.
- Màn Độ phủ và màn Nộp đợt khảo sát: khối dùng `primaryContainer` không còn tím.

- [ ] **Step 3: Kiểm bằng mắt, Dark Mode**

Bật Dark Mode ở cài đặt hệ thống, lặp lại Bước 2. Chú ý viên chỉ báo tab và badge ưu tiên.

- [ ] **Step 4: Kiểm font scaling**

Đặt cỡ chữ hệ thống lên mức lớn nhất. Xác nhận badge ưu tiên không bị cắt chữ hay tràn dòng ở màn Chi tiết lệnh. Nhãn dài nhất là "Bình thường".

- [ ] **Step 5: Mở PR vào `dev`**

```bash
git push -u origin fix/design-system-p0-contrast
gh pr create --base dev --title "fix(theme): clear three shipped contrast and color-role defects" --body "$(cat <<'EOF'
## Summary
- Khôi phục `primaryContainer`/`onPrimaryContainer` bị rớt trong đợt rollback v2.1 (`530f772`). Viên chỉ báo tab ở thanh điều hướng đang hiển thị tím baseline Material trên mọi màn hình chính.
- Chuyển nhãn mức ưu tiên Work Order từ chấm màu 7dp sang badge nền+chữ. Màu "Cao" trước đây ở 2.17:1, dưới cả ngưỡng 3:1 cho phần tử phi văn bản.
- Nâng chữ phụ từ 4.55:1 (sát sàn AA) lên 7.24:1 (AAA).
- Xoá component chết `PriorityDot` (0 nơi gọi).
- Thêm test chặn hồi quy đo tương phản thật, để lỗi role M3 thiếu không tái diễn âm thầm.

Spec: `docs/superpowers/specs/2026-11-13-design-system-v2-civic-grade-design.md` (Giai đoạn 0).
Không đụng nghiệp vụ, API, Room, sync queue hay pipeline camera/GPS.

## Test plan
- [ ] `./gradlew :app:testLocalDebugUnitTest` xanh toàn bộ
- [ ] `./gradlew ktlintCheck` sạch
- [ ] Máy thật, Light Mode: viên chỉ báo tab navy ở cả 4 tab
- [ ] Máy thật, Dark Mode: viên chỉ báo tab và badge ưu tiên đúng màu
- [ ] Máy thật: badge ưu tiên không cắt chữ ở cỡ chữ hệ thống lớn nhất
EOF
)"
```

---

## Những gì KHÔNG làm ở Giai đoạn 0

Ghi rõ để tránh phạm vi trôi:

- **Không** dựng ramp màu đầy đủ, thang elevation, thang chữ hợp nhất — Giai đoạn 1.
- **Không** xoá 22 token v2.1 zombie — Giai đoạn 3.
- **Không** đụng 54 mã hex hardcode ngoài `core/theme` — Giai đoạn 3.
- **Không** xoá `WorkOrderPriority.color()` — còn 2 nơi dùng hợp lệ cho bản đồ và biểu đồ.
- **Không** đổi `DarkSemanticColors` sang ramp tối ám navy — Giai đoạn 1.
- **Không** đổi bất kỳ nhãn tiếng Việt, luồng nghiệp vụ hay điều kiện trạng thái nào.
