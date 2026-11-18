package com.luxmap.feature.workorder.ui.completion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.location.LocationTracker
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class CameraOnlyEvidenceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun inspection_report_only_offers_camera_capture() = verifyCameraOnly("observation")

    @Test
    fun repair_report_only_offers_camera_capture() = verifyCameraOnly("after")

    private fun verifyCameraOnly(kind: String) {
        val viewModel =
            EvidenceCaptureViewModel(
                SavedStateHandle(mapOf("workOrderId" to "WO-CAMERA-TEST", "kind" to kind)),
                mockk<WorkOrderCompletionRepository>(relaxed = true),
                mockk<LocationTracker>(relaxed = true),
            )
        composeRule.setContent {
            LuxMapTheme { EvidenceCaptureRoute(onDone = {}, viewModel = viewModel) }
        }
        composeRule.onNodeWithText("Chụp ảnh").assertIsDisplayed()
        composeRule.onNodeWithText("Chọn từ thư viện").assertDoesNotExist()
        composeRule.onNodeWithText("Xong").assertIsDisplayed()
    }
}
