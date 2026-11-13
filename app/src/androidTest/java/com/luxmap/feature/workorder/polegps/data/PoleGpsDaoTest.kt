package com.luxmap.feature.workorder.polegps.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PoleGpsDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PoleGpsDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.poleGpsDao()
    }

    @After
    fun tearDown() = db.close()

    private fun measurement(
        source: String,
        lat: Double,
    ) = LocalPoleGpsMeasurementEntity("WO-1", "P-1", lat, 106.0, 3.0, Instant.ofEpochSecond(10), source)

    private fun attachment(
        id: String,
        status: String = AttachmentStatusColumn.PENDING,
    ) = LocalWorkOrderAttachmentEntity("WO-1", id, "$id.pdf", 10L, "/api/v1/x/$id", status, null)

    @Test
    fun device_measurement_replaces_an_earlier_server_row() =
        runTest {
            dao.insertServerMeasurements(listOf(measurement(MeasurementSourceColumn.SERVER, 1.0)))
            dao.upsertDeviceMeasurement(measurement(MeasurementSourceColumn.DEVICE, 2.0))
            val row = dao.measurements("WO-1").single()
            assertEquals(2.0, row.lat, 0.0)
            assertEquals(MeasurementSourceColumn.DEVICE, row.source)
        }

    @Test
    fun server_prefill_never_overwrites_a_device_measurement() =
        runTest {
            dao.upsertDeviceMeasurement(measurement(MeasurementSourceColumn.DEVICE, 2.0))
            dao.insertServerMeasurements(listOf(measurement(MeasurementSourceColumn.SERVER, 1.0)))
            assertEquals(2.0, dao.measurements("WO-1").single().lat, 0.0)
        }

    @Test
    fun replace_attachments_keeps_existing_status_and_drops_removed_files() =
        runTest {
            dao.replaceAttachments("WO-1", listOf(attachment("A-1"), attachment("A-2")))
            dao.updateAttachmentStatus("WO-1", "A-1", AttachmentStatusColumn.READY, "/files/a1.pdf")
            dao.replaceAttachments("WO-1", listOf(attachment("A-1")))
            val rows = dao.attachments("WO-1")
            assertEquals(listOf("A-1"), rows.map { it.attachmentId })
            assertEquals(AttachmentStatusColumn.READY, rows.single().status)
            assertEquals("/files/a1.pdf", rows.single().localPath)
        }

    @Test
    fun replace_attachments_with_empty_list_clears_the_order() =
        runTest {
            dao.replaceAttachments("WO-1", listOf(attachment("A-1")))
            dao.replaceAttachments("WO-1", emptyList())
            assertTrue(dao.attachments("WO-1").isEmpty())
        }

    @Test
    fun pole_list_cache_puts_unplaced_poles_last() =
        runTest {
            val cache = db.poleListCacheDao()
            cache.insertAll(
                listOf(
                    LocalPoleListItemEntity("WO-1", "P-null", null, null, null, "unknown"),
                    LocalPoleListItemEntity("WO-1", "P-2", 2, 1.0, 1.0, "normal"),
                    LocalPoleListItemEntity("WO-1", "P-1", 1, 1.0, 1.0, "normal"),
                ),
            )
            assertEquals(listOf("P-1", "P-2", "P-null"), cache.byWorkOrder("WO-1").map { it.poleId })
        }
}
