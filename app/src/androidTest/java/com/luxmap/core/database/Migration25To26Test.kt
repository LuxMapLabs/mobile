package com.luxmap.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.feature.workorder.polegps.data.LocalPoleGpsRouteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class Migration25To26Test {
    @Test
    fun upgrade_keeps_measurements_and_adds_the_route_cache() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-25-26-test.db"
            context.deleteDatabase(name)
            val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            before.openHelper.writableDatabase.execSQL(
                "INSERT INTO local_pole_gps_measurement VALUES " +
                    "('W', 'P', 10.0, 106.0, 5.0, 1000, 'device', '/photo.jpg', 'E', 'queued')",
            )
            before.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE local_pole_gps_route")
                it.version = 25
            }
            val after =
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(MIGRATION_25_26, MIGRATION_26_27)
                    .build()
            val dao = after.poleGpsDao()
            assertEquals(1, dao.measurements("W").size)
            dao.upsertRoute(LocalPoleGpsRouteEntity("W", "SEG-1", "LINESTRING (106 10, 106.001 10)", Instant.EPOCH))
            assertEquals("SEG-1", dao.route("W")!!.segmentId)
            after.close()
            context.deleteDatabase(name)
        }
}
