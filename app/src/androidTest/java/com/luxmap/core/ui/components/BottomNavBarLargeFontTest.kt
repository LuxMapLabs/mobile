package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BottomNavBarLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private val items =
        listOf(
            BottomNavItem("home", "Hôm nay", Icons.Filled.Home),
            BottomNavItem("survey", "Khảo sát", Icons.Filled.PhotoCamera),
            BottomNavItem("map", "Bản đồ", Icons.Filled.Map),
            BottomNavItem("profile", "Cá nhân", Icons.Filled.Person),
        )

    @Test
    fun every_tab_label_is_fully_shown_at_large_font_on_a_narrow_screen() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        BottomNavBar(items = items, currentRoute = "home", onItemSelected = {})
                    }
                }
            }
        }
        items.forEach { item ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule
                .onNodeWithText(item.label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
            assertFalse("${item.label} is cut with an ellipsis", ellipsized)
            assertTrue("${item.label} needs ${layout.lineCount} lines", layout.lineCount <= 2)
        }
    }
}
