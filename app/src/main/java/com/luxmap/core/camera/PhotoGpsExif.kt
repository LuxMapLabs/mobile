package com.luxmap.core.camera

import androidx.exifinterface.media.ExifInterface
import java.io.File
import javax.inject.Inject

interface PhotoGpsExif {
    fun write(
        file: File,
        lat: Double,
        lng: Double,
    )

    fun read(file: File): Pair<Double, Double>?
}

class FileExifPhotoGps
    @Inject
    constructor() : PhotoGpsExif {
        override fun write(
            file: File,
            lat: Double,
            lng: Double,
        ) {
            val exif = ExifInterface(file.absolutePath)
            exif.setLatLong(lat, lng)
            exif.saveAttributes()
        }

        override fun read(file: File): Pair<Double, Double>? {
            val exif = ExifInterface(file.absolutePath)
            val latLong = exif.latLong ?: return null
            return latLong[0] to latLong[1]
        }
    }
