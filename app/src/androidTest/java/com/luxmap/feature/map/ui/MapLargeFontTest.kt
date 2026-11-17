package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import com.luxmap.feature.map.data.PoleDetail
import com.luxmap.feature.map.data.PoleMarker
import org.junit.Rule
import org.junit.Test

class MapLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun large(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LuxMapTheme { Box(modifier = Modifier.width(360.dp)) { content() } }
            }
        }
    }

    @Test
    fun map_chrome_keeps_its_targets() {
        large {
            Column {
                MapSearchBar(query = "", onQueryChange = {}, onFilterClick = {}, hiddenStatusCount = 1)
                LocateMeButton(isLocating = false, onClick = {})
            }
        }
        composeRule
            .onNodeWithContentDescription("Lọc và lớp bản đồ. Đang lọc: ẩn 1 trạng thái")
            .assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Định vị vị trí hiện tại").assertTouchTargetIsAtLeast(56.dp)
    }

    @Test
    fun pole_quick_view_actions_keep_their_targets() {
        large {
            PoleQuickViewBottomSheet(
                pole =
                    PoleMarker(
                        poleId = "POLE-XA-PHUOC-HIEP-0047",
                        segmentId = null,
                        lat = 10.8,
                        lng = 106.7,
                        fixtureStatus = AssetCondition.DIM,
                        powerSource = "solar",
                        hasIotNode = true,
                        nearSensitivePoi = true,
                    ),
                onDismiss = {},
                onOpenDetail = {},
                onNavigate = {},
            )
        }
        composeRule.onNode(
            hasText("Chi tiết") and hasClickAction(),
        ).assertIsDisplayed().assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNode(hasText("Đường đi") and hasClickAction()).assertTouchTargetIsAtLeast(48.dp)
    }

    @Test
    fun pole_detail_navigate_keeps_its_target() {
        large {
            PoleDetailScreen(
                uiState =
                    PoleDetailUiState.Success(
                        PoleDetail(
                            poleId = "POLE-XA-PHUOC-HIEP-0047",
                            segmentId = null,
                            segmentName = "Đường liên xã Phước Hiệp đoạn qua chợ",
                            lat = 10.8,
                            lng = 106.7,
                            fixtureType = null,
                            powerSource = null,
                            lampWatt = null,
                            installDate = null,
                            warrantyExpiry = null,
                            fixtureStatus = AssetCondition.OUT,
                            statusConfidence = null,
                            determinedAt = "2026-11-10T03:00:00Z",
                            sourceChannel = "cv",
                            hasIotNode = false,
                            iotNodeStatus = null,
                            iotLastReportAt = null,
                            luminanceHistory = emptyList(),
                            runtimeHistory = emptyList(),
                            openFaults = emptyList(),
                            recentFrames = emptyList(),
                            note = null,
                        ),
                    ),
                onBack = {},
            )
        }
        composeRule
            .onNode(hasText("Đường đi") and hasClickAction())
            .performScrollTo()
            .assertTouchTargetIsAtLeast(48.dp)
    }
}
