package com.luxmap.di

import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.auth.data.RealAuthRepository
import com.luxmap.feature.home.data.HomeRepository
import com.luxmap.feature.home.data.RealHomeRepository
import com.luxmap.feature.map.data.FakeMapRepository
import com.luxmap.feature.map.data.FakePoleDetailRepository
import com.luxmap.feature.map.data.MapRepository
import com.luxmap.feature.map.data.PoleDetailRepository
import com.luxmap.feature.survey.data.FakeSurveyRepository
import com.luxmap.feature.survey.data.FakeUploadRepository
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.data.UploadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Bind Fake<->Real repository của từng feature tại đây, 1 chỗ duy nhất
// (xem "Repository Pattern" trong CLAUDE.md). Thêm @Binds khi feature có
// Repository interface + FakeXxxRepository/RealXxxRepository.
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindMapRepository(impl: FakeMapRepository): MapRepository

    @Binds
    abstract fun bindPoleDetailRepository(impl: FakePoleDetailRepository): PoleDetailRepository

    @Binds
    abstract fun bindAuthRepository(impl: RealAuthRepository): AuthRepository

    @Binds
    abstract fun bindHomeRepository(impl: RealHomeRepository): HomeRepository

    @Binds
    abstract fun bindSurveyRepository(impl: FakeSurveyRepository): SurveyRepository

    @Binds
    abstract fun bindUploadRepository(impl: FakeUploadRepository): UploadRepository
}
