package com.luxmap.feature.survey.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurveyPlanDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SurveyPlanDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.surveyPlanDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertingAPlanTwiceByItsNaturalKeyDoesNotCreateADuplicateRow() =
        runTest {
            val plan =
                LocalSurveyPlanEntity(
                    surveySweepId = "SWEEP-1",
                    assignedByName = "Kỹ sư bảo trì A",
                    plannedDate = "2026-10-01",
                    status = "planned",
                    cachedAt = "2026-09-28T00:00:00Z",
                )

            dao.upsertPlans(listOf(plan))
            dao.upsertPlans(listOf(plan.copy(status = "in_progress")))

            dao.observePlans().test {
                val plans = awaitItem()
                assertEquals(1, plans.size)
                assertEquals("in_progress", plans.first().status)
            }
        }

    @Test
    fun roadSegmentsForASweepAreFilteredBySurveySweepId() =
        runTest {
            dao.upsertRoadSegments(
                listOf(
                    LocalRoadSegmentEntity(
                        roadSegmentId = "RS-1",
                        surveySweepId = "SWEEP-1",
                        name = "Đường A",
                        lengthMeters = 500.0,
                    ),
                    LocalRoadSegmentEntity(
                        roadSegmentId = "RS-2",
                        surveySweepId = "SWEEP-2",
                        name = "Đường B",
                        lengthMeters = 300.0,
                    ),
                ),
            )

            val segments = dao.roadSegmentsFor("SWEEP-1")

            assertEquals(1, segments.size)
            assertEquals("RS-1", segments.first().roadSegmentId)
        }
}
