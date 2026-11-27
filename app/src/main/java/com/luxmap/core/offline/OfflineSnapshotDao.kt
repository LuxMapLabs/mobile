package com.luxmap.core.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineSnapshotDao {
    @Query("SELECT * FROM local_offline_snapshot WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): LocalOfflineSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(snapshot: LocalOfflineSnapshotEntity)

    @Query("DELETE FROM local_offline_snapshot")
    suspend fun clear()
}
