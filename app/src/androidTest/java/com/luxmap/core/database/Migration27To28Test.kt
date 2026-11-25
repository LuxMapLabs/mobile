package com.luxmap.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class Migration27To28Test {
    @Test
    fun upgrade_keeps_old_readings_as_reference_readings_and_stores_the_stationary_protocol() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-27-28-test.db"
            context.deleteDatabase(name)
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().apply {
                openHelper.writableDatabase
                close()
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE local_lux_reading")
                it.execSQL(
                    "CREATE TABLE local_lux_reading (clientOpId TEXT NOT NULL, workOrderId TEXT NOT NULL, " +
                        "poleId TEXT NOT NULL, photoId TEXT NOT NULL, measuredAt INTEGER NOT NULL, " +
                        "luxValue REAL NOT NULL, meterModel TEXT NOT NULL, dataSource TEXT NOT NULL, " +
                        "serverLuxId TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(clientOpId))",
                )
                it.execSQL(
                    "INSERT INTO local_lux_reading VALUES " +
                        "('OLD', 'W', 'POLE-1', 'PH1', 1000, 12.3, 'ESP32C3_LUX', 'field', NULL, 1000)",
                )
                it.version = 27
            }
            val after =
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(MIGRATION_27_28)
                    .build()
            val dao = after.luxReadingDao()
            val old = dao.observeByWorkOrder("W").first().single()
            assertEquals(12.3, old.luxValue, 0.0)
            assertNull(old.samplesJson)
            assertNull(old.moduleId)
            assertNull(old.sensorHeightM)
            dao.insert(
                LocalLuxReadingEntity(
                    clientOpId = "NEW",
                    workOrderId = "W",
                    poleId = "POLE-1",
                    photoId = "PH2",
                    measuredAt = Instant.ofEpochMilli(2000),
                    luxValue = 12.35,
                    meterModel = "ESP32C3_LUX",
                    dataSource = "field",
                    createdAt = Instant.ofEpochMilli(2000),
                    samplesJson = "[12.3,12.35,12.4]",
                    moduleId = "AA:BB",
                    sensorHeightM = 1.0,
                ),
            )
            val saved = dao.byId("NEW")!!
            assertEquals("[12.3,12.35,12.4]", saved.samplesJson)
            assertEquals("AA:BB", saved.moduleId)
            assertEquals(1.0, saved.sensorHeightM!!, 0.0)
            after.close()
            context.deleteDatabase(name)
        }
}
