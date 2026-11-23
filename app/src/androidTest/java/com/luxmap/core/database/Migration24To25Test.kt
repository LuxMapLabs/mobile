package com.luxmap.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderDraftEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration24To25Test {
    @Test
    fun upgrade_preserves_offline_data_and_validates_owner_scoped_drafts() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-24-25-test.db"
            context.deleteDatabase(name)
            // exportSchema=false: derive the full previous schema, as in Migration23To24Test.
            val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            before.openHelper.writableDatabase.apply {
                execSQL(
                    "INSERT INTO local_pole_list_item (workOrderId, poleId, fixtureStatus, note) " +
                        "VALUES ('W', 'P', 'normal', 'Cạnh cầu')",
                )
                execSQL(
                    "INSERT INTO local_pole_gps_measurement VALUES " +
                        "('W', 'P', 10.0, 106.0, 5.0, 1000, 'device', '/photo.jpg', 'E', 'queued')",
                )
                execSQL(
                    "INSERT INTO local_work_order_evidence VALUES " +
                        "('E', 'W', 'observation', '/photo.jpg', 1000, 10.0, 106.0, 'draft', 'P')",
                )
                execSQL("INSERT INTO local_work_order_completion VALUES ('W', 'Báo cáo', NULL, NULL, 'C', 'pending')")
                execSQL(
                    "INSERT INTO sync_queue (clientOpId, opType, payloadJson, dependsOnClientOpId, status, " +
                        "attemptCount, lastError, createdAt, updatedAt) " +
                        "VALUES ('C', 'complete_work_order', '{\"work_order_id\":\"W\"}', " +
                        "NULL, 'queued', 2, NULL, 1000, 2000)",
                )
            }
            before.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE local_work_order_draft")
                it.execSQL("DELETE FROM room_master_table")
                it.version = 24
            }
            val upgraded =
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27).build()
            try {
                val db = upgraded.openHelper.writableDatabase // Room validates the entire schema.
                listOf(
                    "local_pole_list_item",
                    "local_pole_gps_measurement",
                    "local_work_order_evidence",
                    "local_work_order_completion",
                    "sync_queue",
                ).forEach { table ->
                    db.query("SELECT COUNT(*) FROM $table").use {
                        it.moveToFirst()
                        assertEquals(table, 1, it.getInt(0))
                    }
                }
                db.query("SELECT clientOpId, payloadJson, status, attemptCount, updatedAt FROM sync_queue").use {
                    it.moveToFirst()
                    assertEquals("C", it.getString(0))
                    assertEquals("{\"work_order_id\":\"W\"}", it.getString(1))
                    assertEquals("queued", it.getString(2))
                    assertEquals(2, it.getInt(3))
                    assertEquals(2000L, it.getLong(4))
                }
                assertEquals("Cạnh cầu", upgraded.poleListCacheDao().byWorkOrder("W").single().note)
                val dao = upgraded.workOrderDraftDao()
                assertNull(dao.get("U1", "W"))
                val first = LocalWorkOrderDraftEntity("U1", "W", "Ghi chú", "LED", "{\"F\":\"fault_present\"}")
                val second = first.copy(ownerUserId = "U2", reportNote = "Bản nháp riêng")
                dao.save(first)
                dao.save(second)
                dao.save(first.copy(reportNote = "Đã sửa"))
                assertEquals("Đã sửa", dao.get("U1", "W")?.reportNote)
                assertEquals(second, dao.get("U2", "W"))
                dao.delete("U1", "W")
                assertNull(dao.get("U1", "W"))
                assertEquals(second, dao.get("U2", "W"))
            } finally {
                upgraded.close()
                context.deleteDatabase(name)
            }
        }
}
