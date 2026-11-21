package com.luxmap.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.feature.survey.data.entity.LocalAssignedPoleGpsOrderEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration22To23Test {
    @Test
    fun upgrade_preserves_existing_rows_and_room_validates_new_cache() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-22-23-test.db"
            context.deleteDatabase(name)
            // Derive the complete v22 schema by removing the two v23 additions: GPS cache
            // and survey owner column. Reopening validates every table with real Room.
            val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val sqlite = before.openHelper.writableDatabase
            sqlite.execSQL(
                "INSERT INTO local_assigned_survey_route " +
                    "(workOrderId, title, communeId, woStatus, dueDate, scheduledDate) " +
                    "VALUES ('S', 'survey', 'C', 'assigned', NULL, NULL)",
            )
            sqlite.execSQL(
                "INSERT INTO local_pole_gps_measurement VALUES " +
                    "('W', 'P', 10.0, 106.0, 5.0, 1000, 'device', '/photo.jpg', 'E', 'queued')",
            )
            sqlite.execSQL(
                "INSERT INTO local_work_order_evidence VALUES ('E', 'W', 'photo', '/photo.jpg', " +
                    "1000, 10.0, 106.0, 'queued', 'P')",
            )
            sqlite.execSQL(
                "INSERT INTO sync_queue (clientOpId, opType, payloadJson, dependsOnClientOpId, status, " +
                    "attemptCount, lastError, createdAt, updatedAt) " +
                    "VALUES ('E', 'upload_evidence', '{}', NULL, 'queued', 0, NULL, 1000, 1000)",
            )
            before.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE local_work_order_draft")
                restoreV23PoleCache(it)
                it.execSQL("DROP TABLE local_assigned_pole_gps_order")
                it.execSQL(
                    "CREATE TABLE survey_v22 (workOrderId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                        "communeId TEXT NOT NULL, woStatus TEXT NOT NULL, dueDate TEXT, scheduledDate TEXT)",
                )
                it.execSQL(
                    "INSERT INTO survey_v22 SELECT workOrderId, title, communeId, woStatus, dueDate, scheduledDate " +
                        "FROM local_assigned_survey_route",
                )
                it.execSQL("DROP TABLE local_assigned_survey_route")
                it.execSQL("ALTER TABLE survey_v22 RENAME TO local_assigned_survey_route")
                it.execSQL("DELETE FROM room_master_table")
                it.version = 22
            }
            val upgraded =
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25).build()
            try {
                val database = upgraded.openHelper.writableDatabase
                listOf(
                    "local_assigned_survey_route",
                    "local_pole_gps_measurement",
                    "local_work_order_evidence",
                    "sync_queue",
                ).forEach { table ->
                    database.query("SELECT COUNT(*) FROM $table").use {
                        it.moveToFirst()
                        assertEquals(table, 1, it.getInt(0))
                    }
                }
                database.query("SELECT photoFilePath, evidenceClientOpId FROM local_pole_gps_measurement").use {
                    it.moveToFirst()
                    assertEquals("/photo.jpg", it.getString(0))
                    assertEquals("E", it.getString(1))
                }
                assertEquals(1, upgraded.surveyRouteCacheDao().all().size)
                assertEquals(emptyList<Any>(), upgraded.surveyRouteCacheDao().allForOwner("A"))
                val row = LocalAssignedPoleGpsOrderEntity("A", "W", "GPS", "C", "assigned", null, null)
                upgraded.poleGpsOrderCacheDao().replaceAll("A", listOf(row))
                assertEquals(listOf(row), upgraded.poleGpsOrderCacheDao().all("A"))
            } finally {
                upgraded.close()
                context.deleteDatabase(name)
            }
        }
}
