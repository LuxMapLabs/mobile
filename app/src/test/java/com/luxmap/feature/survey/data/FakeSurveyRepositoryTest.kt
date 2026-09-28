package com.luxmap.feature.survey.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSurveyRepositoryTest {
    @Test
    fun `emits at least one assigned route with its road segments`() =
        runTest {
            val repository = FakeSurveyRepository()

            repository.observeAssignedRoutes().test {
                val routes = awaitItem()
                assertTrue(routes.isNotEmpty())
                assertTrue(routes.first().roadSegments.isNotEmpty())
                awaitComplete()
            }
        }
}
