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

@RunWith(AndroidJUnit4::class)
class AppMigrations17To18Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion17() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(17) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE local_pole_gps_measurement (workOrderId TEXT NOT NULL, " +
                                    "poleId TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, " +
                                    "accuracyM REAL NOT NULL, capturedAt INTEGER NOT NULL, source TEXT NOT NULL, " +
                                    "PRIMARY KEY(workOrderId, poleId))",
                            )
                            db.execSQL(
                                "CREATE TABLE local_work_order_evidence (clientOpId TEXT NOT NULL, " +
                                    "workOrderId TEXT NOT NULL, kind TEXT NOT NULL, filePath TEXT NOT NULL, " +
                                    "capturedAt INTEGER NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, " +
                                    "uploadStatus TEXT NOT NULL, PRIMARY KEY(clientOpId))",
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
            "INSERT INTO local_pole_gps_measurement (workOrderId, poleId, lat, lng, accuracyM, capturedAt, source) " +
                "VALUES ('WO-1', 'P-1', 10.0, 106.0, 5.0, 1000, 'device')",
        )
        db.execSQL(
            "INSERT INTO local_work_order_evidence (clientOpId, workOrderId, kind, filePath, capturedAt, lat, lng, " +
                "uploadStatus) VALUES ('OP-1', 'WO-1', 'observation', '/f/a.jpg', 1000, 10.0, 106.0, 'pending')",
        )
        MIGRATION_17_18.migrate(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun measurement_keeps_its_data_and_gains_null_photo_columns() {
        db.query(
            "SELECT lat, photoFilePath, evidenceClientOpId, evidenceUploadStatus FROM local_pole_gps_measurement " +
                "WHERE poleId = 'P-1'",
        ).use {
            it.moveToFirst()
            assertEquals(10.0, it.getDouble(0), 0.0)
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
        }
    }

    @Test
    fun evidence_keeps_its_data_and_gains_a_null_pole_id() {
        db.query("SELECT filePath, poleId FROM local_work_order_evidence WHERE clientOpId = 'OP-1'").use {
            it.moveToFirst()
            assertEquals("/f/a.jpg", it.getString(0))
            assertTrue(it.isNull(1))
        }
    }
}

@RunWith(AndroidJUnit4::class)
class AppMigrations18To19Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion18() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(18) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "clientOpId TEXT NOT NULL, opType TEXT NOT NULL, payloadJson TEXT NOT NULL, " +
                                    "dependsOnClientOpId TEXT, status TEXT NOT NULL, attemptCount INTEGER NOT NULL, " +
                                    "lastError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
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
                "VALUES ('OP-1', 'update_pole_location', '{}', NULL, 'queued', 0, NULL, 1, 1)",
        )
        MIGRATION_18_19.migrate(db)
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
    fun the_new_table_accepts_a_row() {
        db.execSQL(
            "INSERT INTO local_pole_location_update (poleId, photoFilePath, lat, lng, capturedAt, status, " +
                "clientOpId) VALUES ('POLE-1', '/f/a.jpg', 10.0, 106.0, 1000, 'pending', 'OP-2')",
        )
        db.query("SELECT lat, lng, status FROM local_pole_location_update WHERE poleId = 'POLE-1'").use {
            it.moveToFirst()
            assertEquals(10.0, it.getDouble(0), 0.0)
            assertEquals(106.0, it.getDouble(1), 0.0)
            assertEquals("pending", it.getString(2))
        }
    }
}

@RunWith(AndroidJUnit4::class)
class Migration28To29Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion28() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(28) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE local_pole_gps_measurement (workOrderId TEXT NOT NULL, " +
                                    "poleId TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, " +
                                    "accuracyM REAL NOT NULL, capturedAt INTEGER NOT NULL, source TEXT NOT NULL, " +
                                    "photoFilePath TEXT, evidenceClientOpId TEXT, evidenceUploadStatus TEXT, " +
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
            "INSERT INTO local_pole_gps_measurement VALUES ('WO-1', 'P-1', 10.5, 106.5, 3.0, 99, 'device', " +
                "'/p.jpg', 'OP-1', 'pending')",
        )
        MIGRATION_28_29.migrate(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun existing_measurement_keeps_its_data() {
        db.query("SELECT accuracyM, photoFilePath, evidenceUploadStatus FROM local_pole_gps_measurement").use {
            assertTrue(it.moveToFirst())
            assertEquals(3.0, it.getDouble(0), 0.0)
            assertEquals("/p.jpg", it.getString(1))
            assertEquals("pending", it.getString(2))
        }
    }

    @Test
    fun accuracy_can_now_be_null() {
        db.execSQL(
            "INSERT INTO local_pole_gps_measurement (workOrderId, poleId, lat, lng, accuracyM, capturedAt, source) " +
                "VALUES ('WO-1', 'P-2', 10.5, 106.5, NULL, 99, 'device')",
        )
        db.query("SELECT accuracyM FROM local_pole_gps_measurement WHERE poleId = 'P-2'").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
    }
}

@RunWith(AndroidJUnit4::class)
class Migration29To32Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion29() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(29) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE local_assigned_survey_route (workOrderId TEXT NOT NULL PRIMARY KEY, " +
                                    "title TEXT NOT NULL, communeId TEXT NOT NULL, woStatus TEXT NOT NULL, " +
                                    "dueDate TEXT, scheduledDate TEXT, ownerUserId TEXT)",
                            )
                            db.execSQL(
                                "CREATE TABLE local_assigned_pole_gps_order (ownerUserId TEXT NOT NULL, " +
                                    "workOrderId TEXT NOT NULL, title TEXT NOT NULL, communeId TEXT NOT NULL, " +
                                    "woStatus TEXT NOT NULL, dueDate TEXT, scheduledDate TEXT, " +
                                    "PRIMARY KEY(ownerUserId, workOrderId))",
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
        db.execSQL("INSERT INTO local_assigned_survey_route VALUES ('WO-1', 'T', 'C', 'assigned', NULL, NULL, 'U-1')")
        MIGRATION_29_30.migrate(db)
        MIGRATION_30_31.migrate(db)
        MIGRATION_31_32.migrate(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun history_cache_accepts_a_row_with_and_without_a_fetch_time() {
        db.execSQL(
            "INSERT INTO local_history_item (ownerUserId, workOrderId, title, taskKind, woStatus, communeId, " +
                "updatedAt, scheduledDate, position, fetchedAt) " +
                "VALUES ('U-1', 'WO-1', 'T', 'repair', 'verified', 'C', '2026-11-20T08:00:00Z', NULL, 0, 1000)",
        )
        db.execSQL(
            "INSERT INTO local_history_item (ownerUserId, workOrderId, title, taskKind, woStatus, communeId, " +
                "updatedAt, scheduledDate, position) " +
                "VALUES ('U-1', 'WO-2', 'T', 'survey', 'done', 'C', '2026-11-20T08:00:00Z', NULL, 1)",
        )
        db.query("SELECT fetchedAt FROM local_history_item ORDER BY position").use {
            assertTrue(it.moveToFirst())
            assertEquals(1000L, it.getLong(0))
            assertTrue(it.moveToNext())
            assertTrue(it.isNull(0))
        }
    }

    @Test
    fun hub_cache_tables_are_dropped() {
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN " +
                "('local_assigned_survey_route', 'local_assigned_pole_gps_order')",
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }
}
