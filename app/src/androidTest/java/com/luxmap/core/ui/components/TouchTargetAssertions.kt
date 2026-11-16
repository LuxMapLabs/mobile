package com.luxmap.core.ui.components

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertTrue

fun SemanticsNodeInteraction.assertTouchTargetIsAtLeast(minimum: Dp): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    val density = node.layoutInfo.density.density
    val widthDp = node.touchBoundsInRoot.width / density
    val heightDp = node.touchBoundsInRoot.height / density
    assertTrue("touch target was ${widthDp}x$heightDp dp", widthDp >= minimum.value - 0.5f)
    assertTrue("touch target was ${widthDp}x$heightDp dp", heightDp >= minimum.value - 0.5f)
    return this
}
