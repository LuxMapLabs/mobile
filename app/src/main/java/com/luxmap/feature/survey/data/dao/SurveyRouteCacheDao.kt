package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.luxmap.feature.survey.data.entity.LocalAssignedSurveyRouteEntity

@Dao
interface SurveyRouteCacheDao {
    @Query("SELECT * FROM local_assigned_survey_route ORDER BY scheduledDate")
    suspend fun all(): List<LocalAssignedSurveyRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalAssignedSurveyRouteEntity>)

    @Query("DELETE FROM local_assigned_survey_route")
    suspend fun deleteAll()

    // Wraps delete+insert in one transaction so a fresh fetch never leaves the cache briefly
    // empty (or mixing old and new rows) if the process dies mid-call.
    @Transaction
    suspend fun replaceAll(items: List<LocalAssignedSurveyRouteEntity>) {
        deleteAll()
        insertAll(items)
    }
}
