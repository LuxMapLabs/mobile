package com.luxmap.di

import com.luxmap.core.common.BootSessionProvider
import com.luxmap.core.common.RealBootSessionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    abstract fun bindBootSessionProvider(impl: RealBootSessionProvider): BootSessionProvider
}
