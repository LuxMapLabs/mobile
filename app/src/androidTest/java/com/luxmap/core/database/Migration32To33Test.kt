package com.luxmap.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.offline.LocalOfflineSnapshotEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPoleSkipEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class Migration32To33Test {
    @Test
    fun upgrade_keeps_old_photos_without_a_label_and_adds_the_skip_and_snapshot_tables() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-32-33-test.db"
            context.deleteDatabase(name)
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().apply {
                openHelper.writableDatabase
                close()
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE local_survey_pole_skip")
                it.execSQL("DROP TABLE local_offline_snapshot")
                it.execSQL("DROP TABLE local_pole_photo")
                it.execSQL(
                    "CREATE TABLE local_pole_photo (photoId TEXT NOT NULL, workOrderId TEXT NOT NULL, " +
                        "poleId TEXT NOT NULL, filePath TEXT NOT NULL, capturedAt INTEGER NOT NULL, " +
                        "lat REAL NOT NULL, lng REAL NOT NULL, serverPhotoId TEXT, aiStatus TEXT, cvState TEXT, " +
                        "confidence REAL, modelVersion TEXT, analyzedAt INTEGER, finalCvState TEXT, " +
                        "reviewNote TEXT, reviewedBy TEXT, reviewedAt INTEGER, decision TEXT, decisionNote TEXT, " +
                        "decidedBy TEXT, decidedAt INTEGER, draftFinalCvState TEXT, draftReviewNote TEXT, " +
                        "videoUrl TEXT, mediaType TEXT NOT NULL, PRIMARY KEY(photoId))",
                )
                it.execSQL(
                    "INSERT INTO local_pole_photo (photoId, workOrderId, poleId, filePath, capturedAt, lat, lng, " +
                        "mediaType) VALUES ('PH1', 'W', 'POLE-1', '/p.jpg', 1000, 10.0, 106.0, 'photo')",
                )
                it.version = 32
            }
            val after =
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(MIGRATION_32_33)
                    .build()
            val dao = after.polePhotoDao()
            assertNull(dao.byId("PH1")!!.engineerObservation)
            dao.upsertSkip(LocalSurveyPoleSkipEntity("W", "POLE-2", "Bị che", Instant.ofEpochMilli(2000)))
            assertEquals("Bị che", dao.observeSkips("W").first().single().reason)
            after.offlineSnapshotDao().put(LocalOfflineSnapshotEntity("home", "{}", Instant.ofEpochMilli(3000)))
            assertEquals("{}", after.offlineSnapshotDao().get("home")?.json)
            after.close()
            context.deleteDatabase(name)
        }
}
