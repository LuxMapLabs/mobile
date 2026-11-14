package com.luxmap.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppMigrationsTest {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion16() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(16) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "clientOpId TEXT NOT NULL, opType TEXT NOT NULL, payloadJson TEXT NOT NULL, " +
                                    "dependsOnClientOpId TEXT, status TEXT NOT NULL, attemptCount INTEGER NOT NULL, " +
                                    "lastError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                            )
                            db.execSQL(
                                "CREATE TABLE local_pole_list_item (workOrderId TEXT NOT NULL, poleId TEXT NOT NULL, " +
                                    "position INTEGER NOT NULL, fixtureStatus TEXT NOT NULL, " +
                                    "PRIMARY KEY(workOrderId, poleId))",
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
        db.execSQL(
            "INSERT INTO sync_queue (clientOpId, opType, payloadJson, dependsOnClientOpId, status, " +
                "attemptCount, lastError, createdAt, updatedAt) " +
                "VALUES ('OP-1', 'complete_work_order', '{}', NULL, 'queued', 0, NULL, 1, 1)",
        )
        db.execSQL("INSERT INTO local_pole_list_item VALUES ('WO-1', 'P-1', 3, 'normal')")
        MIGRATION_16_17.migrate(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun unsent_sync_queue_rows_survive() {
        db.query("SELECT clientOpId, status FROM sync_queue").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("OP-1", it.getString(0))
            assertEquals("queued", it.getString(1))
        }
    }

    @Test
    fun cached_poles_keep_their_data_and_gain_null_coordinates() {
        db.query("SELECT position, lat, lng, fixtureStatus FROM local_pole_list_item WHERE poleId = 'P-1'").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
            assertEquals("normal", it.getString(3))
        }
    }

    @Test
    fun pole_position_can_now_be_null() {
        db.execSQL("INSERT INTO local_pole_list_item VALUES ('WO-1', 'P-2', NULL, NULL, NULL, 'unknown')")
        db.query("SELECT COUNT(*) FROM local_pole_list_item").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
        }
    }

    @Test
    fun new_tables_exist() {
        val names = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            while (it.moveToNext()) names += it.getString(0)
        }
        assertTrue(
            names.containsAll(
                listOf("local_pole_gps_order", "local_work_order_attachment", "local_pole_gps_measurement"),
            ),
        )
    }
}
