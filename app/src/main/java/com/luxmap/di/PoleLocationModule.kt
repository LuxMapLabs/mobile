package com.luxmap.di

import com.luxmap.feature.polelocation.data.FileExifPoleLocation
import com.luxmap.feature.polelocation.data.PoleLocationExif
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PoleLocationModule {
    @Binds
    abstract fun bindPoleLocationExif(impl: FileExifPoleLocation): PoleLocationExif
}
