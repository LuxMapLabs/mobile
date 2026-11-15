package com.luxmap.feature.polelocation.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PoleLocationUpdateDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PoleLocationUpdateDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.poleLocationUpdateDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(
        poleId: String = "POLE-1",
        status: String = PoleLocationUpdateStatus.PENDING,
    ) = LocalPoleLocationUpdateEntity(poleId, "/f/a.jpg", 10.0, 106.0, Instant.ofEpochSecond(1), status, "OP-1")

    @Test
    fun upsert_then_byPoleId_returns_the_row() =
        runTest {
            dao.upsert(entity())
            val row = dao.byPoleId("POLE-1")
            assertEquals(10.0, row!!.lat, 0.0)
        }

    @Test
    fun byPoleId_with_no_row_is_null() =
        runTest {
            assertNull(dao.byPoleId("POLE-1"))
        }

    @Test
    fun updateStatus_changes_only_the_status_column() =
        runTest {
            dao.upsert(entity())
            dao.updateStatus("POLE-1", PoleLocationUpdateStatus.CONFLICT)
            val row = dao.byPoleId("POLE-1")
            assertEquals(PoleLocationUpdateStatus.CONFLICT, row!!.status)
            assertEquals(10.0, row.lat, 0.0)
        }

    @Test
    fun upsert_replaces_an_existing_row_for_the_same_pole() =
        runTest {
            dao.upsert(entity(status = PoleLocationUpdateStatus.PENDING))
            dao.upsert(entity(status = PoleLocationUpdateStatus.QUEUED))
            assertEquals(PoleLocationUpdateStatus.QUEUED, dao.byPoleId("POLE-1")!!.status)
        }
}
