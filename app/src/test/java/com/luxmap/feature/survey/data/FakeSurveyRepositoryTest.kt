package com.luxmap.feature.survey.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSurveyRepositoryTest {
    @Test
    fun `emits at least one assigned route with a real work order id`() =
        runTest {
            val repository = FakeSurveyRepository()

            repository.observeAssignedRoutes().test {
                val result = awaitItem()
                assertTrue(result.routes.isNotEmpty())
                assertTrue(result.routes.first().workOrderId.isNotEmpty())
                awaitComplete()
            }
        }
}
