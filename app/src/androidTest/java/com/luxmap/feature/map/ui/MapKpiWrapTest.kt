package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapKpiWrapTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun kpi_chips_wrap_instead_of_clipping_at_large_font() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(328.dp).testTag("kpi_box")) {
                        MapKpiChipRow(
                            poleCount = 1240,
                            needsAttentionCount = 137,
                            routeCount = 48,
                            needsAttentionActive = true,
                            onNeedsAttentionClick = {},
                        )
                    }
                }
            }
        }
        val box = composeRule.onNodeWithTag("kpi_box").getBoundsInRoot()
        val last = composeRule.onNodeWithText("48 tuyến").getBoundsInRoot()
        assertTrue("last chip right ${last.right} exceeds box ${box.right}", last.right <= box.right)
        val first = composeRule.onNodeWithText("1240 cột").getBoundsInRoot()
        assertTrue(
            "last chip text wrapped: ${last.bottom - last.top} vs ${first.bottom - first.top}",
            (last.bottom - last.top) <= (first.bottom - first.top) * 1.2f,
        )
    }
}
