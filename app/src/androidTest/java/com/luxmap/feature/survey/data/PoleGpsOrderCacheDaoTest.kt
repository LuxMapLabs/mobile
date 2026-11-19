package com.luxmap.feature.survey.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalAssignedPoleGpsOrderEntity
import com.luxmap.feature.survey.data.entity.LocalAssignedSurveyRouteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoleGpsOrderCacheDaoTest {
    @Test
    fun survey_cache_never_returns_another_owner_rows() =
        runTest {
            val db =
                Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).build()
            try {
                val dao = db.surveyRouteCacheDao()
                val a = LocalAssignedSurveyRouteEntity("S", "Survey A", "C", "assigned", null, null, "A")
                dao.replaceAll(listOf(a))
                assertEquals(listOf(a), dao.allForOwner("A"))
                assertEquals(emptyList<LocalAssignedSurveyRouteEntity>(), dao.allForOwner("B"))
            } finally {
                db.close()
            }
        }

    @Test
    fun replacement_is_atomic_and_scoped_to_one_owner_and_table() =
        runTest {
            val db =
                Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).build()
            try {
                val dao = db.poleGpsOrderCacheDao()
                val a = LocalAssignedPoleGpsOrderEntity("A", "WO-1", "A", "C", "assigned", null, null)
                val b = a.copy(ownerUserId = "B", title = "B")
                dao.replaceAll("A", listOf(a))
                dao.replaceAll("B", listOf(b))
                val survey = LocalAssignedSurveyRouteEntity("S", "Survey", "C", "assigned", null, null)
                db.surveyRouteCacheDao().replaceAll(listOf(survey))
                try {
                    dao.replaceAll("A", listOf(b))
                    error("wrong owner should fail")
                } catch (_: IllegalArgumentException) {
                    assertEquals(listOf(a), dao.all("A"))
                }
                dao.replaceAll("A", emptyList())
                assertEquals(emptyList<LocalAssignedPoleGpsOrderEntity>(), dao.all("A"))
                assertEquals(listOf(b), dao.all("B"))
                assertEquals(listOf(survey), db.surveyRouteCacheDao().all())
            } finally {
                db.close()
            }
        }
}
