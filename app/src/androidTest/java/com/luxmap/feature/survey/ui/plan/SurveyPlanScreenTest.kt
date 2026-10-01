package com.luxmap.feature.survey.ui.plan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.SurveySweepStatus
import org.junit.Rule
import org.junit.Test

class SurveyPlanScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun route() =
        AssignedSurveyRoute(
            surveySweepId = "SWEEP-1",
            assignedByName = "Nguyen Van A",
            plannedDate = "2026-10-02",
            status = SurveySweepStatus.PLANNED,
            roadSegments = emptyList(),
        )

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme { SurveyPlanContent(state = SurveyPlanUiState.Loading, onRouteSelected = {}) }
        }
        composeRule.onNodeWithTag("survey_plan_loading").assertExists()
    }

    @Test
    fun success_state_shows_the_route_name() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(routes = listOf(route())),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("SWEEP-1").assertExists()
    }

    @Test
    fun empty_state_shows_the_no_routes_message() {
        composeRule.setContent {
            LuxMapTheme { SurveyPlanContent(state = SurveyPlanUiState.Empty, onRouteSelected = {}) }
        }
        // substring = true because the real Empty state text adds a contact hint after this part
        // (see SurveyPlanContent) - onNodeWithText needs an exact match otherwise.
        composeRule.onNodeWithText("Chưa có tuyến nào được phân công", substring = true).assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_a_retry_action() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Error("Không tải được tuyến khảo sát"),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được tuyến khảo sát").assertExists()
        composeRule.onNodeWithText("Thử lại").assertExists()
    }
}
