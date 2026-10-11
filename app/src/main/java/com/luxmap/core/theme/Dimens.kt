package com.luxmap.core.theme

import androidx.compose.ui.unit.dp

// Spacing scale (mục 4.1 Design System v2.0)
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
}

// Radius / touch target (mục 4.2). Nút và mọi vùng chạm tối thiểu 48dp — một tay, có thể đeo găng tay.
object Dimens {
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
    val radiusSheet = 28.dp
    val radiusPill = 999.dp
    val minTouchTarget = 48.dp
}

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
