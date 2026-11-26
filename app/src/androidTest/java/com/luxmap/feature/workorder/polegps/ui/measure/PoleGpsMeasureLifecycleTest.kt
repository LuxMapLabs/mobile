package com.luxmap.feature.workorder.polegps.ui.measure

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.luxmap.core.camera.PhotoGpsExif
import com.luxmap.core.camera.PhotoLocationReader
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.polegps.data.GpsClock
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PoleGpsMeasureLifecycleTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun denied_permission_then_retry_and_disposal_manage_exactly_one_observer() {
        var active = 0
        val permission = mutableStateOf(false)
        val visible = mutableStateOf(true)
        val source =
            object : GpsFixSource {
                override fun fixes() =
                    flow<GpsFix> {
                        active++
                        try {
                            awaitCancellation()
                        } finally {
                            active--
                        }
                    }
            }
        lateinit var vm: PoleGpsMeasureViewModel
        rule.runOnUiThread {
            vm =
                PoleGpsMeasureViewModel(
                    SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")),
                    mockk<PoleGpsRepository>(relaxed = true), source, mockk<PhotoGpsExif>(relaxed = true),
                    mockk<PhotoLocationReader>(relaxed = true), GpsClock(),
                )
        }
        rule.setContent { if (visible.value) LocationWatchingEffect(permission.value, vm) }
        rule.runOnIdle {
            assertEquals(0, active)
            permission.value = true
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, active)
            vm.startWatchingLocation()
            assertEquals(1, active)
        }
        rule.runOnIdle { permission.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0, active)
            permission.value = true
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, active)
            visible.value = false
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, active) }
    }

    @Test
    fun denied_permission_retry_starts_GPS_before_camera_and_cancel_saves_nothing() {
        var active = 0
        var activeAtCamera = 0
        var cameraLaunches = 0
        var grant = false
        var backed = 0
        val visible = mutableStateOf(true)
        val source =
            object : GpsFixSource {
                override fun fixes() =
                    flow<GpsFix> {
                        active++
                        try {
                            awaitCancellation()
                        } finally {
                            active--
                        }
                    }
            }
        val repository = mockk<PoleGpsRepository>(relaxed = true)
        coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 1, 10.0, 106.0)
        lateinit var vm: PoleGpsMeasureViewModel
        rule.runOnUiThread {
            vm =
                PoleGpsMeasureViewModel(
                    SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")),
                    repository, source, mockk<PhotoGpsExif>(relaxed = true),
                    mockk<PhotoLocationReader>(relaxed = true), GpsClock(),
                )
        }
        val registry =
            object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    when (contract) {
                        is ActivityResultContracts.RequestMultiplePermissions ->
                            dispatchResult(
                                requestCode,
                                mapOf(
                                    Manifest.permission.CAMERA to grant,
                                    Manifest.permission.ACCESS_FINE_LOCATION to grant,
                                ),
                            )
                        is ActivityResultContracts.TakePicture -> {
                            cameraLaunches++
                            activeAtCamera = active
                            dispatchResult(requestCode, false)
                        }
                    }
                }
            }
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val deniedContext =
            object : ContextWrapper(targetContext) {
                override fun checkPermission(
                    permission: String,
                    pid: Int,
                    uid: Int,
                ) = PackageManager.PERMISSION_DENIED
            }
        rule.setContent {
            CompositionLocalProvider(
                LocalContext provides deniedContext,
                LocalActivityResultRegistryOwner provides owner,
            ) {
                LuxMapTheme {
                    if (visible.value) {
                        PoleGpsMeasureRoute(onBack = {
                            backed++
                            visible.value = false
                        }, viewModel = vm)
                    }
                }
            }
        }
        rule.onNodeWithText("Cần cấp quyền vị trí", substring = true).assertExists()
        rule.runOnIdle {
            assertEquals(0, cameraLaunches)
            assertEquals(0, active)
            grant = true
        }
        rule.onNodeWithText("Thử lại").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, cameraLaunches)
            assertEquals(1, activeAtCamera)
            assertEquals(1, backed)
            assertEquals(0, active)
        }
        coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun restoring_preview_retains_photo_and_retake_cancels_without_saving() {
        var active = 0
        var cameras = 0
        var cameraRequest = 0
        var done = 0
        val visible = mutableStateOf(true)
        val repository = mockk<PoleGpsRepository>(relaxed = true)
        val exif = mockk<PhotoGpsExif>(relaxed = true)
        io.mockk.every { exif.read(any()) } returns null
        coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 1, 10.0, 106.0)
        val clock =
            object : GpsClock() {
                override fun elapsedRealtimeNanos() = 1_000_000_000L
            }
        val source =
            object : GpsFixSource {
                override fun fixes() =
                    flow {
                        active++
                        try {
                            emit(GpsFix(10.5, 106.5, 5.0, Instant.ofEpochSecond(42), 1_000_000_000L))
                            awaitCancellation()
                        } finally {
                            active--
                        }
                    }
            }
        lateinit var vm: PoleGpsMeasureViewModel
        rule.runOnUiThread {
            vm =
                PoleGpsMeasureViewModel(
                    SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")),
                    repository, source, exif, mockk<PhotoLocationReader>(relaxed = true), clock,
                )
        }
        val registry =
            object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    when (contract) {
                        is ActivityResultContracts.RequestMultiplePermissions ->
                            dispatchResult(
                                requestCode,
                                mapOf(
                                    Manifest.permission.CAMERA to true,
                                    Manifest.permission.ACCESS_FINE_LOCATION to true,
                                ),
                            )
                        is ActivityResultContracts.TakePicture -> {
                            cameras++
                            cameraRequest = requestCode
                        }
                    }
                }
            }
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val deniedContext =
            object : ContextWrapper(targetContext) {
                override fun checkPermission(
                    permission: String,
                    pid: Int,
                    uid: Int,
                ) = PackageManager.PERMISSION_DENIED
            }
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(
                LocalContext provides deniedContext,
                LocalActivityResultRegistryOwner provides owner,
            ) {
                LuxMapTheme {
                    if (visible.value) {
                        PoleGpsMeasureRoute(onBack = {
                            done++
                            visible.value = false
                        }, viewModel = vm)
                    }
                }
            }
        }
        rule.runOnIdle {
            assertEquals(1, cameras)
            registry.dispatchResult(cameraRequest, true)
        }
        rule.onNodeWithText("Dùng ảnh này").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(1, cameras)
            assertEquals(1, active)
        }
        // Restored preview retains the photo; disposal never writes an invented position.
        coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
        rule.onNodeWithText("Chụp lại").performClick()
        rule.runOnIdle {
            assertEquals(2, cameras)
            registry.dispatchResult(cameraRequest, false)
        }
        rule.runOnIdle {
            assertEquals(1, done)
            assertEquals(0, active)
        }
        coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
    }
}
