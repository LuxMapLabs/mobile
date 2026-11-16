package com.luxmap.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration21To22Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion21() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(21) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "clientOpId TEXT NOT NULL, opType TEXT NOT NULL, payloadJson TEXT NOT NULL, " +
                                    "dependsOnClientOpId TEXT, status TEXT NOT NULL, attemptCount INTEGER NOT NULL, " +
                                    "lastError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                            )
                            db.execSQL(
                                "CREATE TABLE local_pole_location_update (poleId TEXT NOT NULL, " +
                                    "photoFilePath TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, " +
                                    "capturedAt INTEGER NOT NULL, status TEXT NOT NULL, clientOpId TEXT NOT NULL, " +
                                    "PRIMARY KEY(poleId))",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        listOf("OP-POLE" to "update_pole_location", "OP-DONE" to "complete_work_order").forEach { (id, type) ->
            db.execSQL(
                "INSERT INTO sync_queue (clientOpId, opType, payloadJson, dependsOnClientOpId, status, " +
                    "attemptCount, lastError, createdAt, updatedAt) " +
                    "VALUES ('$id', '$type', '{}', NULL, 'queued', 0, NULL, 1, 1)",
            )
        }
        db.execSQL(
            "INSERT INTO local_pole_location_update VALUES ('P-1', '/f.jpg', 10.0, 106.0, 1, 'pending', 'OP-POLE')",
        )
        MIGRATION_21_22.migrate(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun the_pole_location_table_is_dropped() {
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'local_pole_location_update'").use {
            assertEquals(0, it.count)
        }
    }

    @Test
    fun only_pole_location_ops_are_removed_from_the_sync_queue() {
        db.query("SELECT clientOpId FROM sync_queue").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("OP-DONE", it.getString(0))
        }
    }
}
