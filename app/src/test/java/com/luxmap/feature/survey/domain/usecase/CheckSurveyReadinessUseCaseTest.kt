package com.luxmap.feature.survey.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckSurveyReadinessUseCaseTest {
    private val useCase = CheckSurveyReadinessUseCase()

    private fun readyInput() =
        SurveyReadinessInput(
            cameraPermissionGranted = true,
            exposureLockSupported = true,
            timestampSourceRealtime = true,
            gpsAvailable = true,
            headingAvailable = true,
            freeStorageBytes = 2_000_000_000L,
            requiredStorageBytes = 1_000_000_000L,
            batteryPercent = 80,
        )

    @Test
    fun `is ready when every check passes`() {
        assertTrue(useCase(readyInput()).isReady)
    }

    @Test
    fun `blocks hard when timestamp source is not REALTIME even if everything else passes`() {
        val result = useCase(readyInput().copy(timestampSourceRealtime = false))

        assertFalse(result.isReady)
        assertFalse(result.timestampSourceRealtime)
    }

    @Test
    fun `blocks when free storage is below the route's estimated requirement`() {
        val result = useCase(readyInput().copy(freeStorageBytes = 500_000_000L, requiredStorageBytes = 1_000_000_000L))

        assertFalse(result.isReady)
    }

    @Test
    fun `blocks when battery is below the minimum threshold`() {
        val result = useCase(readyInput().copy(batteryPercent = 10))

        assertFalse(result.isReady)
    }

    @Test
    fun `is not ready when heading sensor is unavailable`() {
        val input = readyInput().copy(headingAvailable = false)

        val result = useCase(input)

        assertFalse(result.isReady)
        assertFalse(result.headingAvailable)
    }
}
