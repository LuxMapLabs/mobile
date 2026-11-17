package com.luxmap.feature.map.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MapChromeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun filter_button_announces_hidden_statuses_and_shows_a_dot() {
        var opened = 0
        composeRule.setContent {
            LuxMapTheme {
                MapSearchBar(query = "", onQueryChange = {}, onFilterClick = { opened++ }, hiddenStatusCount = 2)
            }
        }
        composeRule
            .onNodeWithContentDescription("Lọc và lớp bản đồ. Đang lọc: ẩn 2 trạng thái")
            .assertTouchTargetIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithTag("map_filter_dot", useUnmergedTree = true).assertExists()
    }

    @Test
    fun filter_button_without_filtering_has_no_dot() {
        composeRule.setContent {
            LuxMapTheme { MapSearchBar(query = "", onQueryChange = {}, onFilterClick = {}) }
        }
        composeRule.onNodeWithContentDescription("Lọc và lớp bản đồ").assertExists()
        composeRule.onNodeWithTag("map_filter_dot", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun the_needs_attention_chip_is_a_selectable_filter_with_a_48dp_target() {
        var toggled = 0
        composeRule.setContent {
            LuxMapTheme {
                MapKpiChipRow(
                    poleCount = 120,
                    needsAttentionCount = 7,
                    routeCount = 9,
                    needsAttentionActive = true,
                    onNeedsAttentionClick = { toggled++ },
                )
            }
        }
        composeRule
            .onNode(hasText("cần xử lý", substring = true) and hasClickAction())
            .assertIsSelected()
            .assertTouchTargetIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun the_locate_button_is_56dp_and_labelled() {
        var clicks = 0
        composeRule.setContent {
            LuxMapTheme { LocateMeButton(isLocating = false, onClick = { clicks++ }) }
        }
        composeRule
            .onNodeWithContentDescription("Định vị vị trí hiện tại")
            .assertTouchTargetIsAtLeast(56.dp)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun the_legend_uses_the_web_labels_and_announces_its_state() {
        composeRule.setContent { LuxMapTheme { MapLegend() } }
        composeRule
            .onNode(hasText("Chú giải") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đang đóng"))
            .performClick()
        listOf(
            "Đạt chuẩn",
            "Đèn mờ",
            "Hỏng/Tắt",
            "Chưa quét",
            "Gần trường, cầu",
            "Có thiết bị IoT",
            "Tuyến bình thường",
            "Tuyến đang có sự cố",
        ).forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule
            .onNode(hasText("Chú giải") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đang mở"))
    }

    @Test
    fun layer_rows_are_switches() {
        var fixtures: Boolean? = null
        composeRule.setContent {
            LuxMapTheme {
                MapLayerToggle(
                    showFixtures = true,
                    onShowFixturesChange = { fixtures = it },
                    showRoadSegments = false,
                    onShowRoadSegmentsChange = {},
                )
            }
        }
        composeRule
            .onNode(hasText("Cột đèn") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .performClick()
        assertEquals(false, fixtures)
        composeRule
            .onNode(hasText("Tuyến đường") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
    }

    @Test
    fun status_filters_use_the_web_labels_and_selection() {
        composeRule.setContent {
            LuxMapTheme {
                MapStatusFilterRow(
                    selectedStatuses = setOf(AssetCondition.NORMAL, AssetCondition.OUT),
                    onToggle = {},
                )
            }
        }
        composeRule.onNode(hasText("Đạt chuẩn", substring = true) and hasClickAction()).assertIsSelected()
        composeRule.onNode(hasText("Hỏng/Tắt", substring = true) and hasClickAction()).assertIsSelected()
        composeRule.onNode(hasText("Đèn mờ", substring = true) and hasClickAction()).assertIsNotSelected()
        composeRule.onNode(hasText("Chưa quét", substring = true) and hasClickAction()).assertIsNotSelected()
    }
}
