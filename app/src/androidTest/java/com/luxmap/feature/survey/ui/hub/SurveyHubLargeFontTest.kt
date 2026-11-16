package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SurveyHubLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(state: SurveyHubUiState) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        SurveyHubScreen(state = state)
                    }
                }
            }
        }
    }

    @Test
    fun both_route_buttons_stay_on_one_row_with_48dp_targets_and_the_title_is_visible() {
        show(
            SurveyHubUiState.Success(
                assignedRoutes =
                    listOf(
                        AssignedSurveyRoute(
                            workOrderId = "WO-1",
                            title = "Khảo sát tuyến đường liên xã Phước Hiệp đoạn qua chợ",
                            communeId = "COM-001",
                            woStatus = "assigned",
                            dueDate = null,
                            scheduledDate = LocalDate.now().toString(),
                        ),
                    ),
            ),
        )
        val start = composeRule.onNodeWithText("Bắt đầu").assertTouchTargetIsAtLeast(48.dp).fetchSemanticsNode()
        val cancel = composeRule.onNodeWithText("Yêu cầu hủy").assertTouchTargetIsAtLeast(48.dp).fetchSemanticsNode()
        assertEquals(start.boundsInRoot.top, cancel.boundsInRoot.top, 1f)
        composeRule.onNodeWithText("Khảo sát tuyến đường liên xã Phước Hiệp đoạn qua chợ").assertIsDisplayed()
    }
}
