package com.luxmap.feature.map.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import com.luxmap.feature.map.data.CabinetFeeder
import com.luxmap.feature.map.data.CabinetMarker
import com.luxmap.feature.map.data.CabinetTopology
import com.luxmap.feature.map.data.FeederMode
import com.luxmap.feature.map.data.FeederPower
import com.luxmap.feature.map.data.PoleMarker
import com.luxmap.feature.map.data.RoadSegmentLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MapQuickViewTest {
    @get:Rule val composeRule = createComposeRule()

    private val pole =
        PoleMarker(
            poleId = "POLE-0047",
            segmentId = "SEG-1",
            lat = 10.8,
            lng = 106.7,
            fixtureStatus = AssetCondition.OUT,
            powerSource = "grid",
            hasIotNode = false,
            nearSensitivePoi = true,
        )

    @Test
    fun the_pole_sheet_has_close_navigate_and_detail_actions() {
        var dismissed = 0
        var navigated = 0
        var opened = 0
        composeRule.setContent {
            LuxMapTheme {
                PoleQuickViewBottomSheet(
                    pole = pole,
                    onDismiss = { dismissed++ },
                    onOpenDetail = { opened++ },
                    onNavigate = { navigated++ },
                )
            }
        }
        composeRule.onNodeWithText("POLE-0047").assertExists()
        composeRule.onNodeWithText("Hỏng/Tắt", substring = true).assertExists()
        composeRule.onNode(hasText("Đường đi") and hasClickAction()).assertTouchTargetIsAtLeast(48.dp).performClick()
        composeRule.onNode(hasText("Chi tiết") and hasClickAction()).assertTouchTargetIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithContentDescription("Đóng").assertTouchTargetIsAtLeast(48.dp).performClick()
        assertEquals(listOf(1, 1, 1), listOf(navigated, opened, dismissed))
    }

    @Test
    fun the_segment_sheet_shows_its_facts_and_closes() {
        var dismissed = 0
        composeRule.setContent {
            LuxMapTheme {
                RoadSegmentQuickViewBottomSheet(
                    segment =
                        RoadSegmentLine(
                            segmentId = "SEG-1",
                            name = "Đường liên xã Phước Hiệp",
                            hasActiveSegmentFault = true,
                            lengthM = 1200,
                            poleCount = 30,
                            coordinates = emptyList(),
                        ),
                    visiblePoleCount = 12,
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.onNodeWithText("Đường liên xã Phước Hiệp").assertExists()
        composeRule.onNodeWithText("Tuyến đang có sự cố", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("Đóng").performClick()
        assertEquals(1, dismissed)
    }

    private val cabinet =
        CabinetMarker(
            cabinetId = "CAB-1",
            name = "Tủ chợ Phước Hiệp",
            communeId = "COM-1",
            lat = 10.8,
            lng = 106.7,
            feeders =
                listOf(
                    CabinetFeeder("F-1", 1, FeederMode.AUTO, FeederPower.ON),
                    CabinetFeeder("F-2", 2, FeederMode.OFF, FeederPower.OFF),
                ),
            iotNodeCount = 2,
        )

    private fun showCabinetSheet(
        topology: TopologyState,
        onDismiss: () -> Unit = {},
        onNavigate: () -> Unit = {},
    ) {
        composeRule.setContent {
            LuxMapTheme {
                CabinetQuickViewBottomSheet(
                    selection = CabinetSelection(cabinet, topology),
                    communeName = "Phước Hiệp",
                    onDismiss = onDismiss,
                    onNavigate = onNavigate,
                )
            }
        }
    }

    @Test
    fun the_cabinet_sheet_lists_each_feeder_with_mode_and_power() {
        var dismissed = 0
        var navigated = 0
        showCabinetSheet(
            topology =
                TopologyState.Loaded(
                    CabinetTopology(emptyList(), unlocatedPoleCount = 3, unroutedPoleCount = 0),
                ),
            onDismiss = { dismissed++ },
            onNavigate = { navigated++ },
        )
        listOf(
            "Tủ chợ Phước Hiệp",
            "Lộ F-1",
            "Rơ-le 1",
            "Tự động",
            "Có nguồn",
            "Lộ F-2",
            "Tắt",
            "Mất nguồn",
            "2 thiết bị IoT",
            "3 cột chưa có vị trí",
        ).forEach { composeRule.onNodeWithText(it, substring = true).assertExists() }
        composeRule.onNodeWithText("Phước Hiệp").assertExists()
        composeRule.onNodeWithText("cột chưa gắn tuyến", substring = true).assertDoesNotExist()
        composeRule.onNode(hasText("Đường đi") and hasClickAction()).assertTouchTargetIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithContentDescription("Đóng").performClick()
        assertEquals(listOf(1, 1), listOf(navigated, dismissed))
    }

    @Test
    fun the_cabinet_sheet_shows_loading_while_the_diagram_loads() {
        showCabinetSheet(TopologyState.Loading)
        composeRule.onNodeWithContentDescription("Đang tải sơ đồ cấp nguồn").assertExists()
    }

    @Test
    fun the_cabinet_sheet_shows_the_diagram_error() {
        showCabinetSheet(TopologyState.Error("Có cột chưa gắn tuyến, chưa vẽ được sơ đồ"))
        composeRule.onNodeWithText("Có cột chưa gắn tuyến, chưa vẽ được sơ đồ").assertExists()
    }
}
