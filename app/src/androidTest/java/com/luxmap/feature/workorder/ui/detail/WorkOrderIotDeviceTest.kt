package com.luxmap.feature.workorder.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.ui.detail.sections.FaultListCard
import com.luxmap.feature.workorder.ui.detail.sections.InspectionSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File

class WorkOrderIotDeviceTest {
    @get:Rule val composeRule = createComposeRule()

    private val fault =
        WorkOrderFaultDetail(
            faultId = "FAULT-DEVICE-TEST",
            poleId = null,
            lat = 10.97,
            lng = 106.49,
            faultType = "voltage_anomaly",
            faultStatus = "detected",
            severity = "high",
            inspectionOutcome = null,
            cabinetId = "CAB-001",
            originNodeId = "NODE-004-LONG-DEVICE-IDENTIFIER",
            feederId = "FDR-001",
            iotAnomalyCode = "voltage_loss",
        )

    @Test fun inspection_light() = verify(inspection = true, dark = false, fontScale = 1f)

    @Test fun inspection_night_large_text() = verify(inspection = true, dark = true, fontScale = 2f)

    @Test fun repair_light() = verify(inspection = false, dark = false, fontScale = 1f)

    @Test fun repair_night_large_text() = verify(inspection = false, dark = true, fontScale = 2f)

    private fun verify(
        inspection: Boolean,
        dark: Boolean,
        fontScale: Float,
    ) {
        var navigated: Pair<Double, Double>? = null
        var outcome: Pair<String, String>? = null
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                LuxMapTheme(forceDark = dark) {
                    Surface {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (inspection) {
                                InspectionSection(
                                    faults = listOf(fault),
                                    selectedOutcomes = emptyMap(),
                                    reportNote = "",
                                    editable = true,
                                    locked = false,
                                    onNavigateFault = { lat, lng -> navigated = lat to lng },
                                    onFaultOutcomeSelected = { id, value -> outcome = id to value },
                                    onReportNoteChanged = {},
                                )
                            } else {
                                FaultListCard(
                                    title = "Sự cố cần sửa",
                                    faults = listOf(fault),
                                    onNavigateFault = { lat, lng -> navigated = lat to lng },
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText("Điện áp bất thường").assertIsDisplayed()
        val metadata = composeRule.onNodeWithText(faultSupportingText(fault), useUnmergedTree = true)
        metadata.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        metadata.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Metadata must have a text layout", layouts.isEmpty())
        layouts.forEach { assertFalse("Device metadata is clipped", it.hasVisualOverflow) }
        val screenshot =
            File(
                InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "iot-${if (inspection) "inspection" else "repair"}-$dark-$fontScale.png",
            )
        screenshot.outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.onNodeWithContentDescription("Điều hướng").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(10.97 to 106.49, navigated) }
        if (inspection) {
            composeRule.onNodeWithText("Có sự cố").performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals(fault.faultId to "fault_present", outcome) }
        }
    }
}
