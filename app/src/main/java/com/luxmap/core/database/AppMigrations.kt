package com.luxmap.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17: Migration =
    object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_pole_gps_order (workOrderId TEXT NOT NULL, " +
                    "detailJson TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(workOrderId))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_work_order_attachment (workOrderId TEXT NOT NULL, " +
                    "attachmentId TEXT NOT NULL, fileName TEXT NOT NULL, byteCount INTEGER NOT NULL, " +
                    "downloadUrl TEXT NOT NULL, status TEXT NOT NULL, localPath TEXT, " +
                    "PRIMARY KEY(workOrderId, attachmentId))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_pole_gps_measurement (workOrderId TEXT NOT NULL, " +
                    "poleId TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, accuracyM REAL NOT NULL, " +
                    "capturedAt INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(workOrderId, poleId))",
            )
            db.execSQL(
                "CREATE TABLE local_pole_list_item_new (workOrderId TEXT NOT NULL, poleId TEXT NOT NULL, " +
                    "position INTEGER, lat REAL, lng REAL, fixtureStatus TEXT NOT NULL, " +
                    "PRIMARY KEY(workOrderId, poleId))",
            )
            db.execSQL(
                "INSERT INTO local_pole_list_item_new (workOrderId, poleId, position, fixtureStatus) " +
                    "SELECT workOrderId, poleId, position, fixtureStatus FROM local_pole_list_item",
            )
            db.execSQL("DROP TABLE local_pole_list_item")
            db.execSQL("ALTER TABLE local_pole_list_item_new RENAME TO local_pole_list_item")
        }
    }

val MIGRATION_17_18: Migration =
    object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_pole_gps_measurement ADD COLUMN photoFilePath TEXT")
            db.execSQL("ALTER TABLE local_pole_gps_measurement ADD COLUMN evidenceClientOpId TEXT")
            db.execSQL("ALTER TABLE local_pole_gps_measurement ADD COLUMN evidenceUploadStatus TEXT")
            db.execSQL("ALTER TABLE local_work_order_evidence ADD COLUMN poleId TEXT")
        }
    }

val MIGRATION_18_19: Migration =
    object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_pole_location_update (poleId TEXT NOT NULL, " +
                    "photoFilePath TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, " +
                    "capturedAt INTEGER NOT NULL, status TEXT NOT NULL, clientOpId TEXT NOT NULL, " +
                    "PRIMARY KEY(poleId))",
            )
        }
    }

val MIGRATION_21_22: Migration =
    object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS local_pole_location_update")
            db.execSQL("DELETE FROM sync_queue WHERE opType = 'update_pole_location'")
        }
    }

val MIGRATION_22_23: Migration =
    object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_assigned_survey_route ADD COLUMN ownerUserId TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_assigned_pole_gps_order (ownerUserId TEXT NOT NULL, " +
                    "workOrderId TEXT NOT NULL, title TEXT NOT NULL, communeId TEXT NOT NULL, " +
                    "woStatus TEXT NOT NULL, dueDate TEXT, scheduledDate TEXT, " +
                    "PRIMARY KEY(ownerUserId, workOrderId))",
            )
        }
    }

val MIGRATION_23_24: Migration =
    object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_pole_list_item ADD COLUMN segmentId TEXT")
            db.execSQL("ALTER TABLE local_pole_list_item ADD COLUMN note TEXT")
            db.execSQL("ALTER TABLE local_pole_list_item ADD COLUMN fixtureType TEXT")
            db.execSQL("ALTER TABLE local_pole_list_item ADD COLUMN lampWatt INTEGER")
            db.execSQL("ALTER TABLE local_pole_list_item ADD COLUMN lampCount INTEGER")
            db.execSQL("ALTER TABLE local_pole_list_item ADD COLUMN fixtureCount INTEGER")
            db.execSQL("ALTER TABLE local_pole_list_item ADD COLUMN nearSensitivePoi INTEGER")
        }
    }

val MIGRATION_24_25: Migration =
    object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_work_order_draft (ownerUserId TEXT NOT NULL, " +
                    "workOrderId TEXT NOT NULL, reportNote TEXT NOT NULL, materialsUsed TEXT NOT NULL, " +
                    "faultOutcomesJson TEXT NOT NULL, PRIMARY KEY(ownerUserId, workOrderId))",
            )
        }
    }

val MIGRATION_25_26: Migration =
    object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_pole_gps_route (workOrderId TEXT NOT NULL, " +
                    "segmentId TEXT NOT NULL, geomWkt TEXT, fetchedAt INTEGER NOT NULL, PRIMARY KEY(workOrderId))",
            )
        }
    }

val MIGRATION_26_27: Migration =
    object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_lux_reading (clientOpId TEXT NOT NULL, workOrderId TEXT NOT NULL, " +
                    "poleId TEXT NOT NULL, photoId TEXT NOT NULL, measuredAt INTEGER NOT NULL, " +
                    "luxValue REAL NOT NULL, meterModel TEXT NOT NULL, dataSource TEXT NOT NULL, " +
                    "serverLuxId TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(clientOpId))",
            )
        }
    }

val MIGRATION_27_28: Migration =
    object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_lux_reading ADD COLUMN samplesJson TEXT")
            db.execSQL("ALTER TABLE local_lux_reading ADD COLUMN moduleId TEXT")
            db.execSQL("ALTER TABLE local_lux_reading ADD COLUMN sensorHeightM REAL")
        }
    }

val MIGRATION_28_29: Migration =
    object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE local_pole_gps_measurement_new (workOrderId TEXT NOT NULL, " +
                    "poleId TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, accuracyM REAL, " +
                    "capturedAt INTEGER NOT NULL, source TEXT NOT NULL, photoFilePath TEXT, " +
                    "evidenceClientOpId TEXT, evidenceUploadStatus TEXT, PRIMARY KEY(workOrderId, poleId))",
            )
            db.execSQL(
                "INSERT INTO local_pole_gps_measurement_new (workOrderId, poleId, lat, lng, accuracyM, " +
                    "capturedAt, source, photoFilePath, evidenceClientOpId, evidenceUploadStatus) " +
                    "SELECT workOrderId, poleId, lat, lng, accuracyM, capturedAt, source, photoFilePath, " +
                    "evidenceClientOpId, evidenceUploadStatus FROM local_pole_gps_measurement",
            )
            db.execSQL("DROP TABLE local_pole_gps_measurement")
            db.execSQL("ALTER TABLE local_pole_gps_measurement_new RENAME TO local_pole_gps_measurement")
        }
    }

val MIGRATION_29_30: Migration =
    object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS local_history_item (ownerUserId TEXT NOT NULL, " +
                    "workOrderId TEXT NOT NULL, title TEXT NOT NULL, taskKind TEXT NOT NULL, " +
                    "woStatus TEXT NOT NULL, communeId TEXT NOT NULL, updatedAt TEXT NOT NULL, " +
                    "scheduledDate TEXT, position INTEGER NOT NULL, PRIMARY KEY(ownerUserId, workOrderId))",
            )
        }
    }
