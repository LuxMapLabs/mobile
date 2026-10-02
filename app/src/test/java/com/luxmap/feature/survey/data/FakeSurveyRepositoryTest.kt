package com.luxmap.feature.survey.data

import android.content.Context
import app.cash.turbine.test
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSurveyRepositoryTest {
    @Test
    fun `emits at least one assigned route with its road segments`() =
        runTest {
            // observeAssignedRoutes does not touch sessionDao/context, so plain mocks are
            // enough here - segmentFilePathsFor/discardSession get their own androidTest
            // coverage with a real Room database and real files.
            val repository = FakeSurveyRepository(mockk<SurveySessionDao>(), mockk<Context>())

            repository.observeAssignedRoutes().test {
                val routes = awaitItem()
                assertTrue(routes.isNotEmpty())
                assertTrue(routes.first().roadSegments.isNotEmpty())
                awaitComplete()
            }
        }
}
