package com.luxmap.feature.survey.ui.submit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.feature.survey.data.SessionFileStatus
import org.junit.Rule
import org.junit.Test

class SubmitScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun file_status_list_shows_each_files_label() {
        composeRule.setContent {
            SubmitFileStatusList(
                files =
                    listOf(
                        SessionFileStatus(label = "Video đoạn 1", status = "done"),
                        SessionFileStatus(label = "Log GPS", status = "queued"),
                    ),
            )
        }
        composeRule.onNodeWithText("Video đoạn 1").assertExists()
        composeRule.onNodeWithText("Log GPS").assertExists()
    }
}
