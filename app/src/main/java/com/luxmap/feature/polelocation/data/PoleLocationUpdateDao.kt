package com.luxmap.feature.polelocation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PoleLocationUpdateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalPoleLocationUpdateEntity)

    @Query("SELECT * FROM local_pole_location_update WHERE poleId = :poleId")
    suspend fun byPoleId(poleId: String): LocalPoleLocationUpdateEntity?

    @Query("SELECT * FROM local_pole_location_update")
    fun observeAll(): Flow<List<LocalPoleLocationUpdateEntity>>

    @Query("UPDATE local_pole_location_update SET status = :status WHERE poleId = :poleId")
    suspend fun updateStatus(
        poleId: String,
        status: String,
    )
}
