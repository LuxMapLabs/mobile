package com.luxmap.feature.workorder.polegps.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.sync.CompleteWorkOrderSyncHandler
import com.luxmap.feature.workorder.data.sync.CompletionSyncPayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

class PoleGpsSubmissionTest {
    private lateinit var db: AppDatabase
    private lateinit var gps: PoleGpsRepository
    private val files = mutableListOf<File>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val report =
        LocalWorkOrderCompletionEntity("WO-1", "Đã đo các cột được giao", null, null, "COMP-1", "pending")
    private val at = Instant.parse("2026-11-18T02:00:00Z")

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val sync = mockk<PoleGpsDetailSync>()
        coEvery { sync.cached("WO-1") } returns
            WorkOrderDetailDto(
                "WO-1", "Đo GPS", "pole_gps_update", "in_progress", poleIds = listOf("P-1", "P-2"),
            )
        gps =
            PoleGpsRepository(
                db, db.poleGpsDao(), mockk(relaxed = true), mockk(relaxed = true), sync,
                db.poleListCacheDao(), db.workOrderCompletionDao(), mockk(relaxed = true), mockk(relaxed = true),
            )
    }

    @After
    fun cleanup() {
        db.close()
        files.forEach { it.delete() }
    }

    private fun gpsApi(): WorkOrdersApi =
        mockk<WorkOrdersApi>(relaxed = true).also {
            coEvery { it.detail("WO-1") } returns WorkOrderDetailDto("WO-1", "GPS", "pole_gps_update", "in_progress")
        }

    private suspend fun measure(
        poleId: String = "P-1",
        lat: Double = 10.0,
    ) {
        val photo = File.createTempFile("gps-proof", ".jpg", context.cacheDir).apply { writeText("image") }
        files += photo
        gps.saveCapturedPhoto("WO-1", poleId, photo.path, lat, 106.0, 3.0, at)
    }

    private suspend fun payload(): CompletionSyncPayload =
        Json.decodeFromString(
            db.workOrderCompletionDao().submissionOperation("COMP-1", "complete_work_order")!!.payloadJson,
        )

    @Test
    fun partial_report_is_snapshotted_with_photo_dependencies_and_duplicate_submit_is_ignored() =
        runTest {
            measure()
            assertTrue(gps.stageCompletion(report))
            assertFalse(gps.stageCompletion(report.copy(clientOpId = "COMP-DUPLICATE")))
            val submitted = payload()
            assertEquals(listOf("P-1"), submitted.poleLocations!!.map { it.poleId })
            assertEquals(1, submitted.evidenceClientOpIds!!.size)
            assertEquals(2, db.syncQueueDao().queuedRows().size)
            assertNotNull(db.workOrderCompletionDao().completionByWorkOrderId("WO-1"))
        }

    @Test
    fun incomplete_photo_set_rolls_back_the_entire_submission() =
        runTest {
            measure()
            db.poleGpsDao().upsertDeviceMeasurement(
                LocalPoleGpsMeasurementEntity("WO-1", "P-2", 11.0, 106.0, 3.0, at, "device", "/missing.jpg"),
            )
            assertTrue(runCatching { gps.stageCompletion(report) }.isFailure)
            assertNull(db.workOrderCompletionDao().completionByWorkOrderId("WO-1"))
            assertTrue(db.workOrderCompletionDao().evidenceForCompletion("WO-1", "observation").isEmpty())
            assertTrue(db.syncQueueDao().queuedRows().isEmpty())
            assertNull(db.poleGpsDao().measurements("WO-1").first { it.poleId == "P-1" }.evidenceClientOpId)
        }

    @Test
    fun waiting_photo_blocks_complete_and_retry_sends_original_coordinates() =
        runTest {
            measure()
            gps.stageCompletion(report)
            val submitted = payload()
            val api = gpsApi()
            val handler = CompleteWorkOrderSyncHandler(api, db.workOrderCompletionDao(), gps)
            val operation = db.workOrderCompletionDao().submissionOperation("COMP-1", "complete_work_order")!!
            assertEquals(SyncOpResult.WaitingForDependency, handler.handle(operation.payloadJson))
            coVerify(exactly = 0) { api.complete(any(), any()) }
            measure(lat = 12.0)
            db.workOrderCompletionDao().updateEvidenceUploadStatus(submitted.evidenceClientOpIds!!.single(), "synced")
            assertEquals(SyncOpResult.Done, handler.handle(operation.payloadJson))
            coVerify { api.complete("WO-1", match { it.poleLocations!!.single().lat == 10.0 }) }
        }

    @Test
    fun legacy_submission_is_upgraded_durably_and_waits_for_photos() =
        runTest {
            measure()
            db.workOrderCompletionDao().insertOrReplaceCompletion(report)
            val now = Instant.now()
            db.syncQueueDao().insert(
                com.luxmap.core.sync.SyncQueueEntity(
                    clientOpId = "COMP-1",
                    opType = "complete_work_order",
                    payloadJson = "{\"work_order_id\":\"WO-1\"}",
                    dependsOnClientOpId = null,
                    status = "queued",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val api = gpsApi()
            val handler = CompleteWorkOrderSyncHandler(api, db.workOrderCompletionDao(), gps)
            assertEquals(SyncOpResult.WaitingForDependency, handler.handle("{\"work_order_id\":\"WO-1\"}"))
            assertEquals(1, payload().poleLocations!!.size)
            assertEquals(1, payload().evidenceClientOpIds!!.size)
            coVerify(exactly = 0) { api.complete(any(), any()) }
        }

    @Test
    fun failed_gps_submission_reopens_without_deleting_measurements_or_photos() =
        runTest {
            measure()
            gps.stageCompletion(report)
            val operation = db.workOrderCompletionDao().submissionOperation("COMP-1", "complete_work_order")!!
            db.syncQueueDao().updateStatus(operation.id, "failed", 0, "Missing data", Instant.now())
            db.workOrderCompletionDao().updateCompletionSubmitStatus("WO-1", "failed")
            assertTrue(gps.reopenFailedCompletion("WO-1"))
            assertEquals("draft", db.workOrderCompletionDao().completionByWorkOrderId("WO-1")!!.submitStatus)
            assertEquals(1, db.poleGpsDao().measurements("WO-1").size)
            assertEquals(1, db.workOrderCompletionDao().evidenceForCompletion("WO-1", "observation").size)
            assertTrue(files.single().isFile)
        }

    @Test
    fun returned_order_with_retaken_photo_uses_new_evidence_without_reuploading_the_old_photo() =
        runTest {
            measure()
            gps.stageCompletion(report)
            val oldPhoto = payload().evidenceClientOpIds!!.single()
            val oldUpload = db.workOrderCompletionDao().submissionOperation(oldPhoto, "upload_work_order_evidence")!!
            val oldComplete = db.workOrderCompletionDao().submissionOperation("COMP-1", "complete_work_order")!!
            db.workOrderCompletionDao().updateEvidenceUploadStatus(oldPhoto, "synced")
            db.syncQueueDao().updateStatus(oldUpload.id, "done", 0, null, Instant.now())
            db.syncQueueDao().updateStatus(oldComplete.id, "done", 0, null, Instant.now())
            db.workOrderCompletionDao().updateCompletionSubmitStatus("WO-1", "synced")
            db.workOrderCompletionDao().deleteSyncedCompletion("WO-1", "COMP-1")
            measure(lat = 11.0)
            assertTrue(gps.stageCompletion(report.copy(clientOpId = "COMP-2")))
            val next =
                Json.decodeFromString<CompletionSyncPayload>(
                    db.workOrderCompletionDao().submissionOperation("COMP-2", "complete_work_order")!!.payloadJson,
                )
            assertEquals(11.0, next.poleLocations!!.single().lat, 0.0)
            assertTrue(next.evidenceClientOpIds!!.single() != oldPhoto)
            assertEquals(
                "done",
                db.workOrderCompletionDao().submissionOperation(oldPhoto, "upload_work_order_evidence")!!.status,
            )
            assertEquals(2, db.syncQueueDao().queuedRows().size)
        }

    @Test
    fun rework_resends_previous_server_coordinates_without_new_photos() =
        runTest {
            db.poleGpsDao().insertServerMeasurements(
                listOf(
                    LocalPoleGpsMeasurementEntity("WO-1", "P-1", 10.0, 106.0, 3.0, at, MeasurementSourceColumn.SERVER),
                ),
            )
            measure(poleId = "P-2", lat = 11.0)
            assertTrue(gps.stageCompletion(report))
            val submitted = payload()
            assertEquals(setOf("P-1", "P-2"), submitted.poleLocations!!.map { it.poleId }.toSet())
            assertEquals(10.0, submitted.poleLocations!!.single { it.poleId == "P-1" }.lat, 0.0)
            assertEquals(1, submitted.evidenceClientOpIds!!.size)
        }

    @Test
    fun remeasured_server_pole_is_sent_once_with_its_new_coordinates() =
        runTest {
            db.poleGpsDao().insertServerMeasurements(
                listOf(
                    LocalPoleGpsMeasurementEntity("WO-1", "P-1", 10.0, 106.0, 3.0, at, MeasurementSourceColumn.SERVER),
                ),
            )
            measure(poleId = "P-1", lat = 11.0)
            assertTrue(gps.stageCompletion(report))
            val location = payload().poleLocations!!.single()
            assertEquals("P-1", location.poleId)
            assertEquals(11.0, location.lat, 0.0)
        }

    @Test
    fun report_with_only_server_coordinates_is_refused_and_nothing_is_queued() =
        runTest {
            db.poleGpsDao().insertServerMeasurements(
                listOf(
                    LocalPoleGpsMeasurementEntity("WO-1", "P-1", 10.0, 106.0, 3.0, at, MeasurementSourceColumn.SERVER),
                ),
            )
            val error = runCatching { gps.stageCompletion(report) }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertTrue(db.syncQueueDao().queuedRows().isEmpty())
            assertNull(db.workOrderCompletionDao().completionByWorkOrderId("WO-1"))
        }

    @Test
    fun reopened_report_retries_exhausted_upload_while_replacing_a_rejected_photo() =
        runTest {
            measure()
            measure(poleId = "P-2", lat = 11.0)
            gps.stageCompletion(report)
            val old = payload()
            val firstPhoto = old.evidenceClientOpIds!![0]
            val secondPhoto = old.evidenceClientOpIds!![1]
            val firstOp = db.workOrderCompletionDao().submissionOperation(firstPhoto, "upload_work_order_evidence")!!
            val secondOp = db.workOrderCompletionDao().submissionOperation(secondPhoto, "upload_work_order_evidence")!!
            val complete = db.workOrderCompletionDao().submissionOperation("COMP-1", "complete_work_order")!!
            db.syncQueueDao().updateStatus(
                firstOp.id,
                "failed",
                8,
                com.luxmap.core.sync.SYNC_ATTEMPTS_EXHAUSTED,
                Instant.now(),
            )
            db.syncQueueDao().updateStatus(
                secondOp.id,
                "failed",
                0,
                com.luxmap.feature.workorder.data.sync.EVIDENCE_REPLACEMENT_REQUIRED,
                Instant.now(),
            )
            db.syncQueueDao().updateStatus(complete.id, "failed", 0, "Evidence failed", Instant.now())
            db.workOrderCompletionDao().updateCompletionSubmitStatus("WO-1", "failed")
            gps.reopenFailedCompletion("WO-1")
            measure(poleId = "P-2", lat = 12.0)
            gps.stageCompletion(report.copy(clientOpId = "COMP-2"))
            assertEquals(
                "queued",
                db.workOrderCompletionDao().submissionOperation(firstPhoto, "upload_work_order_evidence")!!.status,
            )
            assertEquals(
                0,
                db.workOrderCompletionDao().submissionOperation(
                    firstPhoto,
                    "upload_work_order_evidence",
                )!!.attemptCount,
            )
        }

    @Test
    fun interrupted_GPS_complete_is_selected_for_recovery() =
        runTest {
            db.poleGpsDao().upsertOrder(LocalPoleGpsOrderEntity("WO-1", "{}", at))
            measure()
            gps.stageCompletion(report)
            val operation = db.workOrderCompletionDao().submissionOperation("COMP-1", "complete_work_order")!!
            db.syncQueueDao().updateStatus(operation.id, "syncing", 0, null, Instant.now())
            assertTrue(db.syncQueueDao().queuedRows().any { it.clientOpId == "COMP-1" })
            db.workOrderCompletionDao().insertOrReplaceCompletion(
                report.copy(workOrderId = "WO-OTHER", clientOpId = "COMP-OTHER"),
            )
            db.syncQueueDao().insert(operation.copy(id = 0, clientOpId = "COMP-OTHER", status = "syncing"))
            assertFalse(db.syncQueueDao().queuedRows().any { it.clientOpId == "COMP-OTHER" })
        }
}
