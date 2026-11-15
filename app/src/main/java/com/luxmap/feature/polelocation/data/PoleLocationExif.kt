package com.luxmap.feature.polelocation.data

import androidx.exifinterface.media.ExifInterface
import java.io.File
import javax.inject.Inject

interface PoleLocationExif {
    fun write(
        file: File,
        lat: Double,
        lng: Double,
    )

    fun read(file: File): Pair<Double, Double>?
}

// The device camera app, not this app, writes the photo file (ACTION_IMAGE_CAPTURE via Intent) -
// whether it geotags EXIF depends on that app's own "Location tags" setting, off by default on
// most OEMs. This app always writes its own GPS EXIF right after capture so the result never
// depends on a setting it cannot see or control.
class FileExifPoleLocation
    @Inject
    constructor() : PoleLocationExif {
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
