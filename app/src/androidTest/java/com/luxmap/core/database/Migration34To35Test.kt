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

@RunWith(AndroidJUnit4::class)
class Migration34To35Test {
    @Test
    fun upgrade_keeps_cached_history_rows_and_leaves_the_commune_name_empty() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-34-35-test.db"
            context.deleteDatabase(name)
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().apply {
                openHelper.writableDatabase
                close()
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE local_history_item")
                it.execSQL(
                    "CREATE TABLE local_history_item (ownerUserId TEXT NOT NULL, workOrderId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, taskKind TEXT NOT NULL, woStatus TEXT NOT NULL, " +
                        "communeId TEXT NOT NULL, updatedAt TEXT NOT NULL, scheduledDate TEXT, " +
                        "position INTEGER NOT NULL, fetchedAt INTEGER, completedAt TEXT, closedAt TEXT, " +
                        "PRIMARY KEY(ownerUserId, workOrderId))",
                )
                it.execSQL(
                    "INSERT INTO local_history_item (ownerUserId, workOrderId, title, taskKind, woStatus, " +
                        "communeId, updatedAt, position) VALUES ('U-1', 'WO-1', 'Lệnh', 'repair', 'verified', " +
                        "'COM-1', '2026-11-20T08:00:00Z', 0)",
                )
                it.version = 34
            }
            val after =
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(MIGRATION_34_35)
                    .build()
            val row = after.historyCacheDao().all("U-1").single()
            assertEquals("WO-1", row.workOrderId)
            assertNull(row.communeName)
            after.close()
            context.deleteDatabase(name)
        }
}
