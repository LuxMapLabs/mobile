package com.luxmap.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// exportSchema=false: follow Migration22To23Test's real-Room validation pattern.
internal fun restoreV23PoleCache(db: SQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE poles_v23 (workOrderId TEXT NOT NULL, poleId TEXT NOT NULL, position INTEGER, " +
            "lat REAL, lng REAL, fixtureStatus TEXT NOT NULL, PRIMARY KEY(workOrderId, poleId))",
    )
    db.execSQL(
        "INSERT INTO poles_v23 SELECT workOrderId, poleId, position, lat, lng, fixtureStatus FROM local_pole_list_item",
    )
    db.execSQL("DROP TABLE local_pole_list_item")
    db.execSQL("ALTER TABLE poles_v23 RENAME TO local_pole_list_item")
}

@RunWith(AndroidJUnit4::class)
class Migration23To24Test {
    @Test
    fun upgrade_preserves_cached_poles_measurements_evidence_and_upload_queue() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-23-24-test.db"
            context.deleteDatabase(name)
            val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            before.openHelper.writableDatabase.apply {
                execSQL(
                    "INSERT INTO local_pole_list_item (workOrderId, poleId, position, lat, lng, fixtureStatus) " +
                        "VALUES ('W', 'P', 2, 10.0, 106.0, 'normal')",
                )
                execSQL(
                    "INSERT INTO local_pole_gps_measurement VALUES " +
                        "('W', 'P', 10.0, 106.0, 5.0, 1000, 'device', '/photo.jpg', 'E', 'queued')",
                )
                execSQL(
                    "INSERT INTO local_work_order_evidence VALUES " +
                        "('E', 'W', 'photo', '/photo.jpg', 1000, 10.0, 106.0, 'queued', 'P')",
                )
                execSQL(
                    "INSERT INTO sync_queue (clientOpId, opType, payloadJson, dependsOnClientOpId, status, " +
                        "attemptCount, lastError, createdAt, updatedAt) " +
                        "VALUES ('E', 'upload_evidence', '{}', NULL, 'queued', 0, NULL, 1000, 1000)",
                )
            }
            before.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                restoreV23PoleCache(it)
                it.execSQL("DELETE FROM room_master_table")
                it.version = 23
            }
            val upgraded =
                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    name,
                ).addMigrations(MIGRATION_23_24).build()
            try {
                val db = upgraded.openHelper.writableDatabase // Validates the entire schema.
                val pole = upgraded.poleListCacheDao().byWorkOrder("W").single()
                assertEquals("P", pole.poleId)
                assertEquals(2, pole.position)
                assertEquals(10.0, pole.lat!!, 0.0)
                assertNull(pole.note)
                assertNull(pole.segmentId)
                assertNull(pole.fixtureType)
                assertNull(pole.lampWatt)
                assertNull(pole.lampCount)
                assertNull(pole.fixtureCount)
                assertNull(pole.nearSensitivePoi)
                listOf("local_pole_gps_measurement", "local_work_order_evidence", "sync_queue").forEach { table ->
                    db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                        cursor.moveToFirst()
                        assertEquals(table, 1, cursor.getInt(0))
                    }
                }
                db.query("SELECT photoFilePath, evidenceClientOpId FROM local_pole_gps_measurement").use {
                    it.moveToFirst()
                    assertEquals("/photo.jpg", it.getString(0))
                    assertEquals("E", it.getString(1))
                }
                val enriched =
                    pole.copy(
                        segmentId = "S",
                        note = "Cạnh cầu",
                        fixtureType = "led",
                        lampWatt = 100,
                        lampCount = 2,
                        fixtureCount = 1,
                        nearSensitivePoi = true,
                    )
                upgraded.poleListCacheDao().replaceForWorkOrder("W", listOf(enriched))
                assertEquals(enriched, upgraded.poleListCacheDao().byWorkOrder("W").single())
            } finally {
                upgraded.close()
                context.deleteDatabase(name)
            }
        }
}
