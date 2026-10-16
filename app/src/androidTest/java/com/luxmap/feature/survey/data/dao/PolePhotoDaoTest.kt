package com.luxmap.feature.survey.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PolePhotoDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PolePhotoDao

    private fun photo(
        photoId: String,
        workOrderId: String = "WO-1",
        poleId: String = "POLE-1",
        capturedAt: Instant = Instant.parse("2026-10-15T10:00:00Z"),
    ) = LocalPolePhotoEntity(
        photoId = photoId,
        workOrderId = workOrderId,
        poleId = poleId,
        filePath = "/data/pole-photos/$photoId.jpg",
        capturedAt = capturedAt,
        lat = 10.97,
        lng = 106.49,
    )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.polePhotoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeByWorkOrderReturnsOnlyRowsForThatWorkOrder() =
        runTest {
            dao.insert(photo("PH-1", workOrderId = "WO-1"))
            dao.insert(photo("PH-2", workOrderId = "WO-2"))

            dao.observeByWorkOrder("WO-1").test {
                assertEquals(listOf("PH-1"), awaitItem().map { it.photoId })
            }
        }

    @Test
    fun observeByWorkOrderOrdersNewestFirst() =
        runTest {
            dao.insert(photo("OLD", capturedAt = Instant.parse("2026-10-15T09:00:00Z")))
            dao.insert(photo("NEW", capturedAt = Instant.parse("2026-10-15T10:00:00Z")))

            dao.observeByWorkOrder("WO-1").test {
                assertEquals(listOf("NEW", "OLD"), awaitItem().map { it.photoId })
            }
        }

    @Test
    fun observeByWorkOrderCanReturnTwoRowsForTheSamePoleWhenRecaptured() =
        runTest {
            dao.insert(photo("FIRST", poleId = "POLE-1", capturedAt = Instant.parse("2026-10-15T09:00:00Z")))
            dao.insert(photo("SECOND", poleId = "POLE-1", capturedAt = Instant.parse("2026-10-15T10:00:00Z")))

            dao.observeByWorkOrder("WO-1").test {
                val rows = awaitItem()
                assertEquals(2, rows.size)
                assertEquals(setOf("POLE-1"), rows.map { it.poleId }.toSet())
            }
        }
}
