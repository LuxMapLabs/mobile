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
