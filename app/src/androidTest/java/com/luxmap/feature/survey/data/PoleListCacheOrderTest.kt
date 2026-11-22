package com.luxmap.feature.survey.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoleListCacheOrderTest {
    @Test
    fun cached_poles_keep_api_order_even_when_positions_are_missing_or_out_of_order() =
        runTest {
            val db =
                Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).build()
            try {
                val dao = db.poleListCacheDao()
                val poles =
                    listOf(
                        LocalPoleListItemEntity("W", "P3", null, null, null, "normal"),
                        LocalPoleListItemEntity("W", "P2", 9, 10.0, 106.0, "normal"),
                        LocalPoleListItemEntity("W", "P1", 1, 10.1, 106.1, "normal"),
                    )
                dao.replaceForWorkOrder("W", poles)
                assertEquals(poles, dao.byWorkOrder("W"))
                dao.replaceForWorkOrder("W", poles.reversed())
                assertEquals(poles.reversed(), dao.byWorkOrder("W"))
            } finally {
                db.close()
            }
        }
}
