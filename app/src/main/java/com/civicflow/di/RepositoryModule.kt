package com.civicflow.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Bind Fake<->Real repository của từng feature tại đây, 1 chỗ duy nhất
// (xem "Repository Pattern" trong CLAUDE.md). Thêm @Binds khi feature có
// Repository interface + FakeXxxRepository/RealXxxRepository.
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule
