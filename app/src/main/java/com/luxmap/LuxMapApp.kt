package com.luxmap

import android.app.Application
import com.luxmap.core.map.initMapLibre
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LuxMapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initMapLibre(this)
    }
}
