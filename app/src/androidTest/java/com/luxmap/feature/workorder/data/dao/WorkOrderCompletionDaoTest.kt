package com.luxmap.feature.workorder.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WorkOrderCompletionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: WorkOrderCompletionDao

    private fun evidence(
        clientOpId: String,
        workOrderId: String = "WO-1",
        capturedAt: Instant = Instant.parse("2026-10-06T10:00:00Z"),
    ) = LocalWorkOrderEvidenceEntity(
        clientOpId = clientOpId,
        workOrderId = workOrderId,
        kind = "after",
        filePath = "/data/evidence/$clientOpId.jpg",
        capturedAt = capturedAt,
        lat = 10.97,
        lng = 106.49,
        uploadStatus = "pending",
    )

    private fun completion(
        workOrderId: String = "WO-1",
        clientOpId: String = "COMP-1",
    ) = LocalWorkOrderCompletionEntity(
        workOrderId = workOrderId,
        reportNote = "Đã thay bóng đèn",
        materialsUsed = null,
        faultOutcomesJson = null,
        clientOpId = clientOpId,
        submitStatus = "pending",
    )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.workOrderCompletionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeLatestEvidenceReturnsTheMostRecentCaptureWhenTwoExist() =
        runTest {
            dao.insertEvidence(evidence("OLD", capturedAt = Instant.parse("2026-10-06T09:00:00Z")))
            dao.insertEvidence(evidence("NEW", capturedAt = Instant.parse("2026-10-06T10:00:00Z")))

            dao.observeLatestEvidence("WO-1").test {
                assertEquals("NEW", awaitItem()?.clientOpId)
            }
        }

    @Test
    fun latestEvidenceClientOpIdMatchesObserveLatestEvidence() =
        runTest {
            dao.insertEvidence(evidence("OLD", capturedAt = Instant.parse("2026-10-06T09:00:00Z")))
            dao.insertEvidence(evidence("NEW", capturedAt = Instant.parse("2026-10-06T10:00:00Z")))

            assertEquals("NEW", dao.latestEvidenceClientOpId("WO-1"))
        }

    @Test
    fun updateEvidenceUploadStatusChangesOnlyThatRow() =
        runTest {
            dao.insertEvidence(evidence("OP-1"))

            dao.updateEvidenceUploadStatus("OP-1", "synced")

            assertEquals("synced", dao.evidenceByClientOpId("OP-1")?.uploadStatus)
        }

    @Test
    fun insertOrReplaceCompletionReplacesThePreviousRowForTheSameWorkOrder() =
        runTest {
            dao.insertOrReplaceCompletion(completion(clientOpId = "COMP-1"))

            dao.insertOrReplaceCompletion(completion(clientOpId = "COMP-2"))

            assertEquals("COMP-2", dao.completionByWorkOrderId("WO-1")?.clientOpId)
        }

    @Test
    fun updateCompletionSubmitStatusChangesOnlyThatWorkOrder() =
        runTest {
            dao.insertOrReplaceCompletion(completion())

            dao.updateCompletionSubmitStatus("WO-1", "synced")

            assertEquals("synced", dao.completionByWorkOrderId("WO-1")?.submitStatus)
        }

    @Test
    fun latestEvidenceClientOpIdIsNullWhenNoneExists() =
        runTest {
            assertNull(dao.latestEvidenceClientOpId("WO-NONE"))
        }
}
