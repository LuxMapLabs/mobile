package com.luxmap.di

import com.luxmap.core.camera.ExifPhotoLocationReader
import com.luxmap.core.camera.FileExifPhotoGps
import com.luxmap.core.camera.PhotoGpsExif
import com.luxmap.core.camera.PhotoLocationReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {
    @Binds
    abstract fun bindPhotoGpsExif(impl: FileExifPhotoGps): PhotoGpsExif

    @Binds
    abstract fun bindPhotoLocationReader(impl: ExifPhotoLocationReader): PhotoLocationReader
}
