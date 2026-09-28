package com.luxmap.di

import android.content.Context
import androidx.room.Room
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "luxmap.db")
            // No shipped release yet (versionCode = 1) and the schema is still changing task by
            // task in this plan (this task adds v1's tables, Task 13 bumps to v2) — destructive
            // migration is acceptable pre-release. Revisit before the app ships to a real device
            // with data worth preserving across an update.
            .fallbackToDestructiveMigration()
            .build()

    // Needed so Hilt can build SurveySessionRecoveryUseCase (Task 16), which is the first class
    // injected directly by LuxMapApp that needs this DAO. Only this one DAO is added here because
    // it is the only one a real Hilt injection site currently needs - not general DAO wiring.
    @Provides
    @Singleton
    fun provideSurveySessionDao(db: AppDatabase): SurveySessionDao = db.surveySessionDao()
}
