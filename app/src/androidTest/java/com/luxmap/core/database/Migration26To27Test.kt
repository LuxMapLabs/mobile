package com.luxmap.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
import com.luxmap.feature.workorder.polegps.data.LocalPoleGpsRouteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class Migration26To27Test {
    @Test
    fun upgrade_keeps_existing_rows_and_adds_the_lux_reading_table() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-26-27-test.db"
            context.deleteDatabase(name)
            val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            before.poleGpsDao().upsertRoute(LocalPoleGpsRouteEntity("W", "SEG-1", null, Instant.EPOCH))
            before.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE local_lux_reading")
                it.version = 26
            }
            val after =
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(MIGRATION_26_27)
                    .build()
            assertEquals("SEG-1", after.poleGpsDao().route("W")!!.segmentId)
            val dao = after.luxReadingDao()
            dao.insert(
                LocalLuxReadingEntity(
                    clientOpId = "L1",
                    workOrderId = "W",
                    poleId = "POLE-1",
                    photoId = "PH1",
                    measuredAt = Instant.ofEpochMilli(2000),
                    luxValue = 12.3,
                    meterModel = "ESP32C3_LUX",
                    dataSource = "field",
                    createdAt = Instant.ofEpochMilli(2000),
                ),
            )
            dao.updateServerLuxId("L1", "LUX-0001")
            val saved = dao.observeByWorkOrder("W").first().single()
            assertEquals(12.3, saved.luxValue, 0.0)
            assertEquals("LUX-0001", saved.serverLuxId)
            after.close()
            context.deleteDatabase(name)
        }
}
